package eu.siacs.conversations.services;

import android.content.Context;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.Jid;

/**
 * Explicit per-contact authorization for a chat partner to retract messages
 * that WE authored. Defaults to false to avoid unilateral remote deletion.
 */
public final class PeerDeletePolicy {
    private PeerDeletePolicy() {}

    private static String preferenceKey(final Account account, final Jid other) {
        return "allow_mutual_delete|" + account.getJid().asBareJid() + "|" + other.asBareJid();
    }

    public static boolean isAllowed(final Context context,
                                    final Account account, final Jid other) {
        if (account == null || other == null) return false;
        return context.getSharedPreferences("operatt_chat_privacy", Context.MODE_PRIVATE)
                .getBoolean(preferenceKey(account, other), false);
    }

    public static void setAllowed(final Context context, final Account account,
                                  final Jid other, final boolean allowed) {
        if (account == null || other == null) return;
        context.getSharedPreferences("operatt_chat_privacy", Context.MODE_PRIVATE)
                .edit().putBoolean(preferenceKey(account, other), allowed).apply();
    }
}
