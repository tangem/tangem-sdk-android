# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Tangem SDK Android — a multi-module Kotlin SDK for interacting with Tangem NFC hardware wallets. The SDK handles private key generation/storage, data signing, and card management via NFC communication.

## Build & Development Commands

```bash
# Build the project
./gradlew build

# Run unit tests (core module has the main test suite)
./gradlew :tangem-sdk-core:test

# Run a single test class
./gradlew :tangem-sdk-core:test --tests "com.tangem.crypto.Bip39Test"

# Run a single test method
./gradlew :tangem-sdk-core:test --tests "com.tangem.crypto.Bip39Test.testMethodName"

# Run detekt (linting/code quality)
./gradlew detekt

# Assemble Android library AAR
./gradlew :tangem-sdk-android:assembleRelease

# Assemble demo app
./gradlew :tangem-sdk-android-demo:assembleDebug
```

## Module Structure

```
tangem-sdk-core/         Platform-independent core (Kotlin). All business logic,
                         crypto, card operations, TLV protocol, APDU handling.
                         Package: com.tangem

tangem-sdk-android/      Android-specific bindings. NFC reader, biometric auth,
                         secure storage, UI dialogs. Package: com.tangem.sdk
                         Depends on: tangem-sdk-core

tangem-sdk-android-demo/ Demo Android app showcasing SDK usage.
                         Depends on: tangem-sdk-android, tangem-sdk-core

tangem-sdk-jvm/          JVM-only module (currently excluded from settings.gradle).
tangem-sdk-jvm-demo/     JVM demo app (currently excluded).

tangem-android-tools/    CI infrastructure, detekt config, Docker setup.
```

## Architecture

### Entry Point & Session Lifecycle

`TangemSdk` (in `tangem-sdk-core`) is the main facade. All card operations flow through:

```
TangemSdk.operation()
  → CardSession (manages NFC lifecycle, encryption, user codes)
    → Command.transceive() (serialize → encrypt → NFC send → decrypt → deserialize)
      → CardReader.transceiveApdu() (NFC transport)
```

`SessionEnvironment` carries mutable state (card data, encryption keys, user codes) across commands within a session.

### Command Pattern

All card operations implement `CardSessionRunnable<T>`. The abstract `Command<T>` class provides the serialize/transceive/deserialize pipeline using TLV encoding. Commands live in `tangem-sdk-core/.../operations/` organized by category (sign, wallet, files, backup, attestation, etc.).

### NFC Communication (Android)

- `NfcManager` — manages Android NFC adapter lifecycle, reader mode enable/disable, tied to Activity lifecycle
- `NfcReader` — implements `CardReader` interface using `IsoDep` and `NfcV` transports with Kotlin Flows

### TLV Protocol

Card communication uses Tag-Length-Value encoding. `TlvBuilder` constructs command payloads, `TlvDecoder` parses responses. Tags are defined in the `TlvTag` enum.

### Error Handling

`CompletionResult<T>` sealed class (Success/Failure) with monadic operations (`map`, `flatMap`, `flatMapOnFailure`). Errors extend `TangemError` interface, with `TangemSdkError` sealed class containing all SDK-specific errors.

### Crypto Module

`tangem-sdk-core/.../crypto/` — supports Secp256k1, Secp256r1, Ed25519, BLS12-381 curves. HD wallet derivation via BIP32/BIP44 in `crypto/hdWallet/`. BIP39 mnemonic support in `crypto/bip39/`.

### UI Abstraction

`SessionViewDelegate` interface (in core) abstracts UI from SDK logic. `DefaultSessionViewDelegate` (in android module) provides the default NFC dialog implementation.

## Build Configuration

- **Gradle:** 8.14.1, Kotlin 2.1.10, AGP 8.10.1
- **Android:** compileSdk 34, minSdk 24, targetSdk 34
- **Linting:** Detekt with config at `tangem-android-tools/detekt-config.yml`, baseline at `detekt-baseline.xml`
- **Testing:** JUnit 5 + MockK + Truth (core), JUnit 4 + Espresso (android)
- **Version:** stored in `VERSION` file at project root
- **Publishing:** GitHub Packages and JitPack

## Code Conventions

- Core module is pure Kotlin (no Android dependencies) — keep it that way
- Callback pattern: `CompletionCallback<T> = (CompletionResult<T>) -> Unit`
- Android initialization via `TangemSdk.init()` / `TangemSdk.initWithBiometrics()` extension functions in `tangem-sdk-android`
- Groovy-based Gradle files (not Kotlin DSL)
- Codeowners: `@tangem-developments/android-team`