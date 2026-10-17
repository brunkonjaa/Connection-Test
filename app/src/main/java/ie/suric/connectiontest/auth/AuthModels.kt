package ie.suric.connectiontest.auth

data class Tokens(
    val clientId: String,
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val subject: String,
    val email: String?,
    val scopes: Set<String>,
    val expiresAtEpochSeconds: Long
)

sealed interface AuthState {
    data object SignedOut : AuthState
    data object SigningIn : AuthState
    data class SignedIn(val email: String?) : AuthState
    data class Failed(val message: String) : AuthState
}

object AuthStateMachine {
    fun transition(current: AuthState, event: Event): AuthState = when (event) {
        Event.Begin -> AuthState.SigningIn
        is Event.Success -> AuthState.SignedIn(event.email)
        Event.Cancel -> AuthState.SignedOut
        Event.Disconnect -> AuthState.SignedOut
        is Event.Error -> AuthState.Failed(event.message)
        Event.Expired -> AuthState.Failed("Your ChatGPT connection expired. Sign in again to continue.")
    }
    sealed interface Event {
        data object Begin : Event
        data class Success(val email: String?) : Event
        data object Cancel : Event
        data object Disconnect : Event
        data object Expired : Event
        data class Error(val message: String) : Event
    }
}

object TokenExpiryPolicy {
    fun shouldRefresh(expiresAtEpochSeconds: Long, nowEpochSeconds: Long, safetyWindowSeconds: Long = 60): Boolean =
        expiresAtEpochSeconds <= nowEpochSeconds + safetyWindowSeconds
}
