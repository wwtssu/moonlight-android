package com.limelight.extensions.secondscreen;

import android.app.Activity;
import android.app.Presentation;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.limelight.LimeLog;
import com.limelight.R;

import java.util.ArrayList;
import java.util.List;

/** Owns only an input Presentation on the existing external display. Never moves video. */
public final class ExternalInputController {
    public interface DisplayProvider {
        Display findExternalDisplay();
    }

    private final Activity activity;
    private final DisplayManager displays;
    private final DisplayProvider provider;
    private final PanelKeyboardState.Sink keys;
    private final PanelTouchpadState.Sink mouse;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new ArrayList<>();
    private InputPresentation presentation;
    private boolean extensionPage;
    private boolean enabled, blocked, resumed, connected, focused, registered, destroyed;
    private int status = R.string.external_input_disabled;
    private final Runnable attach = () -> refresh();
    private final DisplayManager.DisplayListener displayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int id) {
                    schedule();
                }

                @Override
                public void onDisplayRemoved(int id) {
                    if (presentation != null && presentation.getDisplay().getDisplayId() == id)
                        dismiss();
                    schedule();
                }

                @Override
                public void onDisplayChanged(int id) {
                    schedule();
                }
            };

    public ExternalInputController(
            Activity activity,
            DisplayProvider provider,
            PanelKeyboardState.Sink keys,
            PanelTouchpadState.Sink mouse) {
        this.activity = activity;
        this.provider = provider;
        this.keys = keys;
        this.mouse = mouse;
        displays = (DisplayManager) activity.getSystemService(Activity.DISPLAY_SERVICE);
    }

    public void configure(boolean enabled, boolean blocked) {
        this.enabled = enabled;
        this.blocked = blocked;
        refresh();
    }

    public void setResumed(boolean resumed) {
        this.resumed = resumed;
        refresh();
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
        refresh();
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        if (presentation != null && presentation.view != null) {
            if (!focused) presentation.view.releaseInputs();
            presentation.view.setEnabled(focused);
        }
    }

    public byte modifiers() {
        return presentation != null && presentation.view != null
                ? presentation.view.modifiers()
                : 0;
    }

    public int status() {
        return status;
    }

    public void addListener(Runnable listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void status(int value) {
        status = value;
        for (Runnable listener : new ArrayList<>(listeners)) listener.run();
    }

    private void schedule() {
        handler.removeCallbacks(attach);
        handler.postDelayed(attach, 150);
    }

    public void retry() {
        refresh();
    }

    public void destroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        dismiss();
        if (registered && displays != null) displays.unregisterDisplayListener(displayListener);
        registered = false;
        listeners.clear();
    }

    private void dismiss() {
        InputPresentation old = presentation;
        presentation = null;
        if (old != null) {
            if (old.view != null) {
                extensionPage = old.view.isExtensionPage();
                old.view.releaseInputs();
            }
            old.setOnDismissListener(null);
            old.dismiss();
        }
    }

    private void refresh() {
        if (destroyed) return;
        if (!enabled || blocked || !resumed || !connected || activity.isFinishing()) {
            handler.removeCallbacks(attach);
            dismiss();
            if (registered && displays != null) displays.unregisterDisplayListener(displayListener);
            registered = false;
            status(
                    !enabled
                            ? R.string.external_input_disabled
                            : blocked
                                    ? R.string.external_input_video_busy
                                    : !connected
                                            ? R.string.external_input_wait_connection
                                            : R.string.external_input_paused);
            return;
        }
        if (displays != null && !registered) {
            displays.registerDisplayListener(displayListener, handler);
            registered = true;
        }
        Display display = provider.findExternalDisplay();
        if (display == null || !display.isValid()) {
            dismiss();
            status(R.string.external_input_wait_display);
            return;
        }
        if (display.getDisplayId()
                == activity.getWindowManager().getDefaultDisplay().getDisplayId()) {
            dismiss();
            status(R.string.external_input_same_display);
            return;
        }
        if (display.getState() != Display.STATE_ON && display.getState() != Display.STATE_UNKNOWN) {
            dismiss();
            status(R.string.external_input_display_off);
            return;
        }
        if (presentation != null
                && presentation.isShowing()
                && presentation.getDisplay().getDisplayId() == display.getDisplayId()) {
            status(R.string.external_input_ready);
            return;
        }
        dismiss();
        InputPresentation next = null;
        try {
            next = new InputPresentation(display);
            InputPresentation created = next;
            next.setOnDismissListener(
                    dialog -> {
                        if (presentation == created) {
                            presentation = null;
                            if (created.view != null) {
                                extensionPage = created.view.isExtensionPage();
                                created.view.releaseInputs();
                            }
                            status(R.string.external_input_window_unavailable);
                        }
                    });
            next.show();
            presentation = next;
            next.view.setEnabled(focused);
            status(R.string.external_input_ready);
        } catch (WindowManager.InvalidDisplayException
                | WindowManager.BadTokenException
                | SecurityException
                | IllegalArgumentException e) {
            if (next != null) {
                next.setOnDismissListener(null);
                if (next.view != null) next.view.releaseInputs();
                if (next.isShowing()) next.dismiss();
            }
            LimeLog.warning("External input window unavailable: " + e.getMessage());
            status(R.string.external_input_window_unavailable);
        }
    }

    private final class InputPresentation extends Presentation {
        ExternalInputView view;

        InputPresentation(Display display) {
            super(activity, display);
        }

        @Override
        protected void onCreate(Bundle savedState) {
            super.onCreate(savedState);
            Window window = getWindow();
            if (window != null) {
                window.addFlags(
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                window.setBackgroundDrawableResource(android.R.color.black);
                window.getDecorView()
                        .setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
            view = new ExternalInputView(getContext(), keys, mouse);
            view.restoreKeyboardPage(extensionPage);
            setContentView(view);
            if (window != null)
                window.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
        }

        @Override
        protected void onStop() {
            if (view != null) view.resetToPad();
            super.onStop();
        }
    }
}
