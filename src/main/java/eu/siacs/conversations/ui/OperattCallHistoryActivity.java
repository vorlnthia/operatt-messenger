package eu.siacs.conversations.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.ImmutableSet;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.RtpSessionStatus;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.CallIntegrationConnectionService;
import eu.siacs.conversations.xmpp.jingle.Media;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only account-scoped list of locally archived RTP calls. */
public final class OperattCallHistoryActivity extends XmppActivity {
    private record Entry(String uuid, String name, long when, boolean incoming,
                         boolean successful, long duration) {}
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Entry> all = new ArrayList<>(), visible = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView empty;
    private int filter = 0;
    private int ink, muted, backdrop, accent, surface, outline, error, chip, selectedChip;
    private final List<MaterialButton> filterButtons = new ArrayList<>();

    private GradientDrawable rounded(final int fill, final int radius) {
        final GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), outline);
        return shape;
    }
    private void loadColors() {
        final boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        ink = getColor(night ? R.color.operatt_dark_onSurface : R.color.operatt_light_onSurface);
        muted = getColor(night ? R.color.operatt_dark_onSurfaceVariant
                : R.color.operatt_light_onSurfaceVariant);
        backdrop = getColor(night ? R.color.operatt_dark_background
                : R.color.operatt_light_background);
        accent = getColor(night ? R.color.operatt_dark_primary : R.color.operatt_light_primary);
        surface = getColor(night ? R.color.operatt_dark_surface : R.color.operatt_light_surface);
        outline = getColor(night ? R.color.operatt_dark_outlineVariant
                : R.color.operatt_light_outlineVariant);
        error = getColor(night ? R.color.operatt_dark_error : R.color.operatt_light_error);
        chip = getColor(night ? R.color.operatt_dark_surfaceContainer
                : R.color.operatt_light_surfaceContainer);
        selectedChip = getColor(night ? R.color.operatt_dark_primaryContainer
                : R.color.operatt_light_primaryContainer);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadColors();
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(1); root.setBackgroundColor(backdrop);
        final MaterialToolbar bar = new MaterialToolbar(this);
        bar.setTitle(R.string.operatt_call_history_title);
        bar.setTitleTextColor(ink);
        bar.setNavigationIconTint(ink);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(64)));
        setContentView(root); setSupportActionBar(bar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        final LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(dp(6), dp(4), dp(6), dp(4));
        root.addView(buttons, new LinearLayout.LayoutParams(-1, dp(64)));
        final int[] tabNames = {R.string.operatt_call_filter_all,
            R.string.operatt_call_filter_missed, R.string.operatt_call_filter_outgoing};
        for (int i=0;i<3;i++) {
            final int target=i;
            final MaterialButton tab=new MaterialButton(this);
            tab.setText(tabNames[i]);tab.setAllCaps(false);
            tab.setTextColor(ink);
            tab.setBackgroundTintList(ColorStateList.valueOf(i == 0 ? selectedChip : chip));
            filterButtons.add(tab);
            buttons.addView(tab,new LinearLayout.LayoutParams(0,-1,1));
            tab.setOnClickListener(v->{filter=target;applyFilter();});
        }
        empty = new TextView(this);
        empty.setText(R.string.operatt_call_empty);empty.setTextColor(muted);
        empty.setGravity(Gravity.CENTER);empty.setPadding(10,25,10,25);
        root.addView(empty);
        final ListView list = new ListView(this);
        list.setDividerHeight(0);
        adapter=new BaseAdapter() {
            @Override public int getCount(){return visible.size();}
            @Override public Entry getItem(int position){return visible.get(position);}
            @Override public long getItemId(int position){return position;}
            @Override public View getView(int p, View old, ViewGroup parent) {
                final Entry e=getItem(p);
                final LinearLayout row=new LinearLayout(OperattCallHistoryActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14),dp(12),dp(12),dp(12));
                final TextView glyph=new TextView(OperattCallHistoryActivity.this);
                glyph.setText(e.incoming()?"↙":"↗");glyph.setTextSize(26);
                glyph.setTextColor(e.incoming()&&!e.successful()?error:accent);
                glyph.setGravity(Gravity.CENTER);
                row.addView(glyph,new LinearLayout.LayoutParams(dp(48),dp(48)));
                final LinearLayout info=new LinearLayout(OperattCallHistoryActivity.this);
                info.setOrientation(1);
                final TextView label=new TextView(OperattCallHistoryActivity.this);
                label.setText(e.name());label.setTextSize(16);label.setTextColor(ink);
                label.setTypeface(null,1);
                info.addView(label);
                final TextView detail=new TextView(OperattCallHistoryActivity.this);
                final int kind=e.incoming()?(e.successful()?R.string.operatt_call_incoming:
                    R.string.operatt_call_missed):R.string.operatt_call_outgoing;
                detail.setText(getString(kind)+" · "+DateFormat.getDateTimeInstance(3,3).format(e.when())
                    +(e.duration()>0?" · "+e.duration()/1000+"s":""));
                detail.setTextSize(12);detail.setTextColor(muted);
                info.addView(detail);
                row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
                final MaterialButton call=new MaterialButton(OperattCallHistoryActivity.this);
                call.setIconResource(R.drawable.ic_call_24dp);call.setText("");
                call.setContentDescription(getString(R.string.operatt_call_redial));
                call.setOnClickListener(v->redial(e));
                row.addView(call,new LinearLayout.LayoutParams(dp(50),dp(50)));
                row.setOnClickListener(v->openChat(e));
                row.setBackground(rounded(surface,18));
                final LinearLayout shell = new LinearLayout(OperattCallHistoryActivity.this);
                shell.setPadding(dp(12),dp(4),dp(12),dp(4));
                shell.addView(row, new LinearLayout.LayoutParams(-1,-2));
                return shell;
            }
        };
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
    }

    @Override protected void onBackendConnected() {
        if(xmppConnectionService==null)return;
        final Map<String,String> names=new HashMap<>();
        final String selected=androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .getString("operatt_selected_account",null);
        for (Conversation c:xmppConnectionService.getConversations())
            if(selected==null||selected.equals(c.getAccount().getJid().asBareJid().toString()))
                names.put(c.getUuid(),c.getName().toString());
        executor.execute(()->{
            final List<Entry> result=new ArrayList<>();
            final SQLiteDatabase db=DatabaseBackend.getInstance(this).getReadableDatabase();
            try(Cursor cursor=db.query(Message.TABLENAME,
                    new String[]{Message.CONVERSATION,Message.BODY,Message.TIME_SENT,Message.STATUS},
                    Message.TYPE+"=?",new String[]{String.valueOf(Message.TYPE_RTP_SESSION)},
                    null,null,Message.TIME_SENT+" DESC","250")) {
                while(cursor.moveToNext()){
                    final String uuid=cursor.getString(0), name=names.get(uuid);
                    if(name==null)continue;
                    final RtpSessionStatus status=RtpSessionStatus.of(cursor.getString(1));
                    result.add(new Entry(uuid,name,cursor.getLong(2),
                        cursor.getInt(3)==Message.STATUS_RECEIVED,status.successful,status.duration));
                }
            }
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                all.clear();all.addAll(result);applyFilter();
            });
        });
    }
    @Override protected void refreshUiReal() {}
    private void applyFilter(){
        visible.clear();
        for(Entry e:all)if(filter==0||(filter==1&&e.incoming()&&!e.successful())||
            (filter==2&&!e.incoming()))visible.add(e);
        for(int i=0;i<filterButtons.size();i++)
            filterButtons.get(i).setBackgroundTintList(ColorStateList.valueOf(
                    i==filter?selectedChip:chip));
        if(adapter!=null)adapter.notifyDataSetChanged();
        if(empty!=null)empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);
    }
    private void openChat(Entry e){
        final Intent i=new Intent(this,ConversationsActivity.class);
        i.setAction(ConversationsActivity.ACTION_VIEW_CONVERSATION);
        i.putExtra(ConversationsActivity.EXTRA_CONVERSATION,e.uuid());
        startActivity(i);
    }
    private void redial(Entry e){
        if(xmppConnectionService==null)return;
        final Conversation c=xmppConnectionService.findConversationByUuidReliable(e.uuid());
        if(c==null||!c.getAccount().isOnlineAndConnected())return;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.operatt_call_redial)
            .setItems(new String[]{getString(R.string.operatt_call_voice),
                getString(R.string.operatt_call_video)},(dialog,which)->{
                final Set<Media> media=which==0?ImmutableSet.of(Media.AUDIO):
                    ImmutableSet.of(Media.AUDIO,Media.VIDEO);
                CallIntegrationConnectionService.placeCall(xmppConnectionService,c.getAccount(),
                    c.getAddress(),media);
            }).show();
    }
    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item){
        if(item.getItemId()==android.R.id.home){finish();return true;}
        return super.onOptionsItemSelected(item);
    }
    @Override public void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
