package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.FileBackend;
import java.io.File;

/** In-app photo viewer; only available to our own activities. */
public final class ImageViewerActivity extends AppCompatActivity {
    private static final String PATH = "operatt.photo.path";
    private ImageView image;
    private float zoom = 1f;
    private float lastX;
    private float lastY;
    private float tx;
    private float ty;
    private ScaleGestureDetector scaleGesture;

    public static void open(Context context, File file) {
        if (!file.isFile()) {
            Toast.makeText(context, R.string.file_deleted, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(context, ImageViewerActivity.class);
        intent.putExtra(PATH, file.getAbsolutePath());
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    private int dp(int size) {
        return (int) (size * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String path = getIntent().getStringExtra(PATH);
        final File file = path == null ? null : new File(path);
        if (file == null || !file.isFile()) {
            Toast.makeText(this, R.string.file_deleted, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        getWindow().setStatusBarColor(Color.rgb(12, 20, 31));
        getWindow().setNavigationBarColor(Color.rgb(12, 20, 31));
        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 20, 31));
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));

        final ImageButton close = button(android.R.drawable.ic_menu_close_clear_cancel, "Close");
        FrameLayout.LayoutParams left = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.START);
        left.setMargins(dp(12), dp(12), 0, 0);
        root.addView(close, left);
        close.setOnClickListener(v -> finish());

        final ImageButton share = button(android.R.drawable.ic_menu_share, "Share photo");
        FrameLayout.LayoutParams right = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
        right.setMargins(0, dp(12), dp(12), 0);
        root.addView(share, right);
        share.setOnClickListener(v -> {
            try {
                Uri uri = FileBackend.getUriForFile(this, file);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/*");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, null));
            } catch (RuntimeException e) {
                Toast.makeText(this, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
            }
        });

        scaleGesture = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoom = Math.min(5f, Math.max(1f, zoom * detector.getScaleFactor()));
                if (zoom == 1f) { tx = 0; ty = 0; }
                redraw();
                return true;
            }
        });
        image.setOnTouchListener((v, event) -> {
            scaleGesture.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastX = event.getX();
                lastY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                    && event.getPointerCount() == 1 && !scaleGesture.isInProgress() && zoom > 1f) {
                tx += event.getX() - lastX;
                ty += event.getY() - lastY;
                redraw();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    && event.getEventTime() - event.getDownTime() < 250 && zoom == 1f) {
                // A single tap keeps the toolbar accessible; zoom is driven by pinch.
            }
            if (event.getPointerCount() == 1) {
                lastX = event.getX();
                lastY = event.getY();
            }
            return true;
        });
        setContentView(root);
        new Thread(() -> {
            Bitmap decoded = null;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (options.outWidth > 0 && options.outHeight > 0) {
                    int sample = 1;
                    while (Math.max(options.outWidth / sample, options.outHeight / sample) > 3072) {
                        sample *= 2;
                    }
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = sample;
                    decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                }
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // Invalid or unsupported image; show an error instead of crashing.
            }
            final Bitmap result = decoded;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (result != null) result.recycle();
                } else if (result == null) {
                    Toast.makeText(this, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    image.setImageBitmap(result);
                }
            });
        }, "operatt-photo-loader").start();
    }

    private ImageButton button(int icon, String description) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setColorFilter(Color.WHITE);
        b.setContentDescription(description);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(175, 24, 34, 46));
        bg.setCornerRadius(dp(24));
        b.setBackground(bg);
        return b;
    }

    private void redraw() {
        if (image == null) return;
        float maxX = image.getWidth() * (zoom - 1f) / 2f;
        float maxY = image.getHeight() * (zoom - 1f) / 2f;
        tx = Math.max(-maxX, Math.min(maxX, tx));
        ty = Math.max(-maxY, Math.min(maxY, ty));
        image.setScaleX(zoom);
        image.setScaleY(zoom);
        image.setTranslationX(tx);
        image.setTranslationY(ty);
    }
}
