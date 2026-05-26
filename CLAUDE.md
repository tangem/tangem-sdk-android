# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Tangem SDK Android — an NFC-based hardware wallet SDK enabling 3rd-party apps to create/store private keys and sign data on Tangem cards. Three-module Gradle project: pure Kotlin/JVM core, Android library, and demo app.

## Build & Test Commands

```bash
./gradlew assemble                          # Build all modules
./gradlew test                              # Run all unit tests
./gradlew :tangem-sdk-core:test             # Run core module tests only
./gradlew :tangem-sdk-android:test          # Run android module tests only
./gradlew detekt                            # Static analysis (auto-correct enabled)
./gradlew :tangem-sdk-core:test --tests "com.tangem.crypto.bip39.DefaultBIP39Test"  # Single test class
./gradlew :tangem-sdk-android:testDebugUnitTest --tests "com.tangem.SomeTest"      # Single test (Android module)
./gradlew publishToMavenLocal                   # Publish artifacts to ~/.m2 for local testing
```

Fastlane orchestrates CI: `bundle exec fastlane test` runs clean assemble + detekt + all module tests.

## Module Architecture

- **tangem-sdk-core** — Pure Kotlin/JVM. All card operations, cryptography, APDU protocol, HD wallet derivation. No Android dependencies. This is where most business logic lives.
- **tangem-sdk-android** — Android library. NFC reader (`NfcCardReader`), default UI dialogs (`DefaultSessionViewDelegate`), biometric auth, secure storage via Android Keystore.
- **tangem-sdk-android-demo** — Reference Android app demonstrating SDK usage.

## Core Design Patterns

**Command pattern over NFC**: Operations inherit from `Command<T : CommandResponse>` which serializes to `CommandApdu`, transceives via NFC, and deserializes the response. Higher-level `Task` classes compose multiple commands (e.g., `ScanTask` = read + attestation + derivation).

**CardSession**: Central orchestrator managing NFC communication lifecycle, APDU transceiving, encryption, and error handling. Runs on `Dispatchers.IO` with `SupervisorJob`.

**CompletionResult<T>**: Sealed class (`Success`/`Failure`) used throughout instead of exceptions. Has functional operators (`map`, `flatMap`, `doOnSuccess`, `doOnFailure`).

**Entry point**: `TangemSdk` — requires `CardReader`, `SessionViewDelegate`, and `Config`. Key methods: `scanCard()`, `sign()`, `createWallet()`, `deriveWalletPublicKey()`.

## Code Style

Detekt — static analysis with auto-correct enabled. Config: `tangem-android-tools/detekt-config.yml`, baseline: `detekt-baseline.xml`.

## Dependencies

All versions and dependency coordinates centralized in `dependencies.gradle` (maps `versions` and `libraries`). Module build files reference `libraries.xxx` — no hardcoded versions in module files.

## Key Technical Details

- Kotlin 2.1.10, AGP 8.10.1, Target SDK 34, Min SDK 24
- Crypto: SpongyCastle (BouncyCastle fork), EdDSA, kBLS for BLS signatures
- Networking: Retrofit + Moshi
- No DI framework — manual constructor injection
- Version defined in `/VERSION` file (currently 3.9.2)
- Published to GitHub Maven Packages (`com.tangem.tangem-sdk-kotlin`)

## Branching

See @.claude/rules/git-rules.md