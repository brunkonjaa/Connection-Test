# Security decisions

- No API keys, OAuth credentials, passwords, private user data, or signing keys are committed. GitHub Actions requires no ChatGPT secrets.
- Authentication opens the system browser. The app does not collect passwords, read browser data/cookies, use WebView login, or access ChatGPT app storage.
- The temporary callback binds only to IPv4 loopback at `127.0.0.1`; state and PKCE are fresh per attempt, OIDC nonce and ID token are validated, and callback path/client identity are checked.
- Access, refresh and ID tokens are encrypted with AES-GCM under a non-exportable Android Keystore key. The per-install host ID is an opaque UUID and contains no account identity.
- Disconnect clears the encrypted token record. Token refresh saves a rotated refresh token.
- Requests use HTTPS and Android's default certificate verification. No cleartext app network traffic is enabled.
- The only requested Android permission is `INTERNET`. Android Photo Picker grants access only to an image explicitly selected by the user.
- The sole exported activity is the required launcher activity. OAuth callback uses a loopback socket, not an exported deep-link component.
- No bearer tokens, authorization codes, image contents, request bodies or account data are logged. HTTP failures show service messages where available and generic status messages otherwise.
- Responses requests include `store=false`; conversation history remains in memory and is resent in each request.

## Known limits

- OpenAI's official docs do not confirm Android as a platform or provide a native SDK/reference client. Treat native callback behavior and OAuth policy acceptance as unverified until real-device testing.
- Token revocation is not implemented; local credentials are erased on disconnect, and access can be withdrawn through ChatGPT settings.
- Pull requests run GitHub's dependency review and fail for high-severity dependency alerts; Dependabot checks Gradle and workflow dependencies weekly. This does not replace a full release-time security assessment.
- A debug APK is suitable for private testing only. Production distribution requires a separate signing and release process.
