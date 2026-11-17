package eu.siacs.conversations.services;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xml.Element;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.Locale;
import java.util.UUID;

/**
 * Consent-based, on-demand family location sharing between Operatt clients.
 * Each account explicitly approves individual bare JIDs in Contact Details.
 *
 * This protocol never turns on continuous tracking or requests location in the
 * background. It can only return a location fix the OS already has, younger
 * than MAX_FIX_AGE_MS. An absent/stale fix is NOT reported as current position.
 */
public final class FamilyLocationManager {
    public static final String NAMESPACE = "urn:operatt:family-location:1";
    private static final String PREFS = "operatt_family_location";
    private static final long MAX_FIX_AGE_MS = 10L * 60L * 1000L;
    private static final long MIN_REQUEST_INTERVAL_MS = 60L * 1000L;

    private FamilyLocationManager() {}

    private static String key(final Account account, final Jid contact) {
        return account.getJid().asBareJid() + "|" + contact.asBareJid();
    }

    public static boolean isAuthorized(final Context context, final Account account,
                                       final Jid contact) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("allow|" + key(account, contact), false);
    }

    public static void setAuthorized(final Context context, final Account account,
                                     final Jid contact, final boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("allow|" + key(account, contact), enabled).apply();
    }

    public static boolean hasLocationPermission(final Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
    }

    public static void request(final Account account, final Jid contact) {
        if (account.getXmppConnection() == null || contact == null) return;
        final Message message = new Message(Message.Type.CHAT);
        message.setTo(contact.asBareJid());
        message.setId(UUID.randomUUID().toString());
        message.addChild("location-request", NAMESPACE)
                .setAttribute("id", UUID.randomUUID().toString());
        account.getXmppConnection().sendMessagePacket(message);
    }

    /** Called only for a live, validated, non-MUC XMPP message from a peer. */
    public static void handleRequest(final XmppConnectionService service, final Account account,
                                     final Jid sender, final Element request) {
        if (sender == null || request == null || request.getAttribute("id") == null) return;
        if (!isAuthorized(service, account, sender) || !hasLocationPermission(service)) return;
        final SharedPreferences preferences = service.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final String throttleKey = "sent|" + key(account, sender);
        final long now = System.currentTimeMillis();
        // Enforce a minimum request interval, even across process restarts.
        final long previous = preferences.getLong(throttleKey, 0L);
        if (previous > 0 && now >= previous && now - previous < MIN_REQUEST_INTERVAL_MS) return;
        preferences.edit().putLong(throttleKey, now).apply();

        final LocationManager manager =
                (LocationManager) service.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) return;
        Location best = null;
        try {
            for (final String provider : manager.getProviders(true)) {
                final Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null &&
                        (best == null || candidate.getElapsedRealtimeNanos() >
                            best.getElapsedRealtimeNanos())) {
                    best = candidate;
                }
            }
        } catch (final SecurityException ignored) {
            return;
        }
        if (best == null) return;
        final long ageMs = (SystemClock.elapsedRealtimeNanos()
                - best.getElapsedRealtimeNanos()) / 1_000_000L;
        if (ageMs < 0 || ageMs > MAX_FIX_AGE_MS) return;

        final Message reply = new Message(Message.Type.CHAT);
        reply.setTo(sender.asBareJid());
        reply.setId(UUID.randomUUID().toString());
        reply.setBody(String.format(Locale.US, "geo:%.6f,%.6f",
                best.getLatitude(), best.getLongitude()));
        if (account.getXmppConnection() != null) {
            account.getXmppConnection().sendMessagePacket(reply);
        }
    }
}
