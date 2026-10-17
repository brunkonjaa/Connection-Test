# Architecture

## Components

- `MainActivity` and Compose screens: connection status and one conversation surface.
- `ConnectionViewModel`: auth and conversation state, initial test question, token refresh, retry and disconnect.
- `OpenAiOAuth`: system-browser authorization, local loopback callback listener, PKCE, token exchange/refresh, OIDC verification.
- `TokenVault`: encrypts the OAuth record using AES-GCM and a non-exportable Android Keystore key.
- `ChatApi`: account-specific model listing and streamed Responses API calls.
- `ConversationReducer` / `AuthStateMachine`: state seams exercised by unit tests.

## Sign-in flow

1. The app binds an ephemeral HTTP server only to `127.0.0.1`, using `/auth/callback`.
2. It creates fresh random OAuth `state`, OIDC `nonce`, PKCE verifier/challenge, and a persistent opaque per-install `ext_agent_host_id`.
3. The system browser opens OpenAI's documented authorize URL. First sign-in uses `dynamic_agent_client`; later sign-ins reuse the issued client ID.
4. The callback path, state, OAuth errors, returned client ID and code are validated. The app exchanges the code with the original redirect URI and PKCE verifier.
5. The ID-token signature is checked against OpenAI JWKS; issuer, audience, expiry and nonce are checked. The granted `chatgpt.tokens.use.direct` scope is mandatory.
6. Tokens and verified account identity are encrypted in private app storage. Disconnect deletes that encrypted record.

## Inference flow

The app calls `GET https://api.openai.com/v1/models` with the OAuth bearer token and selects the first model marked `visibility=list` for that account. It posts conversation input to `POST https://api.openai.com/v1/responses` with `stream=true` and `store=false`, then consumes `response.output_text.delta` events through `response.completed`. It sends text history in every request; no API conversation storage is used. A photo selected through Android Photo Picker is sent as an image data URL when the selected model accepts images.

There is no custom server. Inference requests go directly from the Android client to documented OpenAI API endpoints.
