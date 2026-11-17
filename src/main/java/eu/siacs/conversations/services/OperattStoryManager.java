package eu.siacs.conversations.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xml.Element;
import im.conversations.android.xmpp.model.hints.Store;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A lightweight private-contact story protocol for Operatt-to-Operatt clients.
 * Stories are delivered to the chosen roster contacts as XMPP stanzas. Each
 * device caches story metadata locally. Uploaded media may outlive the story
 * on the HTTP host; privacy therefore depends on the unguessable upload link.
 */
public final class OperattStoryManager {
    public static final String NS = "urn:operatt:story:1";
    public static final long TTL_MS = 24L * 60L * 60L * 1000L;
    private static final int MAX_STORIES = 350;
    private static final String PREFS = "operatt_stories_v1";

    private OperattStoryManager() {}

    public static final class Story {
        public String id;
        public String account;
        public String owner;
        public String type; // text, image, video
        public String caption;
        public String mediaUrl;
        public String mime;
        public String audience; // comma separated bare JIDs (only stored by publisher)
        public String viewers; // comma separated bare JIDs (only stored by publisher)
        public long created;
        public boolean highlight;
        public boolean seen;

        public boolean isOwn() { return owner != null && owner.equals(account); }
        public boolean active() { return created > 0
                && created <= System.currentTimeMillis() + 60_000L
                && created + TTL_MS > System.currentTimeMillis(); }
        public int viewCount() {
            return viewers == null || viewers.isEmpty() ? 0 : viewers.split(",").length;
        }

        JSONObject toJson() throws JSONException {
            final JSONObject o = new JSONObject();
            o.put("id", id);o.put("account",account);o.put("owner",owner);
            o.put("type",type);o.put("caption",caption);o.put("media",mediaUrl);
            o.put("mime",mime);o.put("audience",audience);o.put("viewers",viewers);
            o.put("created",created);o.put("highlight",highlight);o.put("seen",seen);
            return o;
        }

        static Story fromJson(JSONObject o) {
            Story s = new Story();
            s.id=o.optString("id","");s.account=o.optString("account","");
            s.owner=o.optString("owner","");s.type=o.optString("type","text");
            s.caption=o.optString("caption","");s.mediaUrl=o.optString("media","");
            s.mime=o.optString("mime","");s.audience=o.optString("audience","");
            s.viewers=o.optString("viewers","");s.created=o.optLong("created",0L);
            s.highlight=o.optBoolean("highlight",false);s.seen=o.optBoolean("seen",false);
            return s;
        }
    }

    private static String key(Account a) {
        return "items|" + a.getJid().asBareJid();
    }

    public static synchronized List<Story> all(Context context, Account account) {
        final List<Story> list = new ArrayList<>();
        if (account == null) return list;
        final SharedPreferences prefs = context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        final String data = prefs.getString(key(account),"[]");
        try {
            final JSONArray arr = new JSONArray(data);
            for (int i=0;i<arr.length();i++) {
                Story s=Story.fromJson(arr.getJSONObject(i));
                if (!s.id.isEmpty() && !s.owner.isEmpty()) list.add(s);
            }
        } catch (JSONException e) {
            Log.w(Config.LOGTAG,"Could not read local story index",e);
        }
        list.sort((a,b)->Long.compare(b.created,a.created));
        return list;
    }

    private static synchronized void save(Context context,Account account,List<Story> items) {
        items.sort((a,b)->Long.compare(b.created,a.created));
        JSONArray arr=new JSONArray();
        int count=0;
        for(Story s:items) {
            // Remote expired stories are discarded, own items form local archive.
            if(!s.isOwn() && !s.active()) continue;
            if(count++>=MAX_STORIES) break;
            try { arr.put(s.toJson()); }
            catch(JSONException e){Log.w(Config.LOGTAG,"Cannot persist story",e);}
        }
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putString(key(account),arr.toString()).apply();
    }

    private static void upsert(Context ctx,Account a,Story item) {
        List<Story> all=all(ctx,a);
        Story prior=null;
        for(Story s:all)if(s.id.equals(item.id)&&s.owner.equals(item.owner)){prior=s;break;}
        if(prior!=null) {
            item.seen=prior.seen;
            item.highlight=prior.highlight;
            item.viewers=prior.viewers;
            all.remove(prior);
        }
        all.add(item);
        save(ctx,a,all);
    }

    @NonNull
    public static List<Story> active(Context context,Account a) {
        List<Story> results=new ArrayList<>();
        for(Story s:all(context,a))if(s.active())results.add(s);
        return results;
    }

    @NonNull
    public static List<Story> ownArchive(Context context,Account a) {
        List<Story> result=new ArrayList<>();
        for(Story s:all(context,a))if(s.isOwn()&&!s.active())result.add(s);
        return result;
    }

    @NonNull
    public static List<Story> highlights(Context context,Account a) {
        List<Story> result=new ArrayList<>();
        for(Story s:all(context,a))if(s.isOwn()&&s.highlight)result.add(s);
        return result;
    }

    public static synchronized void setHighlight(Context ctx, Account a, Story story, boolean value) {
        List<Story> items=all(ctx,a);
        for(Story s:items)if(s.isOwn()&&s.id.equals(story.id))s.highlight=value;
        save(ctx,a,items);
    }

    private static void send(Account account,Jid destination,String kind,Story story) {
        if(account.getXmppConnection()==null || destination==null) return;
        Message packet=new Message(Message.Type.CHAT);
        packet.setId(UUID.randomUUID().toString());
        packet.setTo(destination.asBareJid());
        Element item=packet.addChild(kind,NS);
        item.setAttribute("id",story.id);
        if("story".equals(kind)) {
            item.setAttribute("created",Long.toString(story.created));
            item.setAttribute("type",story.type);
            item.setAttribute("mime",story.mime==null?"":story.mime);
            item.setAttribute("url",story.mediaUrl==null?"":story.mediaUrl);
            item.setContent(story.caption==null?"":story.caption);
        }
        packet.addExtension(new Store());
        account.getXmppConnection().sendMessagePacket(packet);
    }

    public static boolean publish(Context ctx,Account account,String type,String caption,
                                  String url,String mime,List<Jid> recipients) {
        if(account==null || !account.isOnlineAndConnected() || recipients==null || recipients.isEmpty())return false;
        if(!"text".equals(type)&&!"image".equals(type)&&!"video".equals(type))return false;
        if(("image".equals(type)||"video".equals(type))
                && (url==null||!url.startsWith("https://")))return false;
        Story story=new Story();
        story.id=UUID.randomUUID().toString();
        story.account=account.getJid().asBareJid().toString();
        story.owner=story.account;
        story.type=type;story.caption=limit(caption,500);
        story.mediaUrl=url==null?"":url;
        story.mime=mime==null?"":mime;
        story.created=System.currentTimeMillis();
        Set<String> targets=new HashSet<>();
        for(Jid to:recipients){
            if(to==null||to.asBareJid().equals(account.getJid().asBareJid()))continue;
            final Contact contact=account.getRoster().getContact(to.asBareJid());
            if(contact==null||!contact.showInRoster()||contact.isBlocked())continue;
            targets.add(to.asBareJid().toString());
        }
        if(targets.isEmpty())return false;
        story.audience=String.join(",",targets);
        story.viewers="";
        upsert(ctx,account,story);
        for(String target:targets)send(account,Jid.of(target),"story",story);
        return true;
    }

    public static synchronized boolean editCaption(Context ctx,Account account,
                                                    Story story,String newCaption) {
        if(!story.isOwn()||!story.active()||account==null||!account.isOnlineAndConnected())return false;
        List<Story> list=all(ctx,account);
        for(Story s:list) {
            if(s.id.equals(story.id)&&s.isOwn()) {
                s.caption=limit(newCaption,500);
                for(String target:split(s.audience)) send(account,Jid.of(target),"story",s);
                save(ctx,account,list);
                return true;
            }
        }
        return false;
    }

    public static synchronized void delete(Context ctx,Account a,Story story) {
        if(story==null||!story.isOwn())return;
        List<Story> items=all(ctx,a);
        Story found=null;
        for(Story s:items)if(s.id.equals(story.id)&&s.isOwn()){found=s;break;}
        if(found==null)return;
        for(String to:split(found.audience))send(a,Jid.of(to),"story-delete",found);
        items.remove(found);save(ctx,a,items);
    }

    public static synchronized void markSeen(Context ctx, Account a,Story story) {
        if(story==null||story.isOwn()||story.seen)return;
        List<Story> all=all(ctx,a);
        for(Story s:all)if(s.id.equals(story.id)&&s.owner.equals(story.owner)){
            s.seen=true;break;
        }
        save(ctx,a,all);
        try {send(a,Jid.of(story.owner),"story-view",story);}
        catch(IllegalArgumentException ignored){}
    }

    public static void handleIncoming(Context ctx,Account account,Jid sender,Element element) {
        if(sender==null||element==null||account==null)return;
        Contact contact=account.getRoster().getContact(sender.asBareJid());
        if(contact==null||!contact.showInRoster()||contact.isBlocked())return;
        String kind=element.getName();
        String id=element.getAttribute("id");
        if(id==null||id.length()>80||id.isEmpty())return;
        String from=sender.asBareJid().toString();
        String own=account.getJid().asBareJid().toString();
        if("story".equals(kind)) {
            Story story=new Story();story.id=id;story.owner=from;story.account=own;
            story.type=element.getAttribute("type");
            story.mime=element.getAttribute("mime");
            story.mediaUrl=element.getAttribute("url");
            story.caption=limit(element.getContent(),500);
            try { story.created=Long.parseLong(element.getAttribute("created")); }
            catch(Exception e){return;}
            if(!story.active())return;
            if(!"text".equals(story.type)&&!"image".equals(story.type)
                    &&!"video".equals(story.type))return;
            if(!"text".equals(story.type)
                    &&(story.mediaUrl==null || story.mediaUrl.length()>2048
                        || !story.mediaUrl.startsWith("https://")))return;
            upsert(ctx,account,story);
        } else if("story-delete".equals(kind)) {
            List<Story> list=all(ctx,account);
            list.removeIf(s->s.owner.equals(from)&&s.id.equals(id));
            save(ctx,account,list);
        } else if("story-view".equals(kind)) {
            List<Story> list=all(ctx,account);
            for(Story s:list) {
                if(!s.isOwn()||!s.id.equals(id)||!split(s.audience).contains(from))continue;
                Set<String> viewers=new HashSet<>(split(s.viewers));
                viewers.add(from);
                s.viewers=String.join(",",viewers);
            }
            save(ctx,account,list);
        }
    }

    private static String limit(String value,int count) {
        if(value==null)return "";
        return value.length()>count?value.substring(0,count):value;
    }

    private static Set<String> split(String csv) {
        Set<String> set=new HashSet<>();
        if(csv==null||csv.isEmpty())return set;
        Collections.addAll(set,csv.split(","));
        set.remove("");
        return set;
    }
}
