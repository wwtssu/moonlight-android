package com.limelight.extensions.secondscreen;

import java.util.LinkedHashMap;
import java.util.Map;

/** Relative pad gestures in local dp, independent of the video viewport. */
public final class PanelTouchpadState {
    private static final float POINTER_SPEED = 2f;

    public interface Sink {
        void move(int dx, int dy);

        void button(boolean right, boolean down);

        void scroll(int vertical, int horizontal);
    }

    private static final class Point {
        float x, y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private final Map<Integer, Point> points = new LinkedHashMap<>();
    private final Sink sink;
    private int maxContacts;
    private boolean dragging, canceled, scrolled;
    private long started, lastTap;
    private float startX,
            startY,
            lastTapX,
            lastTapY,
            travel,
            remainderX,
            remainderY,
            scrollX,
            scrollY;

    public PanelTouchpadState(Sink sink) {
        this.sink = sink;
    }

    public void down(int id, float x, float y, long now) {
        if (points.containsKey(id)) return;
        if (points.isEmpty()) {
            started = now;
            startX = x;
            startY = y;
            travel = 0;
            maxContacts = 0;
            canceled = false;
            scrolled = false;
            scrollX = scrollY = 0;
            if (lastTap != 0
                    && now - lastTap < 320
                    && Math.hypot(x - lastTapX, y - lastTapY) < 28) {
                dragging = true;
                sink.button(false, true);
                lastTap = 0;
            }
        }
        points.put(id, new Point(x, y));
        maxContacts = Math.max(maxContacts, points.size());
        if (points.size() > 1) {
            if (dragging) canceled = true;
            releaseDrag();
            lastTap = 0;
        }
        if (points.size() > 2) canceled = true;
    }

    private float center(boolean x) {
        float sum = 0;
        for (Point p : points.values()) sum += x ? p.x : p.y;
        return sum / points.size();
    }

    public void move(int id, float x, float y) {
        Point p = points.get(id);
        if (p == null) return;
        float oldX = center(true), oldY = center(false);
        travel += Math.hypot(x - p.x, y - p.y);
        p.x = x;
        p.y = y;
        if (canceled) return;
        float dx = center(true) - oldX, dy = center(false) - oldY;
        if (maxContacts == 1) {
            remainderX += dx * POINTER_SPEED;
            remainderY += dy * POINTER_SPEED;
            int mx = (int) remainderX, my = (int) remainderY;
            remainderX -= mx;
            remainderY -= my;
            if (mx != 0 || my != 0) sink.move(mx, my);
        } else if (points.size() == 2) {
            scrollY += dy * 5;
            scrollX -= dx * 5;
            if (travel < 9 && !scrolled) return;
            scrolled = true;
            int v = (int) scrollY, h = (int) scrollX;
            scrollY -= v;
            scrollX -= h;
            if (v != 0 || h != 0) sink.scroll(v, h);
        }
    }

    public void up(int id, boolean cancel, long now) {
        if (points.remove(id) == null) return;
        canceled |= cancel;
        if (!points.isEmpty()) return;
        boolean wasDrag = dragging;
        releaseDrag();
        if (!canceled
                && !wasDrag
                && !scrolled
                && now - started < 280
                && travel < 9
                && maxContacts <= 2) {
            boolean right = maxContacts == 2;
            sink.button(right, true);
            sink.button(right, false);
            if (!right) {
                lastTap = now;
                lastTapX = startX;
                lastTapY = startY;
            } else lastTap = 0;
        } else lastTap = 0;
        maxContacts = 0;
    }

    private void releaseDrag() {
        if (dragging) {
            dragging = false;
            sink.button(false, false);
        }
    }

    public void cancel() {
        releaseDrag();
        points.clear();
        maxContacts = 0;
        lastTap = 0;
        canceled = true;
        remainderX = remainderY = scrollX = scrollY = 0;
    }
}
