# Qimi · 栖密

[简体中文](README.md) · **English**

[Project website](https://chenzhiyong1994.github.io/qimi/) · [GitHub](https://github.com/chenzhiyong1994/qimi) · [Report an issue](https://github.com/chenzhiyong1994/qimi/issues)

[1.0.0 release](https://github.com/chenzhiyong1994/qimi/releases/tag/v1.0.0) · [Download Android APK](https://github.com/chenzhiyong1994/qimi/releases/download/v1.0.0/qimi-1.0.0.apk) · [Release notes and installation boundaries](docs/releases/1.0.0.md)

**Passwords stay local. Everyday life feels lighter.**

![Qimi product banner: Passwords stay local. Everyday life feels lighter.](assets/promo/qimi-banner.png)

Qimi is an open-source local password manager for Android. It protects account information with a master password and makes saving, finding, and manually using credentials more convenient. The app does not request the `INTERNET` permission and has no cloud accounts, cloud sync, network analytics, or remote icons.

**1.0.0** provides a local vault, entry editing, password generation, and manual use, covering S1 and its increments. The release version does not imply a complete MVP or completed security acceptance: backup and recovery, drafts, biometrics, and autofill are not implemented, and full security review and practical verification of system cloud backup / device-transfer exclusions remain unfinished. Use synthetic data for testing only; do not store real passwords. See [Project status](docs/project-status.md) and [Validation records](docs/validation-s1.md) for implementation, validation, and actual publication status.

## Current capabilities

- Create a local encrypted vault and unlock it with a master password.
- Add entries or edit their name, username, password, website, and notes from the details page, preserving original values. Changes update the existing entry only when saved.
- Generate passwords when adding or editing an entry: Simple, Normal (default), or Complex presets, with optional advanced complexity, 8–128 character length, and character types. A candidate fills the form only after you choose to use it.
- Search entries in a list showing only names and a fixed password mask; open details to check the username and reveal or copy passwords when needed.
- Lock manually or when the app enters the background; automatically hide revealed passwords and clear the sensitive clipboard.
- A forest-green / warm-white interface, a dark theme, and native Compose interactions; a distinct brand mark and an adaptive app icon.

The vault uses KDBX 4.1. Saving includes candidate-file verification, an atomic commit, and rollback on failure. See the [Vault core contract](vault-core/README.md) for parameters, data preservation, and compatibility boundaries. Interoperability with external KDBX implementations has not yet been verified.

## Roadmap

| Increment | Planned work |
| --- | --- |
| S2 | Offline encrypted backups, readability verification, recovery, and preservation of data on failure |
| S3 | Organizing scattered information, other sign-in methods, encrypted drafts, and resuming interrupted work |
| S4 | Tags and favorites, generated-password draft/copy integration, history, and a recycle bin |
| S5 | Biometric unlock and system autofill for trusted targets |
| S6 | A complete MVP, compatibility and security acceptance, and release preparation |

The capabilities in this table are not implemented yet. Basic editing and password generation were brought forward into S1 at the user's request. Editing has no drafts: backgrounding or locking discards unsaved input, and copying within the generator is not included. S2 remains the next priority. See the [Iteration roadmap](docs/iteration-roadmap.md) for dependencies and acceptance boundaries.

## Build and validation

Install **JDK 17**, **Android SDK Platform 36**, and **Build Tools 35.0.0**, then configure `JAVA_HOME` and `ANDROID_HOME`. The project includes the **Gradle 8.14.5** Wrapper; no global Gradle installation is required. The first build needs to download the declared tools and dependencies. The app itself has no networking functionality. The minimum SDK configuration is Android 10 (API 29); support across actual devices is still being verified.

On Windows with PowerShell 7, run from the project root:

```powershell
pwsh -NoProfile -File scripts/android.ps1
pwsh -NoProfile -File scripts/check-package.ps1
pwsh -NoProfile -File scripts/check-docs.ps1
```

On Linux / macOS, configure the same JDK and SDK and run the Gradle Wrapper:

```bash
./gradlew :vault-core:test :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest \
  --no-daemon --console=plain
```

The default build writes the debug APK to `app/build/outputs/apk/debug/app-debug.apk` and the unsigned release APK to `app/build/outputs/apk/release/app-release-unsigned.apk`. The distribution filename is `qimi-1.0.0.apk`; signing is a separate packaging step, with signing material kept outside the repository. See the [Development guide](docs/development.md) for versions, dependency verification, rebuilding, and device tests. Device tests recreate synthetic fixtures and must run only on a dedicated emulator containing synthetic test data.

In debug builds, the unlock page on a standard Android Emulator offers a one-tap test-vault unlock button. It works only with an existing vault using the synthetic fixture passphrase. It follows normal authentication and does not create or overwrite a vault. Release builds contain neither the button nor the synthetic passphrase.

## Contributing

Use [Issues](https://github.com/chenzhiyong1994/qimi/issues) to report reproducible problems and [Pull Requests](https://github.com/chenzhiyong1994/qimi/pulls) to improve the implementation, tests, or documentation. Use synthetic data only. Do not upload real vaults, passwords, keys, or logs that have not been sanitized. Changes involving vault storage, recovery, or autofill must preserve the existing security boundaries and include relevant validation evidence.

Documentation and maintenance entry points:

- [Project status](docs/project-status.md) and [Project instructions](AGENTS.md): current facts and maintenance conventions.
- [Product plan](docs/product-plan.md) and [Experience specification](docs/experience-spec.md): goals, scope, and user journeys.
- [Security and data specification](docs/security-and-data.md) and [Acceptance scenarios](docs/acceptance.md): data boundaries and validation requirements.
- [Development guide](docs/development.md), [Vault core](vault-core/README.md), and [Decision records](docs/decisions.md): rebuilding, storage contracts, and technical decisions.
- [Brand assets](assets/brand/README.md): vector logos, transparent PNGs, and export instructions.

The linked detailed documentation is currently written in Simplified Chinese.

## License

Project-owned source code and finished brand assets are licensed under [Apache-2.0](LICENSE). Third-party licenses and attributions are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [NOTICE](NOTICE).
