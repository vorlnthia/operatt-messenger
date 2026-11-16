# Operatt Messenger — iPhone app development

## Status

This folder contains a **reproducible iOS native source preparation and simulator build**, not a signed iPhone installation package. The base is [Snikket iOS](https://github.com/snikket-im/snikket-ios) pinned at commit `6948e50bea18487e9f2329f678ec834c3826fd64`, GPL-3.0, itself based on Tigase Siskin.

The preparation script currently:
- Gives the app the distinct **Operatt Messenger** name, bundle identifiers and a non-Snikket icon.
- Defaults in-band XMPP registration to `chat.operatt.com`.
- Allows short usernames to be expanded to `username@chat.operatt.com` for sign-in.
- Replaces hardcoded Snikket keychain/app group identifiers in text resources.
- Clears the upstream crash-reporting DSN.

It **does not** implement Telegram feature parity, production push delivery, all rebranding or a release IPA.

## GitHub build

Open the **Operatt iOS Simulator** workflow in Actions (on a PR or manually after merging). The workflow clones the pinned upstream source, applies `ios/prepare_upstream.py`, resolves the upstream dependencies, builds with Xcode on macOS, and uploads `operatt-ios-simulator.zip` if successful. This ZIP is for **Apple Simulator on a Mac only** — it **cannot be installed on an iPhone**.

## iPhone/TestFlight distribution prerequisites

A real-device IPA/TestFlight build requires:
1. An Apple Developer Program team and a new `com.operatt.messenger.ios` App ID.
2. Signing certificates and provisioning profiles for main app, share extension, notification service, and their app groups.
3. APNs and XMPP push gateway configuration tested end to end. Snikket's upstream push setup cannot simply be copied without provisioning.
4. Privacy/consent, rights and security review plus replacement of all remaining upstream trademarks, texts and support links.
5. Successful simulator and device smoke tests, plus App Store Connect access.

Keep the iOS source and derivative releases compliant with GPL-3.0 and retain required upstream copyright notices. **Never commit Apple signing private keys or certificates to this repository.**

The Android app is built by `.github/workflows/android.yml`. On Android, the local account removal action is destructive and requires backup; use the account manager to switch without deleting local history.

## Prioritized feature parity roadmap

- Phase 1: registration/login, safe account switching, chat/search UI, theme and icon identity, consistent XMPP server configuration.
- Phase 2: contact discovery, replies/edits/reactions, media, groups/channels, voice/video, robust archive sync, encrypted multi-device UX.
- Phase 3: user-generated stickers, packs and gallery, camera mirroring/filters, moderation/admin functionality, granular permissions, detailed QA.
- Phase 4: signed releases, public distribution, support, anti-spam controls, monitoring and backups.

Do not claim every Telegram feature is implemented by this initial milestone.
