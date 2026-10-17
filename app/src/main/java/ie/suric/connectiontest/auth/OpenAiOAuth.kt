package ie.suric.connectiontest.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jwt.SignedJWT
import org.json.JSONObject

class OpenAiOAuth(private val context: Context, private val http: OkHttpClient = OkHttpClient.Builder()
    .callTimeout(45, TimeUnit.SECONDS).build()) {
    private val authHost = "https://auth.openai.com"
    private val redirectPath = "/auth/callback"
    private val random = SecureRandom()
    @Volatile private var activeServer: ServerSocket? = null

    fun cancel() { runCatching { activeServer?.close() } }

    suspend fun signIn(existing: Tokens? = null): Tokens = withContext(Dispatchers.IO) {
        val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        activeServer = server
        server.soTimeout = 180_000
        val redirect = "http://127.0.0.1:${server.localPort}$redirectPath"
        val state = randomValue(32)
        val nonce = randomValue(32)
        val verifier = randomValue(48)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val hostId = context.getSharedPreferences("runtime", Context.MODE_PRIVATE).let { prefs ->
            prefs.getString("host_id", null) ?: "urn:uuid:${UUID.randomUUID()}".also { prefs.edit().putString("host_id", it).apply() }
        }
        val params = linkedMapOf(
            "client_id" to (existing?.clientId ?: "dynamic_agent_client"),
            "ext_agent_host_id" to hostId, "response_type" to "code", "redirect_uri" to redirect,
            "scope" to "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct",
            "resource" to "https://api.openai.com/v1", "state" to state, "nonce" to nonce,
            "code_challenge_method" to "S256", "code_challenge" to challenge
        ).apply {
            if (existing == null) put("agent_name_hint", "Ask Buster — Connection Test")
            else {
                put("id_token_hint", existing.idToken)
                existing.email?.let { put("login_hint", it) }
            }
        }
        try {
            val url = Uri.parse("$authHost/api/accounts/authorize").buildUpon().apply {
                params.forEach { (key, value) -> appendQueryParameter(key, value) }
            }.build()
            withContext(Dispatchers.Main) { context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            val socket = server.accept()
            val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
            val requestLine = input.readLine() ?: error("No sign-in callback was received.")
            while (input.readLine()?.isNotEmpty() == true) Unit
            val target = requestLine.split(' ').getOrNull(1) ?: error("Invalid sign-in callback.")
            val callback = Uri.parse("http://127.0.0.1$target")
            val query = callback.queryParameterNames.associateWith { callback.getQueryParameter(it).orEmpty() }
            val body = if (query["state"] == state) "You can return to Ask Buster." else "Sign-in could not be verified. Return to Ask Buster."
            val html = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\nContent-Length: ${body.toByteArray().size}\r\n\r\n$body"
            socket.getOutputStream().use { it.write(html.toByteArray(Charsets.UTF_8)) }
            socket.close()
            require(callback.path == redirectPath) { "The sign-in callback path was not expected." }
            require(query["state"] == state) { "Sign-in could not be verified. Please try again." }
            query["error"]?.let { error(if (it == "access_denied") "Sign-in was cancelled." else "Sign-in failed. Please try again.") }
            val code = query["code"]?.takeIf(String::isNotBlank) ?: error("No authorization code was returned.")
            val callbackClientId = query["client_id"]?.takeIf(String::isNotBlank)
            if (existing != null && callbackClientId != null && callbackClientId != existing.clientId) {
                error("The sign-in returned a different account registration. Please try again.")
            }
            val clientId = callbackClientId ?: existing?.clientId ?: error("OpenAI did not return a registered client ID.")
            exchange(code, clientId, verifier, redirect, nonce).also { result ->
                if (existing != null && result.subject != existing.subject) error("The selected ChatGPT account did not match the saved account.")
            }
        } finally { server.close(); activeServer = null }
    }

    suspend fun refresh(tokens: Tokens): Tokens = withContext(Dispatchers.IO) {
        val response = http.newCall(Request.Builder().url("$authHost/api/accounts/oauth/token")
            .post(FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", tokens.refreshToken)
                .add("client_id", tokens.clientId).add("resource", "https://api.openai.com/v1").build()).build()).execute()
        val text = response.body?.string().orEmpty()
        check(response.isSuccessful) { "Your ChatGPT sign-in expired. Please connect again." }
        val json = JSONObject(text)
        tokens.copy(accessToken = json.getString("access_token"), refreshToken = json.optString("refresh_token", tokens.refreshToken),
            expiresAtEpochSeconds = System.currentTimeMillis()/1000 + json.getLong("expires_in"))
    }

    private fun exchange(code: String, clientId: String, verifier: String, redirect: String, nonce: String): Tokens {
        val response = http.newCall(Request.Builder().url("$authHost/api/accounts/oauth/token").post(FormBody.Builder()
            .add("grant_type", "authorization_code").add("code", code).add("code_verifier", verifier)
            .add("client_id", clientId).add("redirect_uri", redirect).add("resource", "https://api.openai.com/v1").build()).build()).execute()
        val text = response.body?.string().orEmpty()
        check(response.isSuccessful) { "ChatGPT sign-in could not be completed. Please try again." }
        val json = JSONObject(text)
        val scopes = json.optString("scope").split(' ').filter(String::isNotBlank).toSet()
        require("chatgpt.tokens.use.direct" in scopes) { "ChatGPT plan usage was not authorised for this app." }
        val idToken = json.getString("id_token")
        val claims = validateIdToken(idToken, clientId, nonce)
        Tokens(clientId, json.getString("access_token"), json.getString("refresh_token"), idToken,
            claims.getString("sub"), claims.optString("email").takeIf(String::isNotBlank), scopes,
            System.currentTimeMillis()/1000 + json.getLong("expires_in"))
    }

    private fun validateIdToken(token: String, clientId: String, nonce: String): JSONObject {
        val jwt = SignedJWT.parse(token)
        require(jwt.header.algorithm == JWSAlgorithm.RS256 || jwt.header.algorithm == JWSAlgorithm.ES256)
        val keysResponse = http.newCall(Request.Builder().url("$authHost/.well-known/jwks.json").build()).execute()
        val keys = JWKSet.parse(keysResponse.body?.string() ?: error("Unable to validate ChatGPT sign-in."))
        val key = keys.keys.firstOrNull { it.keyID == jwt.header.keyID } ?: error("Unable to validate ChatGPT sign-in.")
        val verifier = when (key) {
            is com.nimbusds.jose.jwk.RSAKey -> RSASSAVerifier(key.toRSAPublicKey())
            is com.nimbusds.jose.jwk.ECKey -> com.nimbusds.jose.crypto.ECDSAVerifier(key.toECPublicKey())
            else -> error("Unsupported sign-in verification key.")
        }
        require(jwt.verify(verifier)) { "ChatGPT sign-in could not be verified." }
        val c = jwt.jwtClaimsSet
        require(c.issuer == authHost && clientId in c.audience && c.expirationTime.after(java.util.Date()) && c.getStringClaim("nonce") == nonce)
        return JSONObject().put("sub", c.subject).put("email", c.getStringClaim("email") ?: "")
    }

    private fun randomValue(bytes: Int): String = ByteArray(bytes).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
}
