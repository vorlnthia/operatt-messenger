package eu.siacs.conversations.services;

import android.content.Context;
import android.content.SharedPreferences;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import java.util.LinkedHashSet;
import java.util.Set;

/** Local-only hide markers prevent MAM re-sync from immediately resurrecting
 * deliberately deleted messages. Markers belong to the device/account/chat.
 * Copies on other devices and the server are not deleted by this operation.
 */
public final class OperattLocalDelete {
    private static final String PREFS="operatt_hidden_messages_v1";
    private OperattLocalDelete(){}

    private static String key(Account a,Conversation c) {
        return a.getJid().asBareJid()+"|"+c.getUuid();
    }
    private static String normalized(String id) {
        return id==null || id.isBlank()?null:id;
    }

    public static synchronized void remember(Context ctx,Message message) {
        if(!(message.getConversation() instanceof Conversation c))return;
        SharedPreferences preferences=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String key=key(c.getAccount(),c);
        Set<String> ids=new LinkedHashSet<>(preferences.getStringSet(key,java.util.Collections.emptySet()));
        for(String id:new String[]{message.getUuid(),message.getRemoteMsgId(),
                message.getServerMsgId()})if(normalized(id)!=null)ids.add(id);
        // Bound storage so hidden message markers don't grow without limit.
        while(ids.size()>2000)ids.remove(ids.iterator().next());
        preferences.edit().putStringSet(key,ids).apply();
    }

    public static synchronized boolean hidden(Context ctx, Account account,
                                               Conversation conversation,Message message) {
        if(message==null||conversation==null)return false;
        Set<String> ids=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getStringSet(key(account,conversation),Set.of());
        for(String id:new String[]{message.getUuid(),message.getRemoteMsgId(),
                message.getServerMsgId()})if(normalized(id)!=null&&ids.contains(id))return true;
        return false;
    }
}
