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

## Android usability rebuild – 17 November 2026, v6
- Rebuilt `fragment_conversation.xml` composer rather than stacking additional controls on the original widget: emoji/sticker tray, typing field, adjacent attachment button, and dynamic blue voice/send control
- One-tap access to saved local stickers inside a chat, with direct attachment sending; the existing Sticker Studio still creates PNG assets
- Bottom sheet attachment picker now includes **Request location**, which asks the other person to share deliberately. It does **not** remotely fetch or bypass location consent. Own-location sharing remains separate
- The home header reports the selected account's real XMPP connection status and chat headers show Connected/Connecting/Not connected
- Contacts and account selectors display `@username` for the native Operatt domain, while XMPP routing retains the complete JID. The add-contact dialog accepts shorthand and resolves it to the same server
- Main three-dot menu provides night mode, new group and saved messages, with shortcuts kept elsewhere in the four-tab navigation
- Updated modern spacing, typography, bubbles and floating navigation; an original Operatt design, not a copied Telegram skin

Not implemented: contact location retrieval without recipient permission, complete Telegram protocol/feature parity, push-backed idle battery optimization, a native stories platform, permanent server-side access-code enforcement, and release-key signing. The current fixed signup code is only a client-side check; secure anti-bot protection must be applied on the server.

## Version 5 UI preservation and family controls (17 November 2026)
- This branch is based on the pre-existing `operatt-onboarding` line, including the version 5 visual redesign and its four-tab home UI. It deliberately does not import the outdated master-branch home layout.
- The account-scoped call log compilation error is fixed.
- Family location checks are implemented as explicit opt-in per individual contact. Approved family can request a recent device fix (at most 10 minutes old) without repeated prompts. Permission is visible and revocable under the contact detail menu, and failed or stale requests do not fabricate coordinates. This is not guaranteed live/background tracking.
- The location response follows the selected conversation's normal encryption mode: OMEMO protects coordinates when the user enabled OMEMO; unencrypted conversations are **not** end-to-end encrypted.
- Optional per-contact mutual deletion lets a peer retract messages they originally sent as well as, only with author-side consent, messages authored by the other participant. Retractions are best-effort across XMPP clients; archives and screenshots can retain copies.
- Voice/video audio output selection, video preview switching, low-power preference migration and the luxury-style call log already existed in the preserved `operatt-onboarding` line.
- Stories and their media editor, exact last-seen across offline clients, push-backed guaranteed call delivery, and full battery profiling remain incomplete.
- The build is a debug-signed release candidate for testing. Successful CI compilation is not evidence of two-device usability testing or production-signing readiness.

## Version 5 Stories + family refinement (17 November 2026)

Build SHA: dddd9cc3b6594be1cedb3a0aed171bc25652b287 (CI run 44144095182, Android debug build succeeded).

Implemented:
- A dedicated Stories page on v5's bottom navigation. Text/photo/video stories are sent to individually selected roster contacts using proprietary Operatt XMPP stanzas. Media uses the configured XMPP server's HTTP upload capability, subject to server availability and upload limits. Stories expire after 24 hours in the client feed; owners retain a device-local archive and can highlight entries.
- View acknowledgments from Operatt clients, reply in a normal chat, editable caption on live own stories, and removal propagation to Operatt recipients.
- Incoming story payloads are authenticated by XMPP peer routing and checked against the recipient's roster; unknown/blocked senders are rejected.
- Per-message “Delete for me” with device-local hidden-message identifiers to prevent ordinary MAM re-download of the same IDs; “Delete for everyone” remains best effort, and deleting another author's messages requires their author-side opt-in.
- The one-to-one chat header distinguishes our connection state from the contact's actual presence. Online and typing labels require real protocol evidence; last seen is shown only where available and permitted.
- Family location approvals remain individual, revocable, and reciprocal when both people opt in. Android notification activity is posted upon approval/change and upon an authorized location check (subject to the operating system's notification permission and user settings).
- An idle service with no enabled accounts no longer requests sticky restart or repeated idle wakeups. The active-call foreground service remains in place.

Limitations:
- This is an Android debug-signed build, *not* a production-signed release and not a verified two-device test.
- Media URLs can remain retrievable from an HTTP media server after story expiry/removal; stories are not end-to-end encrypted just because direct chats may use OMEMO.
- Story text overlay is caption-level. Video trimming, advanced stickers/filters, cloud story archives, server-enforced media expiry, and full Telegram/WhatsApp feature parity are not implemented.
- Near-instant delivery with the app completely suspended, low idle battery draw, and reliably updated family locations still require verified XMPP push/FCM/UnifiedPush infrastructure and handset validation. The current family request responds only with an OS location fix newer than ten minutes, if available.
- Contact last-seen cannot be exact when the other account hides or does not publish that information.
