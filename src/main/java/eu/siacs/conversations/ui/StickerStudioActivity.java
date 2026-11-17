package eu.siacs.conversations.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.FileBackend;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Local photo-to-sticker maker. Produces PNG attachments, not native protocol sticker packs.
 * All content is stored in the app's private Images/Stickers folder on this device.
 */
public final class StickerStudioActivity extends AppCompatActivity {
    public static final String EXTRA_SEND_TO_CHAT = "operatt.stickers.send_to_chat";
    private static final int SIZE = 512;
    private Bitmap editable;
    private Bitmap undo;
    private ImageView preview;
    private LinearLayout savedStrip;
    private TextView hint;
    private boolean forChat;
    private final ActivityResultLauncher<String> choosePhoto = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::loadPhoto);

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + .5f);
    }

    private File stickerFolder() {
        return new File(new File(getFilesDir(), "Images"), "Stickers");
    }

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        forChat = getIntent().getBooleanExtra(EXTRA_SEND_TO_CHAT, false);
        setTitle(R.string.operatt_create_sticker);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));
        root.setBackgroundColor(Color.rgb(246, 249, 252));

        hint = new TextView(this);
        hint.setText(R.string.operatt_sticker_erase_hint);
        hint.setTextColor(Color.DKGRAY);
        hint.setTextSize(14);
        hint.setPadding(0, dp(8), 0, dp(12));
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.rgb(225, 234, 241));
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        preview.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP && editable != null) {
                Matrix inv = new Matrix();
                if (preview.getImageMatrix().invert(inv)) {
                    float[] xy = new float[]{event.getX(), event.getY()};
                    inv.mapPoints(xy);
                    int x = (int) xy[0];
                    int y = (int) xy[1];
                    if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) {
                        undo = editable.copy(Bitmap.Config.ARGB_8888, true);
                        eraseConnectedBackground(x, y);
                        preview.setImageBitmap(editable);
                    }
                }
            }
            return true;
        });

        LinearLayout commands = new LinearLayout(this);
        commands.setOrientation(LinearLayout.HORIZONTAL);
        commands.setGravity(Gravity.CENTER);
        root.addView(commands, new LinearLayout.LayoutParams(-1, dp(62)));

        Button select = button(getString(R.string.operatt_sticker_choose_photo));
        Button undoButton = button(getString(R.string.operatt_sticker_undo));
        Button saveButton = button(getString(forChat ? R.string.operatt_sticker_send : R.string.operatt_sticker_save));
        commands.addView(select, new LinearLayout.LayoutParams(0, dp(52), 1));
        commands.addView(undoButton, new LinearLayout.LayoutParams(0, dp(52), .7f));
        commands.addView(saveButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        select.setOnClickListener(v -> choosePhoto.launch("image/*"));
        undoButton.setOnClickListener(v -> {
            if (undo != null) {
                editable = undo;
                undo = null;
                preview.setImageBitmap(editable);
            }
        });
        saveButton.setOnClickListener(v -> saveSticker());

        TextView galleryTitle = new TextView(this);
        galleryTitle.setText(R.string.operatt_sticker_saved);
        galleryTitle.setTextColor(Color.BLACK);
        galleryTitle.setTextSize(16);
        galleryTitle.setPadding(0, dp(14), 0, dp(6));
        root.addView(galleryTitle, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        savedStrip = new LinearLayout(this);
        savedStrip.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(savedStrip);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(96)));
        setContentView(root);
        refreshSavedStickers();
    }

    private Button button(String text) {
        Button result = new Button(this);
        result.setText(text);
        result.setTextAllCaps(false);
        result.setTextSize(12);
        return result;
    }

    private void loadPhoto(Uri uri) {
        if (uri == null) return;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException("Unable to read photo");
            Bitmap raw = BitmapFactory.decodeStream(stream);
            if (raw == null) throw new IOException("Unsupported image");
            final int size = Math.min(raw.getWidth(), raw.getHeight());
            final int left = (raw.getWidth() - size) / 2;
            final int top = (raw.getHeight() - size) / 2;
            Bitmap square = Bitmap.createBitmap(raw, left, top, size, size);
            editable = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
                    .copy(Bitmap.Config.ARGB_8888, true);
            if (square != raw) square.recycle();
            if (raw != editable && !raw.isRecycled()) raw.recycle();
            undo = null;
            preview.setImageBitmap(editable);
        } catch (IOException | RuntimeException | OutOfMemoryError ex) {
            Toast.makeText(this, R.string.operatt_sticker_failed, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Flood-fill from a user-selected background pixel; do not remove unrelated
     * similarly coloured regions that are disconnected from the selection.
     */
    private void eraseConnectedBackground(int x, int y) {
        final int[] pixels = new int[SIZE * SIZE];
        editable.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        final int start = pixels[y * SIZE + x];
        final int red = Color.red(start), green = Color.green(start), blue = Color.blue(start);
        final boolean[] seen = new boolean[pixels.length];
        final int[] queue = new int[pixels.length];
        int read = 0, write = 0;
        queue[write++] = y * SIZE + x;
        seen[queue[0]] = true;
        while (read < write) {
            int index = queue[read++];
            int pixel = pixels[index];
            if (Color.alpha(pixel) == 0) continue;
            int dr = Color.red(pixel) - red;
            int dg = Color.green(pixel) - green;
            int db = Color.blue(pixel) - blue;
            if (dr * dr + dg * dg + db * db > 75 * 75) continue;
            pixels[index] = Color.TRANSPARENT;
            int col = index % SIZE, row = index / SIZE;
            if (col > 0 && !seen[index - 1]) { seen[index - 1] = true; queue[write++] = index - 1; }
            if (col < SIZE - 1 && !seen[index + 1]) { seen[index + 1] = true; queue[write++] = index + 1; }
            if (row > 0 && !seen[index - SIZE]) { seen[index - SIZE] = true; queue[write++] = index - SIZE; }
            if (row < SIZE - 1 && !seen[index + SIZE]) { seen[index + SIZE] = true; queue[write++] = index + SIZE; }
        }
        editable.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
    }

    private void saveSticker() {
        if (editable == null) {
            Toast.makeText(this, R.string.operatt_sticker_choose_first, Toast.LENGTH_SHORT).show();
            return;
        }
        File folder = stickerFolder();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            Toast.makeText(this, R.string.operatt_sticker_failed, Toast.LENGTH_LONG).show();
            return;
        }
        File file = new File(folder, "sticker_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!editable.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("PNG compression failed");
            }
            output.flush();
            refreshSavedStickers();
            if (forChat) {
                final Intent result = new Intent();
                result.setData(FileBackend.getUriForFile(this, file));
                result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                setResult(RESULT_OK, result);
                finish();
            } else {
                Toast.makeText(this, R.string.operatt_sticker_saved_ok, Toast.LENGTH_SHORT).show();
            }
        } catch (IOException | RuntimeException error) {
            Toast.makeText(this, R.string.operatt_sticker_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void refreshSavedStickers() {
        if (savedStrip == null) return;
        savedStrip.removeAllViews();
        File[] files = stickerFolder().listFiles((folder, name) -> name.endsWith(".png"));
        if (files == null) return;
        Arrays.sort(files, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int index = 0; index < Math.min(files.length, 40); index++) {
            final File file = files[index];
            ImageButton thumb = new ImageButton(this);
            thumb.setBackgroundColor(Color.rgb(235, 240, 245));
            thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 4;
            thumb.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath(), opts));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76), dp(76));
            lp.setMargins(dp(2), 0, dp(2), 0);
            savedStrip.addView(thumb, lp);
            thumb.setOnClickListener(v -> loadPhoto(Uri.fromFile(file)));
            thumb.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.operatt_sticker_delete)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete, (dialog, which) -> {
                            if (file.delete()) refreshSavedStickers();
                        }).show();
                return true;
            });
        }
    }
}
