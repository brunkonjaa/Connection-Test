package ie.suric.connectiontest

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ie.suric.connectiontest.auth.*
import ie.suric.connectiontest.chat.*
import ie.suric.connectiontest.security.TokenVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Base64

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(250, 247, 241)
        window.navigationBarColor = android.graphics.Color.rgb(250, 247, 241)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF795548),
                background = androidx.compose.ui.graphics.Color(0xFFFAF7F1), surface = androidx.compose.ui.graphics.Color(0xFFFFFEFC))) {
                val vm: ConnectionViewModel = viewModel(factory = ConnectionViewModel.Factory(application))
                AskBusterApp(vm)
            }
        }
    }
}

class ConnectionViewModel(private val app: android.app.Application) : ViewModel() {
    private val vault = TokenVault(app)
    private val oauth = OpenAiOAuth(app)
    private val api = ChatApi()
    private val _auth = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val auth = _auth.asStateFlow()
    private val _conversation = MutableStateFlow(ConversationState())
    val conversation = _conversation.asStateFlow()
    private var tokens: Tokens? = vault.load()
    private var model: String? = null
    private var autoQuestionSent = false
    private var signInJob: Job? = null

    private fun transitionAuth(event: AuthStateMachine.Event) {
        _auth.value = AuthStateMachine.transition(_auth.value, event)
    }

    init {
        if (tokens != null) {
            autoQuestionSent = true
            _auth.value = AuthState.SignedIn(tokens?.email)
        }
    }

    fun signIn() {
        if (_auth.value == AuthState.SigningIn) return
        transitionAuth(AuthStateMachine.Event.Begin)
        signInJob = viewModelScope.launch {
            try {
                val result = oauth.signIn(tokens)
                tokens = result; vault.save(result)
                transitionAuth(AuthStateMachine.Event.Success(result.email))
                loadModelAndInitialQuestion()
            } catch (_: kotlinx.coroutines.CancellationException) {
                transitionAuth(AuthStateMachine.Event.Cancel)
            } catch (e: Exception) {
                val message = e.message ?: "Sign-in failed. Please try again."
                if (message.contains("cancelled", true)) transitionAuth(AuthStateMachine.Event.Cancel)
                else transitionAuth(AuthStateMachine.Event.Error(message))
            }
        }
    }

    fun cancelSignIn() {
        oauth.cancel()
        signInJob?.cancel()
        signInJob = null
        transitionAuth(AuthStateMachine.Event.Cancel)
    }

    fun disconnect() {
        tokens = null; model = null; vault.clear(); autoQuestionSent = true
        transitionAuth(AuthStateMachine.Event.Disconnect); _conversation.value = ConversationState()
    }

    fun updateDraft(value: String) { _conversation.value = _conversation.value.copy(draft = value) }
    fun send(text: String = _conversation.value.draft, image: Uri? = null) {
        val cleaned = text.trim()
        if ((cleaned.isEmpty() && image == null) || _conversation.value.isLoading) return
        val displayText = cleaned.ifEmpty { "What is in this picture?" }
        val before = _conversation.value
        _conversation.value = ConversationReducer.appendUser(before, displayText)
        viewModelScope.launch {
            try {
                val active = validAccessToken()
                val selectedModel = model ?: loadModel(active)
                val dataUrl = image?.let { uriToDataUrl(it) }
                val history = _conversation.value.messages
                withContext(Dispatchers.IO) {
                    api.stream(active, selectedModel, history, dataUrl) { delta ->
                        _conversation.value = ConversationReducer.appendDelta(_conversation.value, delta)
                    }
                }
                _conversation.value = ConversationReducer.completed(_conversation.value)
            } catch (e: Exception) {
                if (e is ApiException && e.statusCode == 401) {
                    transitionAuth(AuthStateMachine.Event.Expired)
                } else _conversation.value = ConversationReducer.failed(_conversation.value, e.message ?: "The request failed. Try again.")
            }
        }
    }

    fun retry() {
        val lastUser = _conversation.value.messages.lastOrNull { it.role == "user" } ?: return
        val messages = _conversation.value.messages.toMutableList()
        while (messages.lastOrNull()?.role == "assistant") messages.removeAt(messages.lastIndex)
        if (messages.lastOrNull()?.role == "user") messages.removeAt(messages.lastIndex)
        _conversation.value = _conversation.value.copy(messages = messages, isLoading = false)
        send(lastUser.text)
    }

    private suspend fun validAccessToken(): String {
        val current = tokens ?: error("Connect your ChatGPT account first.")
        if (!TokenExpiryPolicy.shouldRefresh(current.expiresAtEpochSeconds, System.currentTimeMillis() / 1000)) return current.accessToken
        return try {
            oauth.refresh(current).also { tokens = it; vault.save(it) }.accessToken
        } catch (e: Exception) {
            throw ApiException(401, "Your ChatGPT sign-in expired. Reconnect your account and retry.")
        }
    }

    private suspend fun loadModel(access: String): String = withContext(Dispatchers.IO) {
        api.listModels(access).firstOrNull()?.also { model = it.slug; _conversation.value = _conversation.value.copy(modelLabel = it.displayName) }
            ?.slug ?: error("No models are currently available for this ChatGPT account.")
    }

    private fun sendInitialQuestion() {
        if (autoQuestionSent) return
        autoQuestionSent = true
        send("What are the three most important things to consider when growing rosemary outdoors in Kilkenny, Ireland?")
    }

    private fun loadModelAndInitialQuestion() {
        viewModelScope.launch {
            try {
                val access = validAccessToken(); loadModel(access); sendInitialQuestion()
            } catch (e: Exception) {
                _conversation.value = ConversationReducer.failed(_conversation.value, e.message ?: "Unable to prepare the ChatGPT connection.")
            }
        }
    }

    private fun uriToDataUrl(uri: Uri): String {
        val mime = app.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: error("The selected file is not an image.")
        val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); var count: Int
            while (input.read(buffer).also { count = it } >= 0) { out.write(buffer, 0, count); require(out.size() <= 10_000_000) { "Choose an image smaller than 10 MB." } }
            out.toByteArray()
        } ?: error("Unable to read the selected image.")
        return "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
    }

    class Factory(private val app: android.app.Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ConnectionViewModel(app) as T
    }
}

@Composable
private fun AskBusterApp(vm: ConnectionViewModel) {
    val auth by vm.auth.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = auth) {
            is AuthState.SignedIn -> ConversationScreen(state.email, vm)
            else -> ConnectionScreen(state, vm)
        }
    }
}

@Composable
private fun ConnectionScreen(state: AuthState, vm: ConnectionViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Ask Buster", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Connection Test", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(28.dp))
        Text(when (state) {
            AuthState.SigningIn -> "Waiting for ChatGPT sign-in…"
            is AuthState.Failed -> "Not connected"
            else -> "Not connected to ChatGPT"
        }, style = MaterialTheme.typography.bodyLarge)
        (state as? AuthState.Failed)?.let { Spacer(Modifier.height(12.dp)); Text(it.message, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(24.dp))
        if (state == AuthState.SigningIn) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            TextButton(onClick = vm::cancelSignIn) { Text("Cancel sign-in") }
        } else Button(onClick = vm::signIn, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Continue with ChatGPT") }
        Spacer(Modifier.height(12.dp))
        Text("Uses eligible ChatGPT plan access if OpenAI grants it. This app does not use an API key.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/agpl-3.0.html"))) }) {
            Text("Open source • AGPL-3.0 • No warranty", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ConversationScreen(email: String?, vm: ConnectionViewModel) {
    val state by vm.conversation.collectAsState()
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { selectedImage = it }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("Ask Buster", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(email ?: "Connected", style = MaterialTheme.typography.labelMedium) }
            TextButton(onClick = vm::disconnect) { Text("Disconnect") }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(state.messages) { _, msg ->
                Surface(color = if (msg.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(if (msg.role == "user") .90f else 1f)) {
                    Text(msg.text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (state.isLoading) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("ChatGPT is responding…") } }
            state.error?.let { item { Column { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = vm::retry) { Text("Retry") } } } }
        }
        state.modelLabel?.let { Text("Model: $it", Modifier.padding(start = 16.dp), style = MaterialTheme.typography.labelSmall) }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.AddPhotoAlternate, "Attach a photo") }
            OutlinedTextField(value = state.draft, onValueChange = vm::updateDraft, modifier = Modifier.weight(1f), placeholder = { Text("Ask a question") }, maxLines = 4)
            IconButton(onClick = { vm.send(image = selectedImage); selectedImage = null }, enabled = !state.isLoading, modifier = Modifier.size(52.dp)) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        }
        selectedImage?.let { Text("Photo attached", Modifier.padding(start = 16.dp, bottom = 8.dp)) }
    }
}
