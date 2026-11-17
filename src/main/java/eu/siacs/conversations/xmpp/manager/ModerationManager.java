package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import com.google.common.base.Strings;
import im.conversations.android.xmpp.model.hints.Store;
import java.util.UUID;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.moderation.Moderate;
import im.conversations.android.xmpp.model.retraction.Retract;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.Collection;
import java.util.List;

public class ModerationManager extends AbstractManager {

    private final XmppConnectionService service;

    public ModerationManager(final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public ListenableFuture<Void> moderate(final eu.siacs.conversations.entities.Message message) {
        final var serverMsgId = message.getServerMsgId();
        final var previous = message.getEditedServerMessageIds();
        if (!previous.isEmpty()) {
            Log.d(
                    Config.LOGTAG,
                    getAccount().getJid()
                            + ": requesting deletion of previous stanza-ids: "
                            + previous);
        }
        final var serverMsgIds =
                new ImmutableSet.Builder<String>().add(serverMsgId).addAll(previous).build();
        final var conversation = message.getConversation();
        final var address = conversation.getAddress().asBareJid();
        final var future = moderate(address, serverMsgIds);
        return Futures.transform(
                future,
                result -> {
                    if (message.getConversation() instanceof Conversation c) {
                        c.remove(message);
                        if (getDatabase().deleteMessage(message.getUuid())) {
                            Log.d(Config.LOGTAG, "deleted local copy of moderated message");
                        }
                        this.service.updateConversationUi();
                        return null;
                    } else {
                        throw new IllegalStateException("Message was not part of conversation");
                    }
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<List<Iq>> moderate(final Jid address, final Collection<String> ids) {
        final var futures =
                Collections2.transform(
                        ids,
                        id -> {
                            final var iq = new Iq(Iq.Type.SET);
                            iq.setTo(address);
                            final var moderate = iq.addExtension(new Moderate(id));
                            moderate.addExtension(new Retract());
                            return this.connection.sendIqPacket(iq);
                        });
        return Futures.allAsList(futures);
    }

    /** Request XEP-0424 message retraction from the other person's client.
     * It is best effort: remote clients and servers may keep a copy.
     */
    public boolean retractOwnMessage(final eu.siacs.conversations.entities.Message message) {
        if (!(message.getConversation() instanceof Conversation conversation)
                || conversation.getMode() != Conversation.MODE_SINGLE
                || message.getStatus() == eu.siacs.conversations.entities.Message.STATUS_RECEIVED
                || message.getStatus() == eu.siacs.conversations.entities.Message.STATUS_SEND_FAILED) {
            return false;
        }
        final String id = message.isCarbon() ? message.getRemoteMsgId() : message.getUuid();
        if (Strings.isNullOrEmpty(id) || conversation.getAddress() == null) {
            return false;
        }
        final var packet = new Message();
        packet.setType(Message.Type.CHAT);
        packet.setTo(conversation.getAddress().asBareJid());
        packet.setFrom(getAccount().getJid());
        packet.setId(UUID.randomUUID().toString());
        final var retract = packet.addExtension(new Retract());
        retract.setAttribute("id", id);
        packet.addExtension(new Store());
        this.connection.sendMessagePacket(packet);
        conversation.remove(message);
        this.service.getNotificationService().clear(message);
        this.getDatabase().deleteMessage(message.getUuid());
        this.service.updateConversationUi();
        return true;
    }

    /** Refuse retraction of another sender\'s messages in a private conversation. */
    public void handlePrivateRetraction(final Message packet, final Jid counterpart) {
        if (packet.getType() != Message.Type.CHAT || counterpart == null) return;
        final var retract = packet.getExtension(Retract.class);
        if (retract == null || Strings.isNullOrEmpty(retract.getId())) return;
        final var conversation = this.service.find(getAccount(), counterpart.asBareJid());
        if (conversation == null || conversation.getMode() != Conversation.MODE_SINGLE) return;
        final var inMemory = conversation.findMessageWithUuidOrRemoteId(retract.getId());
        final var target = inMemory == null
                ? this.getDatabase().getMessageWithUuidOrRemoteId(conversation, retract.getId())
                : inMemory;
        if (target == null) return;
        final boolean sentByUs = packet.fromAccount(getAccount());
        final boolean targetReceived = target.getStatus() == eu.siacs.conversations.entities.Message.STATUS_RECEIVED;
        if (sentByUs == targetReceived) {
            Log.w(Config.LOGTAG, "Rejected attempt to retract a message from another sender");
            return;
        }
        conversation.remove(target);
        this.service.getNotificationService().clear(target);
        this.getDatabase().deleteMessage(target.getUuid());
        this.service.updateConversationUi();
    }

    public void handleRetraction(final Message message) {
        final var account = getAccount();
        final var from = Jid.Invalid.getNullForInvalid(message.getFrom());
        if (from == null || from.isFullJid() || message.getType() != Message.Type.GROUPCHAT) {
            Log.d(
                    Config.LOGTAG,
                    "received retraction from "
                            + from
                            + " but retractions are only supported in MUC");
            return;
        }
        final var mucOptions = getManager(MultiUserChatManager.class).getState(from.asBareJid());
        if (mucOptions == null) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid() + ": received retraction in MUC w/o state");
            return;
        }
        if (mucOptions.isPrivateAndNonAnonymous()) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": retractions are only supported in public channels");
            return;
        }
        final var retraction = message.getExtension(Retract.class);
        final var stanzaId = retraction == null ? null : retraction.getId();
        if (stanzaId == null) {
            Log.d(Config.LOGTAG, "retraction was missing stanza-id");
            return;
        }
        final var conversation = mucOptions.getConversation();
        final var retractedMessage = conversation.findMessageWithServerMsgId(stanzaId);
        if (retractedMessage == null) {
            Log.d(Config.LOGTAG, "received retraction for " + stanzaId + ". Message not found.");
            return;
        }
        final var moderated = retraction.getModerated();
        final var by = moderated == null ? null : moderated.getBy();
        conversation.remove(retractedMessage);
        this.service.getNotificationService().clear(retractedMessage);
        if (getDatabase().deleteMessage(retractedMessage.getUuid())) {
            Log.d(
                    Config.LOGTAG,
                    "received retraction for " + stanzaId + " in " + from + " by " + by);
        }
        this.service.updateConversationUi();
    }
}
