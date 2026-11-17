package eu.siacs.conversations.ui.util;

import eu.siacs.conversations.Config;

/** Short, human-readable handles for the Operatt home server.
 * The XMPP JID remains unchanged for routing and account authentication.
 */
public final class OperattHandle {
    private OperattHandle() {}

    public static String display(final String jid) {
        if (jid == null) return "";
        final String bare = jid.split("/", 2)[0];
        final int at = bare.indexOf('@');
        if (at > 0 && bare.substring(at + 1).equalsIgnoreCase(Config.MAGIC_CREATE_DOMAIN)) {
            return "@" + bare.substring(0, at);
        }
        return bare;
    }

    public static String resolve(final String raw) {
        if (raw == null) return "";
        String input = raw.trim();
        if (input.startsWith("@")) input = input.substring(1);
        if (!input.contains("@") && input.matches("[A-Za-z0-9._-]{1,64}")) {
            return input + "@" + Config.MAGIC_CREATE_DOMAIN;
        }
        return input;
    }
}
