# Ask Buster — Connection Test

A minimal Android proof of concept testing the official **Sign in with ChatGPT** open-source flow and native Responses API conversation. It asks the rosemary question after sign-in and displays the streamed response. It does not use an OpenAI API key or a separate API billing account.

Licensed under AGPL-3.0; see [LICENSE](LICENSE).

## Current status

This client implements the documented OAuth + PKCE loopback flow, encrypted local token storage, account-specific model discovery, streamed text inference, and optional photo input. **A real ChatGPT Plus sign-in and response have not yet been verified on a physical Android device.** See [FEASIBILITY.md](FEASIBILITY.md) for platform caveats and what remains.

## Build

Requirements: JDK 17, Android SDK Platform 36 and Build Tools 36.0.0, and Gradle 8.13.

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions runs the same checks and attaches the APK as a 30-day workflow artifact. No ChatGPT credentials are needed for CI.

## Install

Download `app-debug.apk` from a successful **Android CI** run's `ask-buster-debug-apk` artifact, transfer it to an Android 8.0+ device, and install it. Android may ask you to allow installation from that file source. Launch **Ask Buster — Connection Test**, tap **Continue with ChatGPT**, authorize the app in the system browser, and return to the app. A genuine account-authorized response to the rosemary question should stream into the conversation.

## Scope

This is an independent, minimal feasibility experiment. The current implementation intentionally has no backend, API key, lifestyle features, or production release signing.
