# Operatt Messenger v5 — Atelier / Background-delivery release gate

## Scope and design contract
- **Base:** the existing v5 UI on `master`; preserve screen structure, five-tab navigation, IDs, actions and message routes.
- **Atelier system:** light and dark material theme palettes in `operatt_atelier_palette.xml`; accent is muted petroleum blue; warm-white surfaces, controlled outlines, typographic hierarchy. No UI rearchitecture.
- Components covered: chat list, chat composer, conversation bubbles, settings cards, profile, call screen, call journal, Stories, Sticker Studio and launcher monogram.
- No new external font packages or unnecessary render animations. System fonts keep render overhead and APK size down.

## Battery audit — what changed in code
1. `XmppConnectionService` already avoids `START_STICKY` for a service with no enabled account and no ongoing call; previous revisions also disabled inherited persistent foreground service preference and cancelled idle alarm after logout.
2. `UnifiedPushBroker` used to install a **recurring hourly ScheduledExecutorService callback unconditionally**, even when no distributor transport was selected. The callback now exists **only while an enabled account has explicitly configured a transport**, is cancelled when that transport is removed, and is cancelled on service destruction.
3. The active call/transcoding foreground service stays intact. The device OS can still maintain radio, socket, keepalives, reconnects and the XMPP worker while accounts are active.

These changes reduce *avoidable* background work, not all background XMPP activity. No measured percentage battery improvement is claimed.

## Critical unfinished work: actual low-power push and fast incoming calls
The default CI artifact is `conversationsFreeDebug`; `src/free/java/eu/siacs/conversations/services/PushManagementService.java` explicitly returns `available() = false`. There is **no functioning client push transport in this flavor**. The application's existing `UnifiedPushBroker` is a **distributor** for other apps, not proof that this app can receive push with its own XMPP socket suspended.

For production:
1. Confirm which XMPP domain, Prosody/Snikket instance, and supported XEP-0357 push service/gateway host are actually deployed. Keep credentials and tokens in protected secrets rather than the repository.
2. Connect a compatible Android **client-side** push transport (FCM for a permitted release flavor, or a properly implemented UnifiedPush receiver/subscriber), register device endpoints, register/enable XMPP push on the same server, and handle token renewal and revoke-on-logout.
3. Test message and **incoming call wake-up** with Android Doze/idle and different Wi-Fi/LTE networks. Preserve OS foreground service for active calls and audio route selection.
4. Only then consider lowering active-account idle socket keepalives. Doing so before a verified push transport can cause missed or delayed calls and messages.

Without server access, sender credentials, and running device registration, **server-side push cannot truthfully be marked configured**.

## Verification matrix — must pass before production label
- CI: Android debug build compiles, manifest/resources resolve, universal APK artifact exists.
- Design: light/dark modes; Persian RTL, Turkish, English; five-tab geometry and callback parity; dialogs, stickers, call journal, video camera preview.
- Idle: enable one account; record power by Android Battery usage or Android Battery Historian over a controlled baseline with screen off, Wi-Fi and LTE separately. Compare before/after on the same hardware/config.
- Service: sign out/disable all accounts and confirm renewal timer and idle ping alarms stop. Sign in again; ensure legitimate account rebind works.
- Reliability: two phones, one screen off 30–60 min; send messages and ring voice/video calls in both directions, with speaker, receiver and Bluetooth transitions and reconnection after network changes.
- Notifications: Android 13+ notification permission, call alert sound, screen-locked availability, no stealth family location.
- Security: confirm consent/revocation/notification for family location, chat encryption setting, and release signing identity.
- Distribution: replace debug signing with a stable private release keystore before general rollout; back up data and keys before replacing an APK signed by a different certificate.

**Build success is not equivalent to device QA or production readiness.**
