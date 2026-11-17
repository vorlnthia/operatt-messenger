package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.OperattStoryManager;
import eu.siacs.conversations.ui.util.OperattHandle;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.manager.HttpUploadManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Version 5 compatible dedicated stories hub. No special runtime permissions:
 * photos/videos are selected through Android's document picker. Stories use
 * account-scoped, opt-in direct XMPP delivery; media uses server HTTP upload.
 */
public final class OperattStoriesActivity extends XmppActivity {
    private static final int PICK_MEDIA=7935;
    private static final long MAX_FILE_SIZE=40L*1024L*1024L;
    private static final int DARK=0xff132e40, MUTED=0xff647683,
            SURFACE=0xfff9fbfb, ACCENT=0xff0d5367;
    private static final String SELECTED_ACCOUNT="operatt_selected_account";
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final List<OperattStoryManager.Story> visible=new ArrayList<>();
    private ListView list;
    private ArrayAdapter<OperattStoryManager.Story> adapter;
    private TextView subtitle;
    private Button activeButton,archiveButton,highlightButton;
    private String currentTab="active";
    private String pendingType,pendingCaption;
    private List<Jid> pendingAudience;

    private int dp(float d){return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,d,getResources().getDisplayMetrics()));}

    private GradientDrawable surface(int color,int radius){
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;
    }

    private TextView label(String value,int size,int color,boolean bold){
        TextView text=new TextView(this);
        text.setText(value);text.setTextSize(size);text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return text;
    }

    private Button action(String value){
        Button b=new Button(this);
        b.setText(value);b.setTextSize(12);b.setAllCaps(false);
        b.setTextColor(DARK);b.setBackground(surface(0xffe6eff0,18));
        b.setMinHeight(0);b.setMinimumHeight(0);
        return b;
    }

    @Override public void onCreate(@Nullable Bundle state){
        super.onCreate(state);
        setTitle(R.string.operatt_story_title);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);
        root.setPadding(dp(18),dp(20),dp(18),dp(10));

        TextView title=label(getString(R.string.operatt_story_title),29,DARK,true);
        root.addView(title);
        subtitle=label(getString(R.string.operatt_story_subtitle),13,MUTED,false);
        subtitle.setPadding(0,dp(4),0,dp(16));
        root.addView(subtitle);

        LinearLayout composer=new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setPadding(dp(12),dp(12),dp(12),dp(12));
        composer.setBackground(surface(0xffeaf1f0,23));
        Button textStory=action(getString(R.string.operatt_story_text));
        Button photoStory=action(getString(R.string.operatt_story_photo));
        Button videoStory=action(getString(R.string.operatt_story_video));
        Button[] buttons={textStory,photoStory,videoStory};
        for(Button b:buttons){
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(47),1f);
            p.setMargins(dp(3),0,dp(3),0);
            composer.addView(b,p);
        }
        root.addView(composer);
        textStory.setOnClickListener(v->create("text"));
        photoStory.setOnClickListener(v->create("image"));
        videoStory.setOnClickListener(v->create("video"));

        LinearLayout segments=new LinearLayout(this);
        segments.setPadding(0,dp(20),0,dp(7));
        activeButton=action(getString(R.string.operatt_story_active));
        archiveButton=action(getString(R.string.operatt_story_archive));
        highlightButton=action(getString(R.string.operatt_story_highlights));
        for(Button b:new Button[]{activeButton,archiveButton,highlightButton}){
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(43),1f);
            p.setMargins(dp(3),0,dp(3),0);
            segments.addView(b,p);
        }
        root.addView(segments);
        activeButton.setOnClickListener(v->showTab("active"));
        archiveButton.setOnClickListener(v->showTab("archive"));
        highlightButton.setOnClickListener(v->showTab("highlights"));

        list=new ListView(this);
        list.setDividerHeight(0);
        list.setBackgroundColor(SURFACE);
        adapter=new ArrayAdapter<OperattStoryManager.Story>(
                this,android.R.layout.simple_list_item_1,visible) {
            @NonNull @Override public View getView(int position, View convertView,
                                                   @NonNull ViewGroup parent){
                final OperattStoryManager.Story s=getItem(position);
                LinearLayout row=new LinearLayout(OperattStoriesActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12),dp(13),dp(12),dp(13));
                row.setBackground(surface(0xffffffff,18));
                TextView initial=label(s.isOwn()?"O":s.owner.substring(0,1).toUpperCase(Locale.ROOT),
                        20,0xffffffff,true);
                initial.setGravity(Gravity.CENTER);
                initial.setBackground(surface(s.seen?0xff879da7:ACCENT,35));
                LinearLayout.LayoutParams avatar=new LinearLayout.LayoutParams(dp(47),dp(47));
                row.addView(initial,avatar);
                LinearLayout texts=new LinearLayout(OperattStoriesActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(dp(12),0,0,0);
                String name=s.isOwn()?getString(R.string.operatt_story_mine):OperattHandle.display(s.owner);
                TextView main=label(name,16,DARK,true);
                texts.addView(main);
                String description=(!s.caption.isBlank()?s.caption:(s.type.equals("image")?
                        "Photo story":s.type.equals("video")?"Video story":"Text status"));
                if(description.length()>70)description=description.substring(0,70)+"…";
                TextView detail=label(description,13,MUTED,false);
                detail.setSingleLine(true);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
                texts.addView(detail);
                CharSequence time=DateUtils.getRelativeTimeSpanString(s.created,
                        System.currentTimeMillis(),DateUtils.MINUTE_IN_MILLIS);
                TextView foot=label(time+(s.isOwn()?" · "+s.viewCount()+" views":""),11,MUTED,false);
                texts.addView(foot);
                row.addView(texts,new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,1f));
                LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
                outer.setMargins(0,dp(5),0,dp(5));
                LinearLayout wrap=new LinearLayout(OperattStoriesActivity.this);
                wrap.addView(row,outer);return wrap;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent,view,position,id)->openStory(visible.get(position)));
        root.addView(list,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        setContentView(root);
        showTab("active");
    }

    @Override protected void onBackendConnected(){reload();}
    @Override protected void refreshUiReal(){reload();}
    @Override protected void onResume(){super.onResume();reload();}
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}

    private Account account(){
        if(xmppConnectionService==null||xmppConnectionService.getAccounts().isEmpty())return null;
        final String chosen=PreferenceManager.getDefaultSharedPreferences(this)
                .getString(SELECTED_ACCOUNT,null);
        if(chosen!=null)for(Account a:xmppConnectionService.getAccounts())
            if(a.getJid().asBareJid().toString().equals(chosen))return a;
        return xmppConnectionService.getAccounts().get(0);
    }

    private void showTab(String next){currentTab=next;reload();}
    private void reload(){
        if(list==null||adapter==null)return;
        Account a=account();
        visible.clear();
        if(a!=null) {
            switch(currentTab){
                case "archive"->visible.addAll(OperattStoryManager.ownArchive(this,a));
                case "highlights"->visible.addAll(OperattStoryManager.highlights(this,a));
                default->visible.addAll(OperattStoryManager.active(this,a));
            }
            subtitle.setText(OperattHandle.display(a.getJid().asBareJid().toString())
                    +" · "+getString(R.string.operatt_story_subtitle));
        } else subtitle.setText(R.string.operatt_story_sign_in);
        adapter.notifyDataSetChanged();
        activeButton.setBackground(surface("active".equals(currentTab)?0xffc4dfe1:0xffe6eff0,18));
        archiveButton.setBackground(surface("archive".equals(currentTab)?0xffc4dfe1:0xffe6eff0,18));
        highlightButton.setBackground(surface("highlights".equals(currentTab)?0xffc4dfe1:0xffe6eff0,18));
    }

    private void create(String type){
        Account a=account();
        if(a==null||!a.isOnlineAndConnected()){
            Toast.makeText(this,R.string.operatt_story_sign_in,Toast.LENGTH_LONG).show();return;
        }
        EditText editor=new EditText(this);
        editor.setSingleLine(false);editor.setMinLines(2);
        editor.setMaxLines(5);editor.setInputType(
                InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setHint(R.string.operatt_story_caption);
        LinearLayout wrapper=new LinearLayout(this);
        wrapper.setPadding(dp(20),0,dp(20),0);
        wrapper.addView(editor,new LinearLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(this).setTitle(R.string.operatt_story_new)
            .setView(wrapper).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.continue_btn,(dialog,which)->
                    chooseAudience(type,editor.getText().toString())).show();
    }

    private void chooseAudience(String type,String caption){
        final Account a=account();if(a==null)return;
        final List<Contact> contacts=new ArrayList<>();
        for(Contact c:a.getRoster().getContacts())
            if(c.showInRoster()&&!c.isBlocked()&&!c.isSelf())contacts.add(c);
        if(contacts.isEmpty()){
            Toast.makeText(this,R.string.operatt_story_no_contacts,Toast.LENGTH_LONG).show();return;
        }
        final String[] labels=new String[contacts.size()];
        final boolean[] selection=new boolean[contacts.size()];
        for(int i=0;i<contacts.size();i++){
            labels[i]=contacts.get(i).getDisplayName();
            selection[i]=true;
        }
        new AlertDialog.Builder(this).setTitle(R.string.operatt_story_privacy)
            .setMultiChoiceItems(labels,selection,(dialog,which,checked)->selection[which]=checked)
            .setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.confirm,(dialog,which)->{
                final List<Jid> recipients=new ArrayList<>();
                for(int i=0;i<contacts.size();i++)if(selection[i])
                    recipients.add(contacts.get(i).getAddress().asBareJid());
                if(recipients.isEmpty()){
                    Toast.makeText(this,R.string.operatt_story_no_contacts,Toast.LENGTH_LONG).show();return;
                }
                if("text".equals(type)){
                    if(OperattStoryManager.publish(this,a,type,caption,"","",recipients))reload();
                    else Toast.makeText(this,R.string.operatt_story_failed,Toast.LENGTH_LONG).show();
                }else{
                    pendingType=type;pendingCaption=caption;pendingAudience=recipients;
                    final Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image".equals(type)?"image/*":"video/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent,PICK_MEDIA);
                }
            }).show();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,@Nullable Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_MEDIA||resultCode!=Activity.RESULT_OK||data==null
                ||data.getData()==null||pendingAudience==null)return;
        final Uri uri=data.getData();
        final String type=pendingType,caption=pendingCaption;
        final List<Jid> audience=new ArrayList<>(pendingAudience);
        pendingAudience=null;pendingType=null;pendingCaption=null;
        final Account a=account();if(a==null)return;
        final String mime=getContentResolver().getType(uri);
        if(mime==null || !(("image".equals(type)&&mime.startsWith("image/"))
                ||("video".equals(type)&&mime.startsWith("video/")))){
            Toast.makeText(this,R.string.operatt_story_invalid_media,Toast.LENGTH_LONG).show();return;
        }
        Toast.makeText(this,R.string.operatt_story_uploading,Toast.LENGTH_LONG).show();
        executor.execute(()->{
            File file=null;
            try {
                String extension="image".equals(type)?".jpg":".mp4";
                file=File.createTempFile("operatt-story-"+UUID.randomUUID(),extension,getCacheDir());
                long copied=0;
                try(InputStream in=getContentResolver().openInputStream(uri);
                    FileOutputStream out=new FileOutputStream(file)){
                    if(in==null)throw new IllegalStateException("Media is unavailable");
                    byte[] buffer=new byte[8192];int n;
                    while((n=in.read(buffer))!=-1){
                        copied+=n;
                        if(copied>MAX_FILE_SIZE)throw new IllegalStateException("Media exceeds 40 MB");
                        out.write(buffer,0,n);
                    }
                }
                if(copied==0)throw new IllegalStateException("Empty media");
                if(a.getXmppConnection()==null
                        ||!a.getXmppConnection().getManager(HttpUploadManager.class)
                                    .isAvailableForSize(copied)){
                    throw new IllegalStateException("Server media upload is not available");
                }
                final File uploadFile=file;
                Futures.addCallback(a.getXmppConnection()
                                .getManager(HttpUploadManager.class).upload(file,mime,null),
                    new FutureCallback<okhttp3.HttpUrl>(){
                        @Override public void onSuccess(@Nullable okhttp3.HttpUrl remote){
                            uploadFile.delete();
                            runOnUiThread(()->{
                                if(remote==null||!OperattStoryManager.publish(
                                        OperattStoriesActivity.this,a,type,caption,
                                        remote.toString(),mime,audience)){
                                    Toast.makeText(OperattStoriesActivity.this,
                                            R.string.operatt_story_failed,Toast.LENGTH_LONG).show();
                                }else{reload();Toast.makeText(OperattStoriesActivity.this,
                                        R.string.operatt_story_published,Toast.LENGTH_SHORT).show();}
                            });
                        }
                        @Override public void onFailure(@NonNull Throwable error){
                            uploadFile.delete();
                            runOnUiThread(()->Toast.makeText(OperattStoriesActivity.this,
                                    R.string.operatt_story_failed,Toast.LENGTH_LONG).show());
                        }
                    },ContextCompat.getMainExecutor(this));
            }catch(Exception e){
                if(file!=null)file.delete();
                runOnUiThread(()->Toast.makeText(this,R.string.operatt_story_invalid_media,
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void openStory(OperattStoryManager.Story story){
        final Account a=account();if(a==null)return;
        if(!story.isOwn())OperattStoryManager.markSeen(this,a,story);
        final LinearLayout container=new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(14),dp(10),dp(14),dp(8));
        container.setBackgroundColor(SURFACE);
        if(!"text".equals(story.type)&&story.mediaUrl!=null&&!story.mediaUrl.isBlank()){
            if("image".equals(story.type)){
                final ImageView image=new ImageView(this);
                image.setAdjustViewBounds(true);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                container.addView(image,new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,dp(320)));
                executor.execute(()->{
                    try(InputStream stream=new URL(story.mediaUrl).openStream()){
                        final BitmapFactory.Options opts=new BitmapFactory.Options();
                        opts.inSampleSize=2;
                        final android.graphics.Bitmap bitmap=BitmapFactory.decodeStream(stream,null,opts);
                        runOnUiThread(()->image.setImageBitmap(bitmap));
                    }catch(Exception ignored){}
                });
            }else if("video".equals(story.type)){
                VideoView video=new VideoView(this);
                video.setVideoURI(Uri.parse(story.mediaUrl));
                MediaController controls=new MediaController(this);
                controls.setAnchorView(video);video.setMediaController(controls);
                container.addView(video,new LinearLayout.LayoutParams(-1,dp(320)));
                video.setOnPreparedListener(mp->video.start());
            }
        }
        TextView caption=label(story.caption==null?"":story.caption,18,DARK,true);
        caption.setPadding(dp(10),dp(18),dp(10),dp(20));
        container.addView(caption);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(story.isOwn()?R.string.operatt_story_mine:R.string.operatt_story_title)
                .setView(container)
                .setNegativeButton(R.string.close,null)
                .setPositiveButton(story.isOwn()?R.string.operatt_story_manage
                        :R.string.operatt_story_reply,(d,which)->{
                    if(story.isOwn())manage(story);else reply(story);
                }).create();
        dialog.show();
        reload();
    }

    private void manage(OperattStoryManager.Story story){
        Account a=account();if(a==null)return;
        final String[] options={
                getString(R.string.operatt_story_edit),
                story.highlight?getString(R.string.operatt_story_unhighlight)
                        :getString(R.string.operatt_story_add_highlight),
                getString(R.string.operatt_story_viewers,story.viewCount()),
                getString(R.string.delete)};
        new AlertDialog.Builder(this).setTitle(R.string.operatt_story_manage)
            .setItems(options,(d,which)->{
                if(which==0){
                    EditText edit=new EditText(this);edit.setText(story.caption);
                    new AlertDialog.Builder(this).setTitle(R.string.operatt_story_edit)
                        .setView(edit).setNegativeButton(android.R.string.cancel,null)
                        .setPositiveButton(R.string.confirm,(di,w)->{
                            if(!OperattStoryManager.editCaption(this,a,story,
                                    edit.getText().toString()))
                                Toast.makeText(this,R.string.operatt_story_failed,Toast.LENGTH_SHORT).show();
                            reload();
                        }).show();
                }else if(which==1){
                    OperattStoryManager.setHighlight(this,a,story,!story.highlight);reload();
                }else if(which==2){
                    new AlertDialog.Builder(this).setTitle(options[2])
                        .setMessage(story.viewers==null||story.viewers.isBlank()
                                ?getString(R.string.operatt_story_no_views)
                                :story.viewers.replace(",", "\n"))
                        .setPositiveButton(android.R.string.ok,null).show();
                }else{
                    new AlertDialog.Builder(this).setTitle(R.string.delete)
                        .setMessage(R.string.operatt_story_delete_confirm)
                        .setNegativeButton(android.R.string.cancel,null)
                        .setPositiveButton(R.string.delete,(di,w)->{
                            OperattStoryManager.delete(this,a,story);reload();
                        }).show();
                }
            }).show();
    }

    private void reply(OperattStoryManager.Story story){
        EditText editor=new EditText(this);editor.setSingleLine(false);
        editor.setHint(R.string.operatt_story_reply_hint);
        new AlertDialog.Builder(this).setTitle(R.string.operatt_story_reply)
            .setView(editor).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.send,(d,which)->{
                Account a=account();if(a==null||xmppConnectionService==null)return;
                String response=editor.getText().toString().trim();
                if(response.isEmpty())return;
                try{
                    Conversation c=xmppConnectionService.findOrCreateConversation(
                            a,Jid.of(story.owner),false,false);
                    Message msg=new Message(c,
                            "Story reply · "+(story.caption==null?"":story.caption)+"\n"+response,
                            c.getNextEncryption());
                    xmppConnectionService.sendMessage(msg);
                    Toast.makeText(this,R.string.operatt_story_reply_sent,Toast.LENGTH_SHORT).show();
                }catch(Exception e){
                    Toast.makeText(this,R.string.operatt_story_failed,Toast.LENGTH_LONG).show();
                }
            }).show();
    }
}
