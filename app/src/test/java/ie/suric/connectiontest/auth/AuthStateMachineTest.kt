package ie.suric.connectiontest.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthStateMachineTest {
    @Test fun cancelledSignInReturnsToSignedOut() {
        assertEquals(AuthState.SignedOut, AuthStateMachine.transition(AuthState.SigningIn, AuthStateMachine.Event.Cancel))
    }
    @Test fun successfulSignInUpdatesAccountState() {
        assertEquals(AuthState.SignedIn("bruno@example.test"), AuthStateMachine.transition(AuthState.SigningIn, AuthStateMachine.Event.Success("bruno@example.test")))
    }
    @Test fun expiryProducesReconnectMessage() {
        assertEquals("Your ChatGPT connection expired. Sign in again to continue.", (AuthStateMachine.transition(AuthState.SignedIn(null), AuthStateMachine.Event.Expired) as AuthState.Failed).message)
    }
    @Test fun disconnectReturnsToSignedOut() {
        assertEquals(AuthState.SignedOut, AuthStateMachine.transition(AuthState.SignedIn("x"), AuthStateMachine.Event.Disconnect))
    }
    @Test fun invalidSignInSurfacesFailureWithoutAuthenticating() {
        assertEquals(AuthState.Failed("Invalid callback"), AuthStateMachine.transition(AuthState.SigningIn, AuthStateMachine.Event.Error("Invalid callback")))
    }
    @Test fun tokenExpiryPolicyRefreshesAtExpiryAndInsideSafetyWindow() {
        assertFalse(TokenExpiryPolicy.shouldRefresh(1_061, 1_000))
        assertTrue(TokenExpiryPolicy.shouldRefresh(1_060, 1_000))
        assertTrue(TokenExpiryPolicy.shouldRefresh(999, 1_000))
    }
}
