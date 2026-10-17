# Sign in with ChatGPT feasibility

Research checked against OpenAI's official documentation on 17 October 2026.

## Findings

| Question | Finding |
|---|---|
| Can eligible ChatGPT subscriptions be used instead of an API key? | Yes, for eligible open-source clients. OpenAI says eligible ChatGPT Plus and Pro users can authorize plan usage for eligible AI requests. This uses the documented Responses API OAuth scopes; ordinary API billing is separate. |
| Is Kotlin Android explicitly listed as a supported client? | No Android platform, Android SDK, or Android reference client is identified in the reviewed documentation. The general open-source flow documents a local HTTP callback on `127.0.0.1`, system-browser authorization, and a local client. A native Android implementation of that exact flow appears technically plausible, but Android compatibility is **not confirmed by OpenAI documentation** and remains a real implementation/device test. |
| Does public GitHub visibility alone establish eligibility? | No. The docs describe this as a flow for open-source apps/clients, but do not say that making a repository public alone establishes eligibility. This repository includes an OSI-approved license, and the user must authorize the client and plan use during OAuth. |
| Is a pre-issued client ID or partner approval required? | For the documented OSS flow, no. First authorization uses `client_id=dynamic_agent_client`; OpenAI returns a user-bound client ID. The user names and authorizes the agent in the browser. The docs say the direct flow needs neither a client secret nor a partner API key. Later sign-ins reuse the issued ID. |
| OAuth mechanisms? | System browser; fresh state, OIDC nonce and PKCE S256 per attempt; HTTP loopback `127.0.0.1` with exact callback URI (scheme/host/path fixed; port may vary); code exchange with PKCE verifier; verify ID-token signature, issuer, client audience, expiry and nonce; require `chatgpt.tokens.use.direct`. |
| Can it call Responses API? | Yes. The official flow directs an OAuth bearer token to `POST https://api.openai.com/v1/responses`. Requests must set `stream=true` and `store=false`, with conversation context in `input`. Private `backend-api` endpoints are explicitly not supported. |
| Which models are eligible? | Do not hardcode names. Fetch `GET /v1/models` with the selected account's OAuth token; display models with `visibility=list` and send the selected `slug`. Eligibility depends on the account catalog and workspace policy. The docs identify Plus/Pro plan eligibility, not a fixed model allowlist. |
| Image input? | Preview limitations say text, images and files are supported when the selected model accepts them. Audio/video and Files upload/transcription APIs are unsupported. This app's optional photo input is not yet verified with a live account/device. |
| Can this be standalone? | The OSS flow is for open-source and locally hosted apps and uses OAuth tokens directly with the public Responses endpoint. It does not require a custom backend, client secret, partner API key, or separately billed API key. Android-specific callback operation remains unverified. |
| Official Android SDK/reference? | None found in the official SIWC quickstart and OSS sign-in/inference docs reviewed. The protocol is documented generically; this repository implements it directly with Android platform APIs and standard libraries. |

## Current result

**Potentially supported but unverified.** The Android project implements the documented public-client pattern without an API key. CI and physical-device validation remain outstanding until the workflow runs and an Android device completes OAuth plus genuine inference. A successful build or mocked test is not proof of subscription usage.

No static OAuth credentials or pre-registration approval are identified as blockers: this OSS path dynamically registers during user authorization. OpenAI still must accept the client registration and grant the requested plan-use scope to the user's account/workspace.

## Official evidence

- [Sign in with ChatGPT Quickstart](https://developers.openai.com/siwc/quickstart) — eligible Plus/Pro plan usage, OSS flow, scopes.
- [ChatGPT plan usage for open-source apps: overview](https://developers.openai.com/siwc/token-sharing-open-source) — local client model, host ID, dynamic registration and end-to-end flow.
- [Registration and sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in) — OAuth parameters, callback requirements, PKCE, validation, token exchange, JWKS and credential handling.
- [Models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference) — account model catalog and Responses API contract.
- [Preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations) — `store`/`stream` requirements, image and unsupported input/tool limits.
