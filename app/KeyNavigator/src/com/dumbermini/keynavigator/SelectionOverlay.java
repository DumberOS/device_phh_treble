package com.dumbermini.keynavigator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/** A visual marker only: it neither takes focus nor intercepts touch. */
final class SelectionOverlay extends View {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final WindowManager manager;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect bounds = new Rect();
    private final int[] origin = new int[2];
    private boolean attached;

    SelectionOverlay(Context context) {
        super(context);
        manager = context.getSystemService(WindowManager.class);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setFocusable(false);
    }

    void show(Rect rect) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Rect copy = new Rect(rect);
            main.post(() -> show(copy));
            return;
        }
        if (!attached) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.setTitle("Key Navigator highlight");
            manager.addView(this, params);
            attached = true;
        }
        if (!bounds.equals(rect)) { bounds.set(rect); invalidate(); }
    }

    // Clear only the drawing during a scroll, keeping the non-interactive window attached.
    // Removing and re-adding it would generate more accessibility window events.
    void clearHighlight() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::clearHighlight); return; }
        if (!bounds.isEmpty()) { bounds.setEmpty(); invalidate(); }
    }

    void hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::hide); return; }
        if (attached) { manager.removeView(this); attached = false; }
        bounds.setEmpty();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bounds.isEmpty()) return;
        getLocationOnScreen(origin);
        float density = getResources().getDisplayMetrics().density;
        RectF rect = new RectF(bounds);
        rect.offset(-origin[0], -origin[1]);
        rect.inset(3 * density, 3 * density);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x2000DFFF);
        canvas.drawRoundRect(rect, 5 * density, 5 * density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(6 * density);
        canvas.drawRoundRect(rect, 5 * density, 5 * density, paint);
        paint.setColor(0xFF00DFFF);
        paint.setStrokeWidth(3 * density);
        canvas.drawRoundRect(rect, 5 * density, 5 * density, paint);
    }
}
