# Background battery and incoming calls – Operatt Android

## Findings from the actual code
- `XmppConnectionService` keeps a foreground notification whenever the stored `enable_foreground_service` flag is true, or during an active call/transcoding.
- Previous installs can retain a true value from older versions even after newer builds default to false. This is why the app can continue displaying “Active in background”.
- In the ConversationsFree flavor, `PushManagementService` is currently a stub and reports `available=false`. Without a trusted push provider plus server registration, there is no Telegram/WhatsApp-grade notification delivery while fully suspended.
- An active RTP call must retain its foreground service, microphone and camera access and proper notification. Do **not** suppress it.

## Implemented
Version 7 migrates the old persistent-service setting to false **once** per install.
The existing switch in Connection & Battery remains available so users
who prioritize immediate delivery can re-enable it. Call and transcoding
foreground services remain unaffected.

## Not solved
- Guaranteed real-time delivery of incoming calls and messages with no
background connection and no configured push credentials. This requires
end-to-end push transport (FCM or installed UnifiedPush distributor),
XMPP push support on Snikket, token registration, and Samsung device QA.
- Measuring exact battery reduction. Battery consumption depends on
mobile radio, Wi-Fi, Android version, and the connected push transport.

## Release criteria
Test on two Android phones: contact each other when one phone has its
screen off for 30 minutes, with separate LTE/Wi-Fi networks. Confirm
message, ringing, speaker/Bluetooth switching, and reconnect after
network changes. Do not claim instant delivery without this test.
