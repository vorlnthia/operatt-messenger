# Signup access code and location privacy — Operatt Android

The new Android registration screen checks a user-provided fixed access code
before creating an account. The literal code is not in source; its SHA-256 digest
is used for an exact UI check. A supplied server invitation token, if present,
is passed to the XMPP server separately and is **not** the fixed code.

**Security limitation:** This is a client-side gate only. Anybody can modify the
client or connect directly with another XMPP client. It cannot block bots
from registering on a server whose registration endpoint remains open.
This requirement can only be enforced securely by the Snikket/Prosody server
using invitation-only registration, preferably expiring individual invite links,
CAPTCHA, and per-IP rate limiting. Do not advertise the fixed code as
server-side anti-abuse protection. The server configuration has not been changed.

The existing Share Location UI shows the account owner's location (only after
OS permission) and offers a Copy button for latitude and longitude. Location
messages are user-initiated; nobody can remotely obtain another person's
location without that person's knowledge and active sharing.

## Other UI changes

Home tabs: Chats, Contacts, Settings, Profile. Profile and setting actions open
their corresponding existing native editors. The chat attachment toolbar opens
a bottom sheet with seven upload actions. These visual conventions are original
Operatt UI inspired by a familiar messaging experience; they do not embed
Telegram logos or assets. Posts, stories, cloud storage features and Telegram
protocol integration are still not provided.

Build workflow uploads an APK named `operatt-universal-android` (debug signature).
Do not uninstall an earlier copy with unbacked-up chats or files.
