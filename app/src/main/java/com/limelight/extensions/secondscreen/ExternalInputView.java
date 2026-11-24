package com.limelight.extensions.secondscreen;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import com.limelight.R;
import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * App-drawn PC keys and a relative pad. Created by the controller with live input sinks, never
 * inflated from XML or connected to an IME.
 */
@SuppressLint("ViewConstructor")
public final class ExternalInputView extends View {
    private static final int TOGGLE = 1, MAIN = 2, EXTENSION = 3;
    private static final int BACKGROUND = 0xff111219, TEXT = 0xfff1eff7;

    private static final class Zone {
        final RectF bounds, hitBounds;
        final PanelKeyboardLayout.Key key;
        final int action;

        Zone(RectF bounds, PanelKeyboardLayout.Key key, int action) {
            this(bounds, bounds, key, action);
        }

        Zone(RectF bounds, RectF hitBounds, PanelKeyboardLayout.Key key, int action) {
            this.bounds = bounds;
            this.hitBounds = hitBounds;
            this.key = key;
            this.action = action;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF iconBounds = new RectF();
    private final RectF pausedMessageBounds = new RectF();
    private final List<Zone> zones = new ArrayList<>();
    private final Map<Integer, Zone> contacts = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PanelKeyboardState keyboard;
    private final PanelTouchpadState pad;
    private boolean keyboardVisible, extension;
    private float density, width, height, leftInset, topInset, rightInset, bottomInset;
    private final Runnable repeat =
            new Runnable() {
                @Override
                public void run() {
                    keyboard.repeat(SystemClock.uptimeMillis());
                    if (keyboard.hasRepeatingContacts()) handler.postDelayed(this, 50);
                }
            };

    public ExternalInputView(
            Context context, PanelKeyboardState.Sink keySink, PanelTouchpadState.Sink mouseSink) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        keyboard = new PanelKeyboardState(keySink);
        pad = new PanelTouchpadState(mouseSink);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateContentDescription();
        setOnApplyWindowInsetsListener(
                (view, insets) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Legacy insets may retain navigation-bar space even in immersive mode.
                        Insets safe =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        leftInset = safe.left;
                        topInset = safe.top;
                        rightInset = safe.right;
                        bottomInset = safe.bottom;
                    } else {
                        leftInset = insets.getSystemWindowInsetLeft();
                        topInset = insets.getSystemWindowInsetTop();
                        rightInset = insets.getSystemWindowInsetRight();
                        bottomInset = insets.getSystemWindowInsetBottom();
                    }
                    resize();
                    return insets;
                });
    }

    private void resize() {
        releaseInputs();
        width = Math.max(0, (getWidth() - leftInset - rightInset) / density);
        height = Math.max(0, (getHeight() - topInset - bottomInset) / density);
        layoutZones();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resize();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        updateContentDescription();
        invalidate();
    }

    private void updateContentDescription() {
        setContentDescription(
                !isEnabled()
                        ? getResources().getString(R.string.external_input_panel_paused)
                                + "，"
                                + getResources()
                                        .getString(R.string.external_input_panel_paused_hint)
                        : keyboardVisible ? "电脑键盘，右上角返回触控板" : "外置屏触控板，右上角切换电脑键盘");
    }

    public byte modifiers() {
        return keyboard.modifiers();
    }

    public boolean isExtensionPage() {
        return extension;
    }

    public void restoreKeyboardPage(boolean extension) {
        this.extension = extension;
        layoutZones();
        invalidate();
    }

    public void releaseInputs() {
        handler.removeCallbacks(repeat);
        contacts.clear();
        keyboard.releaseAll();
        pad.cancel();
        invalidate();
    }

    public void resetToPad() {
        releaseInputs();
        keyboardVisible = false;
        updateContentDescription();
        layoutZones();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseInputs();
        super.onDetachedFromWindow();
    }

    private void add(float x, float y, float w, float h, PanelKeyboardLayout.Key key, int action) {
        if (w > 0 && h > 0) zones.add(new Zone(new RectF(x, y, x + w, y + h), key, action));
    }

    private void rows(
            PanelKeyboardLayout.Key[][] rows,
            float x,
            float y,
            float w,
            float h,
            boolean standard) {
        float rowHeight = (h - 4 * (rows.length - 1)) / rows.length;
        for (PanelKeyboardLayout.Key[] row : rows) {
            float units = 0;
            for (PanelKeyboardLayout.Key key : row)
                units += key == null ? 1 : standard ? key.units : 1;
            float xx = x;
            for (PanelKeyboardLayout.Key key : row) {
                float unit = key == null ? 1 : standard ? key.units : 1;
                float kw = (w + 4) * unit / units - 4;
                if (key != null) add(xx, y, kw, rowHeight, key, 0);
                xx += kw + 4;
            }
            y += rowHeight + 4;
        }
    }

    private void layoutZones() {
        zones.clear();
        if (width <= 0 || height <= 0) return;
        if (keyboardVisible) {
            add(118, 19, 82, 30, null, MAIN);
            add(205, 19, 82, 30, null, EXTENSION);
            float w = width - 24, h = height - 76;
            if (!extension) rows(PanelKeyboardLayout.MAIN, 12, 64, w, h, true);
            else {
                float half = (w - 20) / 2;
                rows(PanelKeyboardLayout.NAV, 12, 64, half, h, false);
                float x = 12 + half + 20, y = 64, cw = (half - 12) / 4, rh = (h - 16) / 5;
                int[][] cells = {
                    {0, 0, 1, 1}, {0, 1, 1, 1}, {0, 2, 1, 1}, {0, 3, 1, 1}, {1, 0, 1, 1},
                    {1, 1, 1, 1}, {1, 2, 1, 1}, {1, 3, 2, 1}, {2, 0, 1, 1}, {2, 1, 1, 1},
                    {2, 2, 1, 1}, {3, 0, 1, 1}, {3, 1, 1, 1}, {3, 2, 1, 1}, {3, 3, 2, 1},
                    {4, 0, 1, 2}, {4, 2, 1, 1}
                };
                for (int i = 0; i < cells.length; i++) {
                    int[] c = cells[i];
                    add(
                            x + c[1] * (cw + 4),
                            y + c[0] * (rh + 4),
                            c[3] * (cw + 4) - 4,
                            c[2] * (rh + 4) - 4,
                            PanelKeyboardLayout.NUM[i],
                            0);
                }
            }
        }
        // Keep the 44dp button, but make the whole 64dp corner touchable.
        zones.add(
                new Zone(
                        new RectF(width - 56, 12, width - 12, 56),
                        new RectF(Math.max(0, width - 64), 0, width, Math.min(height, 64)),
                        null,
                        TOGGLE));
    }

    private Zone hit(float x, float y) {
        for (int i = zones.size() - 1; i >= 0; i--)
            if (zones.get(i).hitBounds.contains(x, y)) return zones.get(i);
        return null;
    }

    private void rect(Canvas c, RectF r, int color, float radius) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        c.drawRoundRect(r, radius, radius, paint);
    }

    private void text(
            Canvas c, String value, float x, float y, float w, float h, float size, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size);
        while (paint.measureText(value) > w - 4 && paint.getTextSize() > 8)
            paint.setTextSize(paint.getTextSize() - .5f);
        paint.setColor(color);
        c.drawText(
                value,
                x + (w - paint.measureText(value)) / 2,
                y + h / 2 - (paint.ascent() + paint.descent()) / 2,
                paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BACKGROUND);
        canvas.save();
        canvas.translate(leftInset, topInset);
        canvas.scale(density, density);
        boolean shift = (keyboard.modifiers() & KeyboardPacket.MODIFIER_SHIFT) != 0;
        if (keyboardVisible) {
            text(canvas, "电脑键盘", 12, 12, 80, 44, 15, TEXT);
        }
        for (Zone zone : zones) {
            RectF r = zone.bounds;
            if (zone.key != null) {
                PanelKeyboardLayout.Key key = zone.key;
                boolean held = keyboard.down(key.code), latched = keyboard.locked(key.code);
                int bg =
                        held
                                ? (latched ? 0xff413256 : 0xff59437d)
                                : key.modifier != 0 ? 0xff24222e : 0xff282a35;
                if (shift && key.modifier == KeyboardPacket.MODIFIER_SHIFT && !held)
                    bg = 0xff30283f;
                rect(canvas, r, bg, 6);
                paint.setColor(held ? 0xffa98bdc : 0xff414350);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(.8f);
                canvas.drawRoundRect(r, 6, 6, paint);
                boolean letter = key.code >= KeyEvent.KEYCODE_A && key.code <= KeyEvent.KEYCODE_Z;
                if (!letter && !key.shifted.isEmpty()) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setTextSize(9);
                    paint.setColor(0xffa3a0b1);
                    canvas.drawText(shift ? key.label : key.shifted, r.left + 5, r.top + 13, paint);
                }
                boolean function =
                        key.code >= KeyEvent.KEYCODE_F1 && key.code <= KeyEvent.KEYCODE_F12;
                float size = function || key.modifier != 0 || key.label.length() > 2 ? 12 : 19;
                text(canvas, key.label(shift), r.left, r.top, r.width(), r.height(), size, TEXT);
            } else if (zone.action == TOGGLE) {
                rect(canvas, r, 0xff272932, 12);
                paint.setColor(0xffe1e3eb);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.4f);
                RectF icon = iconBounds;
                icon.set(r.left + 11, r.top + 13, r.right - 11, r.bottom - 13);
                canvas.drawRoundRect(icon, 3, 3, paint);
                if (keyboardVisible)
                    canvas.drawLine(
                            icon.left + 2, icon.bottom - 4, icon.right - 2, icon.bottom - 4, paint);
                else {
                    for (int i = 0; i < 3; i++)
                        for (int j = 0; j < 2; j++) {
                            paint.setStyle(Paint.Style.FILL);
                            canvas.drawRect(
                                    icon.left + 4 + i * 5,
                                    icon.top + 3 + j * 4,
                                    icon.left + 6 + i * 5,
                                    icon.top + 5 + j * 4,
                                    paint);
                        }
                    canvas.drawRect(
                            icon.left + 5, icon.bottom - 4, icon.right - 5, icon.bottom - 2, paint);
                }
            } else {
                boolean selected = zone.action == (extension ? EXTENSION : MAIN);
                if (selected) rect(canvas, r, 0xff2e263f, 6);
                text(
                        canvas,
                        zone.action == MAIN ? "主键盘" : "扩展键区",
                        r.left,
                        r.top,
                        r.width(),
                        r.height(),
                        11,
                        selected ? 0xffd0bdff : 0xff9592a7);
            }
        }
        if (!isEnabled() && width > 48 && height > 84) drawPausedOverlay(canvas);
        canvas.restore();
    }

    private void drawPausedOverlay(Canvas canvas) {
        canvas.drawColor(0xb3191a20);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(0xffe9ba56);
        canvas.drawRect(4, 4, width - 4, height - 4, paint);
        float w = Math.min(width - 48, 360), x = (width - w) / 2, y = (height - 84) / 2;
        RectF message = pausedMessageBounds;
        message.set(x, y, x + w, y + 84);
        rect(canvas, message, 0xff24252c, 0);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(0xffe9ba56);
        canvas.drawRect(message, paint);
        text(
                canvas,
                getResources().getString(R.string.external_input_panel_paused),
                x + 12,
                y + 14,
                w - 24,
                26,
                16,
                0xffffd269);
        text(
                canvas,
                getResources().getString(R.string.external_input_panel_paused_hint),
                x + 12,
                y + 46,
                w - 24,
                22,
                12,
                0xffd2c5a8);
    }

    private void action(int action) {
        if (action == TOGGLE) {
            releaseInputs();
            keyboardVisible = !keyboardVisible;
        } else {
            keyboard.releaseTransient();
            pad.cancel();
            contacts.clear();
            handler.removeCallbacks(repeat);
            extension = action == EXTENSION;
        }
        updateContentDescription();
        layoutZones();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return true;
        int action = event.getActionMasked(),
                index = event.getActionIndex(),
                id = event.getPointerId(index);
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_CANCEL) {
            releaseInputs();
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float x = (event.getX(index) - leftInset) / density,
                    y = (event.getY(index) - topInset) / density;
            Zone zone = hit(x, y);
            if (zone != null) {
                contacts.put(id, zone);
                if (zone.key != null) {
                    keyboard.press(id, zone.key, now);
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (zone.key.repeat) {
                        handler.removeCallbacks(repeat);
                        handler.postDelayed(repeat, 50);
                    }
                }
            } else if (!keyboardVisible && x >= 0 && y >= 0 && x < width && y < height)
                pad.down(id, x, y, now);
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int pointer = event.getPointerId(i);
                float x = (event.getX(i) - leftInset) / density,
                        y = (event.getY(i) - topInset) / density;
                Zone zone = contacts.get(pointer);
                if (zone != null) {
                    if (!zone.hitBounds.contains(x, y)) {
                        contacts.remove(pointer);
                        keyboard.release(pointer, true);
                    }
                } else if (!keyboardVisible) pad.move(pointer, x, y);
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            boolean cancel =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            && (event.getFlags() & MotionEvent.FLAG_CANCELED) != 0;
            float x = (event.getX(index) - leftInset) / density,
                    y = (event.getY(index) - topInset) / density;
            Zone zone = contacts.remove(id);
            if (zone != null) {
                if (zone.key != null)
                    keyboard.release(id, cancel || !zone.hitBounds.contains(x, y));
                else if (!cancel && zone.hitBounds.contains(x, y)) {
                    performClick();
                    action(zone.action);
                }
            } else if (!keyboardVisible) {
                if (!cancel) pad.move(id, x, y);
                pad.up(id, cancel, now);
            }
        }
        invalidate();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
