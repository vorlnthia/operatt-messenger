# Operatt Messenger – feature status (17 Nov 2026)

The Android build is an evolving fork of Conversations/Snikket. It is **not** feature-identical to Telegram.

## Implemented in this branch
- Account-specific chat list filter and shortcuts to existing multi-account management
- Photo-to-PNG sticker maker with manual flood-fill background erase and local sticker library. Sends PNG as an image/file attachment; **not** XMPP native sticker packs, background removal is not AI segmentation
- Private one-to-one XEP-0424 retraction request for own messages, with sender validation on incoming retractions. Best effort; cannot guarantee removal from all clients, caches or backups
- New-chat encryption defaults to unencrypted server-readable mode, existing chats respect their saved encryption selection. OMEMO can be re-enabled in Security settings or per chat. Server MAM archiving must be set to Always for each account to sync across devices; open Profile > server archiving preferences
- Weekly encrypted SQLite/account backup scheduled via WorkManager on installs that have not chosen another cadence, using battery and storage constraints. This **does not include full media files** and **does not delete server copies**
- Standalone universal Android APK generated for Android API 23+ by CI; APK signing still debug unless private release signing secrets are provided

## Not complete
- WhatsApp/Telegram-grade push and battery usage: reliable instant background delivery needs a configured push transport/gateway, matching client registrations, and live device testing. Disabling the foreground service can delay notifications and incoming calls
- Stories, video story editor, server-side expirations, native sticker packs, per-device storage/media backup and cross-device drafts are not implemented
- Full Telegram UI parity is not achieved; navigation and palette are incremental
- Multiple accounts are stored with their distinct account identifiers; this is a UI filter, not complete process isolation
- A release signing certificate stored in GitHub Actions secrets is required for non-destructive update installs
- Deleting stored history from the server following a phone backup is unsafe for cross-device sync and is intentionally not enabled

## Security note
Server-readable messages are **not end-to-end encrypted**. Server operators or anyone with access to the archive may read message content. Never advertise these chats as end-to-end encrypted.
