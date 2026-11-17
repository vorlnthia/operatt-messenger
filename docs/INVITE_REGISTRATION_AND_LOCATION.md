# Invite-protected registration and consent-based location

## Important: there is no client-side shared registration secret

The Android client provides an invitation-token field in manual account creation.
It passes the token to Prosody through the existing pre-authenticated registration
mechanism. Snikket/Prosody must enforce that token on the server for **all** clients,
including XMPP apps and the web portal.

A fixed shared passphrase hardcoded into a published APK can be extracted and
replayed by bots; a visual password check in the Android screen alone is not
security. The operator-supplied shared secret is **not stored in this repo**.

### Required operations on the Snikket host

1. Before changing settings, back up the current configuration.
2. Inspect which effective Prosody configuration controls `allow_registration`
   and `registration_invite_only`, including anything managed or overwritten
   by Docker. On the VPS (SSH, not local Windows Docker):

   ```sh
   docker exec snikket sh -c 'grep -RnsE "^(allow_registration|registration_invite_only)[[:space:]]*=" /etc/prosody /snikket 2>/dev/null | head -30'
   ```

3. Set the **effective persistent** configuration to:

   ```lua
   allow_registration = true
   registration_invite_only = true
   ```

   The location of persistent configuration depends on the actual deployment,
   so do not blindly edit a container-generated file. Only reload/recreate
   the Snikket container according to its deployment instructions after
   verifying the file and backing it up. Keep the other services untouched.

4. Generate a server-issued, expiring invitation for each new user:

   ```sh
   docker exec snikket create-invite --group default
   ```

   Inspect the command's help and configured groups if necessary. Users can
   open its invitation URL; alternatively supply the raw pre-authentication
   token in the Android signup form.

5. **Test from an independent XMPP client** that registration without a
   valid token fails and registration with an unused token succeeds.
   Rate-limiting/CAPTCHA and anti-abuse monitoring are also useful.

Official documentation:
- https://prosody.dev/doc/modules/mod_invites_register
- https://snikket.org/service/help/setup/quickstart/

## Location disclosure

The app's Share Location screen now shows the current device's coordinates
(if Android grants location permission) and offers a Copy action. A
user-initiated Share action sends a location message to the chosen chat.
The app never silently uploads another user's coordinates. Receiving the
location message does not require the recipient's permission, but obtaining
the sender's coordinates does require the sender's choice and OS location permission.

## Build

Changes are on branch `operatt-onboarding`. The standalone
`operatt-universal-android` APK is uploaded by the Android workflow.
Do not ship before testing registration against an invite-only server.
