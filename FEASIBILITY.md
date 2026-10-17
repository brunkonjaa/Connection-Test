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
| Image input? | Preview limitations say text, images and files are supported when the selected model accepts them. Audio/video and Files upload/transcription APIs are unsupported. This app's optional photo input has not yet been verified with a live account/device. |
| Can this be standalone? | The OSS flow is for open-source and locally hosted apps and uses OAuth tokens directly with the public Responses endpoint. It does not require a custom backend, client secret, partner API key, or separately billed API key. Android-specific callback operation remains unverified. |
| Official Android SDK/reference? | None found in the official SIWC quickstart and OSS sign-in/inference docs reviewed. The protocol is documented generically; this repository implements it directly with Android platform APIs and standard libraries. |

## Current result

**Supported and verified for text authentication and inference (17 October 2026).** The user supplied Android screenshots showing the OpenAI authorization notice granting Ask Buster permission to use ChatGPT plan limits, the return to the app's loopback callback, an authenticated account in Ask Buster, and two responses displayed in the app. The app labelled the selected account-listed model `GPT-6.1-Sol`; the second response followed a separate fig-tree question. The screenshot evidence is consistent with the documented OAuth and Responses API path implemented here. No API key or custom backend is part of that path.

The screenshots do not show the device model, Android version, or app build SHA, so those are not asserted. Photo input, app-restart session recovery, disconnect/reconnect, cancellation/expiry behavior on-device, and incremental streaming behavior remain unverified. A visible presentation defect is that Markdown markers such as `**bold**` appear literally in answers.

The CI run for commit `6771f1d7ae0af14345f93cbed3efcb024691e984` passed unit tests, Android lint, debug APK assembly, and artifact upload: [workflow run 38993099752](https://github.com/brunkonjaa/Connection-Test/actions/runs/38993099752). CI is not the evidence for real ChatGPT use; the user-supplied device screenshots are.

No static OAuth credentials or pre-registration approval were required in this observed flow: OpenAI dynamically registered the client during user authorization and the user granted plan-use access. This result verifies the tested account and flow; it does not guarantee eligibility for every account or workspace policy.

## Official evidence

- [Sign in with ChatGPT Quickstart](https://developers.openai.com/siwc/quickstart) — eligible Plus/Pro plan usage, OSS flow, scopes.
- [ChatGPT plan usage for open-source apps: overview](https://developers.openai.com/siwc/token-sharing-open-source) — local client model, host ID, dynamic registration and end-to-end flow.
- [Registration and sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in) — OAuth parameters, callback requirements, PKCE, validation, token exchange, JWKS and credential handling.
- [Models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference) — account model catalog and Responses API contract.
- [Preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations) — `store`/`stream` requirements, image and unsupported input/tool limits.
