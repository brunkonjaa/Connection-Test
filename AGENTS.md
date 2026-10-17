# Project rules

- Keep this repository limited to the Ask Buster connection proof of concept.
- Use Kotlin, Jetpack Compose, Gradle Kotlin DSL, Java 17, and AndroidX.
- Keep OAuth, credential storage, network transport, state, and UI in separate components.
- Use only the documented Sign in with ChatGPT open-source OAuth flow and public Responses API. Do not call ChatGPT private backend endpoints.
- Never add API keys, account credentials, tokens, private account data, or signing keys to source or logs.
- Do not collect passwords, extract cookies, weaken TLS, or add a WebView-based sign-in flow.
- Validate OAuth state, OIDC nonce, ID-token signature/issuer/audience/expiry, and granted plan-use scopes before inference.
- Persist tokens only in the Android Keystore-backed encrypted vault; clear the vault on Disconnect.
- Keep API requests streamed and set `store=false`; only use model identifiers returned for the signed-in account.
- Use mocks in automated tests. A mock result is not evidence of a real ChatGPT connection.
- Minimise manifest permissions and keep all non-launcher components non-exported.
- Do not add unrelated lifestyle features or modify other repositories.
- Update `FEASIBILITY.md`, `SECURITY.md`, `ARCHITECTURE.md`, and `TESTING.md` when behavior or integration assumptions change.
