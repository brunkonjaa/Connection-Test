# Testing

## Automated

```sh
gradle --no-daemon testDebugUnitTest
gradle --no-daemon lintDebug
gradle --no-daemon assembleDebug
```

Unit tests cover authentication UI state transitions, cancellation/expiry messages, conversation state, streamed event parsing, malformed or incomplete streams, model response parsing, and HTTP authentication errors. `MockWebServer` is used for network tests. These tests validate client behavior only; they do not authenticate to OpenAI or prove subscription entitlement.

GitHub Actions runs all three commands and publishes the debug APK artifact. CI uses no user or ChatGPT credentials.

## Real-device verification

On 17 October 2026, the user supplied screenshots from a physical Android device showing:

- OpenAI's authorization notice for Ask Buster and permission to use ChatGPT plan limits.
- Return to Ask Buster through its local `127.0.0.1` callback page.
- The app in a connected state and a response to the initial rosemary question, labelled `GPT-6.1-Sol`.
- A second in-app response to a fig-tree follow-up question.

This is evidence of a real authenticated text conversation in the native app. The screenshots do not identify device model, Android version, or build SHA. Do not infer those details from the screenshots. The CI run for commit `6771f1d7ae0af14345f93cbed3efcb024691e984` passed tests, lint and APK build, but CI itself does not establish account authentication or plan usage.

### Still to test on-device

1. Record device model, Android version and installed APK build SHA.
2. Close and relaunch the app; confirm the encrypted session restores and a question can be submitted.
3. Disconnect and reconnect; confirm credentials are removed on disconnect.
4. Exercise browser cancellation, revoked access/token expiry, airplane mode, failed request and retry; confirm errors are understandable and no credentials appear in logs.
5. Test photo input with an account-listed model that supports images.
6. If required, observe incremental response deltas on-device; screenshots prove a completed answer appeared, not that the UI visibly updated during streaming.

The screenshots show literal Markdown markers such as `**bold**` in response text. Track Markdown rendering as a UI follow-up; it does not invalidate the authenticated inference result.
