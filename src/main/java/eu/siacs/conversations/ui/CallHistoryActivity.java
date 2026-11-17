package eu.siacs.conversations.ui;

import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.RtpSessionStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated call history, querying persisted RTP session events instead of
 * mixing them into the main conversation list.
 */
public class CallHistoryActivity extends XmppActivity {
    private final List<CallRow> rows = new ArrayList<>();
    private ArrayAdapter<CallRow> adapter;

    private static final class CallRow {
        final String conversationUuid;
        final String title;
        final String description;

        CallRow(String conversationUuid, String title, String description) {
            this.conversationUuid = conversationUuid;
            this.title = title;
            this.description = description;
        }

        @NonNull @Override public String toString() { return title; }
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics()));
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Calls");
        final LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(246, 248, 248));
        final TextView heading = new TextView(this);
        heading.setText("Call history");
        heading.setTextColor(Color.rgb(13, 67, 88));
        heading.setTextSize(27);
        heading.setPadding(dp(22), dp(25), dp(22), dp(8));
        heading.setTypeface(null, 1);
        page.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView subtitle = new TextView(this);
        subtitle.setText("Incoming, outgoing and missed calls");
        subtitle.setTextColor(Color.rgb(82, 101, 113));
        subtitle.setTextSize(14);
        subtitle.setPadding(dp(22), 0, dp(22), dp(16));
        page.addView(subtitle);
        final ListView list = new ListView(this);
        list.setDividerHeight(dp(1));
        list.setBackgroundColor(Color.rgb(252, 253, 251));
        adapter = new ArrayAdapter<CallRow>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, rows) {
            @NonNull @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                final View view = convertView != null ? convertView
                        : getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
                final CallRow row = getItem(position);
                final TextView first = view.findViewById(android.R.id.text1);
                final TextView second = view.findViewById(android.R.id.text2);
                first.setText(row.title);
                first.setTextColor(Color.rgb(13, 47, 61));
                first.setTextSize(16);
                second.setText(row.description);
                second.setTextColor(Color.rgb(92, 111, 120));
                second.setTextSize(13);
                view.setPadding(dp(12), dp(10), dp(12), dp(10));
                return view;
            }
        };
        list.setAdapter(adapter);
        list.setEmptyView(new TextView(this) {{
            setText("No call history on this device");
            setGravity(Gravity.CENTER);
        }});
        page.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (xmppConnectionService == null) return;
            final Conversation conversation = xmppConnectionService.findConversationByUuid(
                    rows.get(position).conversationUuid);
            if (conversation != null) {
                switchToConversation(conversation);
            }
        });
    }

    @Override
    protected void refreshUiReal() {
        // Call list data is refreshed when the XMPP backend connects.
    }

    @Override
    protected void onBackendConnected() {
        new Thread(() -> {
            final List<CallRow> loaded = new ArrayList<>();
            try (Cursor cursor = xmppConnectionService.databaseBackend.getReadableDatabase().query(
                    Message.TABLENAME,
                    new String[] {Message.CONVERSATION, Message.BODY, Message.TIME_SENT,
                            Message.STATUS},
                    Message.TYPE + "=?",
                    new String[]{String.valueOf(Message.TYPE_RTP_SESSION)},
                    null, null, Message.TIME_SENT + " DESC", "150")) {
                while (cursor.moveToNext()) {
                    final String uuid = cursor.getString(0);
                    final String body = cursor.getString(1);
                    final long timestamp = cursor.getLong(2);
                    final boolean incoming = cursor.getInt(3) == Message.STATUS_RECEIVED;
                    final RtpSessionStatus call = RtpSessionStatus.of(body);
                    final Conversation conversation =
                            xmppConnectionService.findConversationByUuid(uuid);
                    final String name = conversation == null ? "Unknown contact"
                            : conversation.getName().toString();
                    final String direction = incoming ? (call.successful ? "Incoming" : "Missed")
                            : (call.successful ? "Outgoing" : "Unanswered");
                    final String duration = call.successful
                            ? " · " + (call.duration / 60) + "m " + (call.duration % 60) + "s"
                            : "";
                    final String date = DateFormat.format("dd MMM yyyy  HH:mm", timestamp)
                            .toString();
                    loaded.add(new CallRow(uuid, name, direction + duration + " · " + date));
                }
            } catch (final RuntimeException ignored) {
                // An empty list is safer than showing fabricated call records.
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || adapter == null) return;
                rows.clear();
                rows.addAll(loaded);
                adapter.notifyDataSetChanged();
            });
        }, "operatt-call-history").start();
    }
}
