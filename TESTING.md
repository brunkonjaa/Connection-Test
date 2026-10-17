# Testing

## Automated

```sh
gradle --no-daemon testDebugUnitTest
gradle --no-daemon lintDebug
gradle --no-daemon assembleDebug
```

Unit tests cover authentication UI state transitions, cancellation/expiry messages, conversation state, streamed event parsing, malformed or incomplete streams, model response parsing, and HTTP authentication errors. `MockWebServer` is used for network tests. These tests validate client behavior only; they do not authenticate to OpenAI or prove subscription entitlement.

GitHub Actions runs all three commands and publishes the debug APK artifact. CI uses no user or ChatGPT credentials.

## Real-device test checklist (not yet completed)

1. Install the CI debug APK on an Android 13 device (Samsung A12, if available) with a browser signed in to the intended ChatGPT Plus account.
2. Tap **Continue with ChatGPT** and verify the system browser opens; approve the named app and plan-use permission.
3. Return to the app. Verify it shows a connected account and submits the rosemary question automatically.
4. Confirm a streamed, non-empty answer appears from a live Responses API request, not from local canned content.
5. Close and relaunch the app. Confirm valid tokens restore the connection and a question can be submitted.
6. Select **Disconnect**, verify credentials are cleared, then reconnect.
7. If text succeeds, select a photo and verify image input on an account-listed model that supports it.
8. Exercise browser cancellation, revoked access/token expiry, airplane mode, a failed request and retry. Confirm errors are actionable and no credentials appear in logs.

No physical-device checks are claimed by this setup. Record device model, Android version, build SHA, action and observed result before marking an item complete.
