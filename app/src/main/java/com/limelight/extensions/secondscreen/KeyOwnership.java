package com.limelight.extensions.secondscreen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Prevents one input source from lifting a key still held by the external panel or keyboard. */
public final class KeyOwnership {
    private final Map<Integer, Set<Integer>> existing = new HashMap<>();
    private final Set<Integer> panel = new HashSet<>();

    public boolean existing(int source, int key, boolean down) {
        Set<Integer> sources = existing.get(key);
        if (sources == null) {
            sources = new HashSet<>();
            if (down) existing.put(key, sources);
        }
        boolean repeat = down && sources.contains(source);
        if (down) sources.add(source);
        else {
            sources.remove(source);
            if (sources.isEmpty()) existing.remove(key);
        }
        // With no panel owner, preserve the existing path, including repeated downs.
        return !panel.contains(key) || repeat;
    }

    public boolean panel(int key, boolean down) {
        if (down) {
            boolean repeat = !panel.add(key);
            return repeat || !existing.containsKey(key);
        }
        return panel.remove(key) && !existing.containsKey(key);
    }

    public void clear() {
        panel.clear();
        existing.clear();
    }
}
