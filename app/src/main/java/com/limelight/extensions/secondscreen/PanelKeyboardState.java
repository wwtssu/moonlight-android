package com.limelight.extensions.secondscreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Contact-based modifiers: a standalone contact toggles a latch; a chord never adds one. */
public final class PanelKeyboardState {
    public interface Sink {
        void key(int code, boolean down, byte modifiers);
    }

    private static final class Press {
        final PanelKeyboardLayout.Key key;
        final boolean wasLocked;
        boolean combined;
        long repeatAt;

        Press(PanelKeyboardLayout.Key key, boolean wasLocked, boolean combined, long now) {
            this.key = key;
            this.wasLocked = wasLocked;
            this.combined = combined;
            repeatAt = now + 400;
        }
    }

    private final Map<Integer, Press> contacts = new HashMap<>();
    private final Map<Integer, PanelKeyboardLayout.Key> locks = new LinkedHashMap<>();
    private final Sink sink;

    public PanelKeyboardState(Sink sink) {
        this.sink = sink;
    }

    public void press(int pointer, PanelKeyboardLayout.Key key, long now) {
        if (contacts.containsKey(pointer)) return;
        boolean alreadyDown = down(key.code), combined = false;
        for (Press other : contacts.values()) {
            if (other.key.code != key.code) {
                combined = true;
                other.combined = true;
            }
        }
        contacts.put(pointer, new Press(key, locks.containsKey(key.code), combined, now));
        if (!alreadyDown) sink.key(key.code, true, modifiers());
    }

    public void release(int pointer, boolean cancel) {
        Press press = contacts.get(pointer);
        if (press == null) return;
        if (!cancel && press.key.modifier != 0) {
            if (press.combined || press.wasLocked) locks.remove(press.key.code);
            else locks.put(press.key.code, press.key);
        }
        contacts.remove(pointer);
        if (!down(press.key.code)) sink.key(press.key.code, false, modifiers());
    }

    public boolean down(int code) {
        if (locks.containsKey(code)) return true;
        for (Press press : contacts.values()) if (press.key.code == code) return true;
        return false;
    }

    public boolean locked(int code) {
        return locks.containsKey(code);
    }

    public byte modifiers() {
        int mask = 0;
        for (Press press : contacts.values()) mask |= press.key.modifier;
        for (PanelKeyboardLayout.Key key : locks.values()) mask |= key.modifier;
        return (byte) mask;
    }

    public void repeat(long now) {
        Map<Integer, Press> repeated = new HashMap<>();
        for (Press press : contacts.values()) {
            if (press.key.repeat
                    && press.repeatAt <= now
                    && !repeated.containsKey(press.key.code)) {
                sink.key(press.key.code, true, modifiers());
                repeated.put(press.key.code, press);
            }
        }
        for (Press press : contacts.values())
            if (repeated.containsKey(press.key.code)) press.repeatAt = now + 50;
    }

    public boolean hasContacts() {
        return !contacts.isEmpty();
    }

    public boolean hasRepeatingContacts() {
        for (Press press : contacts.values()) if (press.key.repeat) return true;
        return false;
    }

    public void releaseTransient() {
        release(false);
    }

    public void releaseAll() {
        release(true);
    }

    private void release(boolean clearLocks) {
        Map<Integer, PanelKeyboardLayout.Key> old = new LinkedHashMap<>();
        for (Press press : contacts.values()) old.put(press.key.code, press.key);
        if (clearLocks) old.putAll(locks);
        contacts.clear();
        if (clearLocks) locks.clear();
        // Release character keys before modifiers, with each physical key lifted exactly once.
        for (PanelKeyboardLayout.Key key : new ArrayList<>(old.values())) {
            if (key.modifier == 0 && !down(key.code)) sink.key(key.code, false, modifiers());
        }
        for (PanelKeyboardLayout.Key key : old.values()) {
            if (key.modifier != 0 && !down(key.code)) sink.key(key.code, false, modifiers());
        }
    }
}
