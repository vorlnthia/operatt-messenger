# Operatt Messenger Android delivery

## What is ready

- Android testing builds are produced by the `Operatt Android` workflow for open pull requests.
- Android package ID for the Conversations flavor: `com.operatt.messenger`.
- Testing artifacts are debug APKs, not Play Store releases.
- The optional `Operatt Signed Android Release` workflow builds a signed direct-distribution APK and a Play Store AAB **only after** a private signing key is configured.
- The XMPP domain currently specified in the Android registration UI is `chat.operatt.com`. That does not itself enable server registration. Verify Prosody/ejabberd account creation and policy separately before a public launch.

## Update and signing policy

Android installation updates need the same application ID, the same signing identity, and a higher version code. Ephemeral GitHub-hosted debug signing keys are **not stable between runs**, so a newly built debug APK may not install over a prior debug APK. Do not uninstall an existing installation without first exporting important messages and encryption keys.

For stable releases, generate a long-term private upload/signing keystore offline, back it up securely, and **never commit it to this repository**. Use the same keystore and alias for future versions. The Gradle project uses a single keystore password for both store and key.

For example, on a trusted developer machine:

```bash
keytool -genkeypair -v -keystore operatt-release.jks -alias operatt -keyalg RSA -keysize 3072 -validity 10000
```

Use a strong password. Preserve the keystore and credentials in a separate secure backup. In your repository's **Settings > Secrets and variables > Actions**, define:

- `OPERATT_SIGNING_KEYSTORE_BASE64`: Base64 representation of the entire keystore file, as a single line.
- `OPERATT_SIGNING_PASSWORD`: Keystore/key password.
- `OPERATT_SIGNING_ALIAS`: Alias, e.g. `operatt`.

Run **Actions > Operatt Signed Android Release > Run workflow** from the default branch after reviewing and merging the changes. This workflow does not submit anything to Google Play. Only authorized app owners can complete Play Console setup, privacy/data-safety declarations, and track submissions.

## Before publishing

1. Confirm `chat.operatt.com` is actually the XMPP registration domain and the server permits the intended registration flow (including invitation-free signups, throttling, and anti-abuse controls). The web/admin domain and an XMPP JID domain may differ.
2. Test creating an account, signing in, messaging, reconnecting, multi-device key management, and account removal with **throwaway accounts**.
3. Review the legacy Snikket assets, links, app icon/theme, notification labels, support and privacy links, translations, and branding.
4. Verify all data/backup implications and implement a non-destructive account-switching experience.
5. Define a release version name, bump `versionCode` for each new release, and verify upgrade paths on Android devices.
6. Perform signing, security/privacy tests, and Play Console policy review before publishing to testers or production.

For now, use the PR's APK only for **test installation**. Keep any existing application installed until backup and update/signing compatibility are understood.
