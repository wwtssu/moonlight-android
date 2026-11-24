package com.limelight.extensions.secondscreen;

import static org.junit.Assert.*;

import android.view.KeyEvent;

import com.limelight.nvstream.input.KeyboardPacket;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ExternalInputStateTest {
    private final List<String> events = new ArrayList<>();
    private final PanelKeyboardState keys =
            new PanelKeyboardState(
                    (code, down, mods) -> events.add((down ? "D" : "U") + code + ":" + mods));

    private static PanelKeyboardLayout.Key key(int code) {
        for (PanelKeyboardLayout.Key[][] bank :
                new PanelKeyboardLayout.Key[][][] {
                    PanelKeyboardLayout.MAIN, PanelKeyboardLayout.NAV
                })
            for (PanelKeyboardLayout.Key[] row : bank)
                for (PanelKeyboardLayout.Key k : row) if (k != null && k.code == code) return k;
        throw new AssertionError("Missing key " + code);
    }

    private void down(int pointer, int code) {
        keys.press(pointer, key(code), 100);
    }

    @Test
    public void standaloneModifierLatchesRegardlessOfDurationAndTogglesOff() {
        down(1, KeyEvent.KEYCODE_CTRL_LEFT);
        keys.release(1, false);
        assertTrue(keys.locked(KeyEvent.KEYCODE_CTRL_LEFT));
        assertEquals(1, events.size());
        down(2, KeyEvent.KEYCODE_CTRL_LEFT);
        keys.release(2, false);
        assertFalse(keys.down(KeyEvent.KEYCODE_CTRL_LEFT));
        assertEquals(2, events.size());
    }

    @Test
    public void fastChordDoesNotLatch() {
        down(1, KeyEvent.KEYCODE_CTRL_LEFT);
        down(2, KeyEvent.KEYCODE_C);
        keys.release(2, false);
        keys.release(1, false);
        assertEquals(0, keys.modifiers());
        assertEquals(4, events.size());
    }

    @Test
    public void reversedPressOrderStillCountsAsChord() {
        down(1, KeyEvent.KEYCODE_C);
        down(2, KeyEvent.KEYCODE_SHIFT_RIGHT);
        keys.release(2, false);
        keys.release(1, false);
        assertEquals(0, keys.modifiers());
    }

    @Test
    public void modifierOnlyChordDoesNotLatchEitherModifier() {
        down(1, KeyEvent.KEYCODE_CTRL_LEFT);
        down(2, KeyEvent.KEYCODE_SHIFT_LEFT);
        keys.release(1, false);
        keys.release(2, false);
        assertEquals(0, keys.modifiers());
    }

    @Test
    public void cancelDoesNotLatchAndKeepsPreviouslyExplicitLatch() {
        down(1, KeyEvent.KEYCODE_CTRL_LEFT);
        keys.release(1, true);
        assertEquals(0, keys.modifiers());
        down(2, KeyEvent.KEYCODE_CTRL_LEFT);
        keys.release(2, false);
        down(3, KeyEvent.KEYCODE_CTRL_LEFT);
        keys.release(3, true);
        assertTrue(keys.locked(KeyEvent.KEYCODE_CTRL_LEFT));
    }

    @Test
    public void chordWithRepressedLatchReleasesIt() {
        down(1, KeyEvent.KEYCODE_CTRL_LEFT);
        keys.release(1, false);
        down(2, KeyEvent.KEYCODE_CTRL_LEFT);
        down(3, KeyEvent.KEYCODE_C);
        keys.release(3, false);
        keys.release(2, false);
        assertEquals(0, keys.modifiers());
    }

    @Test
    public void canceledCharacterStillCountsAsCombination() {
        down(1, KeyEvent.KEYCODE_SHIFT_LEFT);
        down(2, KeyEvent.KEYCODE_A);
        keys.release(2, true);
        keys.release(1, false);
        assertEquals(0, keys.modifiers());
    }

    @Test
    public void duplicateContactsLiftOnlyAfterLastOwner() {
        down(1, KeyEvent.KEYCODE_SPACE);
        down(2, KeyEvent.KEYCODE_SPACE);
        assertEquals(1, events.size());
        keys.release(1, false);
        assertEquals(1, events.size());
        keys.release(2, false);
        assertEquals(2, events.size());
    }

    @Test
    public void pageChangePreservesLocksButReleasesContacts() {
        down(1, KeyEvent.KEYCODE_SHIFT_LEFT);
        keys.release(1, false);
        down(2, KeyEvent.KEYCODE_A);
        keys.releaseTransient();
        assertTrue(keys.locked(KeyEvent.KEYCODE_SHIFT_LEFT));
        assertFalse(keys.down(KeyEvent.KEYCODE_A));
        keys.releaseAll();
        assertEquals(0, keys.modifiers());
        assertFalse(keys.hasContacts());
    }

    @Test
    public void repeatKeepsRealDownUpLifetimeAndNeverRepeatsModifiersOrCaps() {
        down(1, KeyEvent.KEYCODE_DEL);
        down(2, KeyEvent.KEYCODE_SHIFT_LEFT);
        down(3, KeyEvent.KEYCODE_CAPS_LOCK);
        keys.repeat(499);
        assertEquals(3, events.size());
        keys.repeat(500);
        assertEquals(4, events.size());
        keys.repeat(549);
        assertEquals(4, events.size());
        keys.repeat(550);
        assertEquals(5, events.size());
        keys.releaseAll();
        assertEquals(8, events.size());
    }

    @Test
    public void bothShiftsKeepPreviewActiveUntilLastRelease() {
        down(1, KeyEvent.KEYCODE_SHIFT_LEFT);
        down(2, KeyEvent.KEYCODE_SHIFT_RIGHT);
        keys.release(1, false);
        assertEquals(KeyboardPacket.MODIFIER_SHIFT, keys.modifiers());
        keys.release(2, false);
        assertEquals(0, keys.modifiers());
    }

    @Test
    public void layoutHasSixEqualUnitRowsAndExplicitFinalKeys() {
        assertEquals(6, PanelKeyboardLayout.MAIN.length);
        for (PanelKeyboardLayout.Key[] row : PanelKeyboardLayout.MAIN) {
            float total = 0;
            for (PanelKeyboardLayout.Key k : row) total += k.units;
            assertEquals(15, total, 0);
        }
        assertEquals(KeyEvent.KEYCODE_FORWARD_DEL, PanelKeyboardLayout.MAIN[0][13].code);
        assertEquals(KeyEvent.KEYCODE_SYSRQ, PanelKeyboardLayout.MAIN[0][14].code);
        assertEquals("a", key(KeyEvent.KEYCODE_A).label(false));
        assertEquals("A", key(KeyEvent.KEYCODE_A).label(true));
        assertEquals("1", key(KeyEvent.KEYCODE_1).label(false));
        assertEquals("!", key(KeyEvent.KEYCODE_1).label(true));
        assertEquals("{", key(KeyEvent.KEYCODE_LEFT_BRACKET).label(true));
    }

    @Test
    public void panelCannotReleasePhysicalKeyboardOwner() {
        KeyOwnership state = new KeyOwnership();
        assertTrue(state.existing(4, 0x80a2, true));
        assertFalse(state.panel(0x80a2, true));
        assertFalse(state.panel(0x80a2, false));
        assertTrue(state.existing(4, 0x80a2, false));
    }

    @Test
    public void physicalKeyboardCannotReleasePanelOwner() {
        KeyOwnership state = new KeyOwnership();
        assertTrue(state.panel(30, true));
        assertFalse(state.existing(1, 30, true));
        assertFalse(state.existing(1, 30, false));
        assertTrue(state.panel(30, false));
    }

    @Test
    public void existingPathAndRepeatAreUnchangedWithoutPanel() {
        KeyOwnership state = new KeyOwnership();
        assertTrue(state.existing(1, 30, true));
        assertTrue(state.existing(1, 30, true));
        assertTrue(state.existing(1, 30, false));
        assertTrue(state.existing(1, 30, false));
    }

    private final List<String> mouseEvents = new ArrayList<>();
    private final PanelTouchpadState pad =
            new PanelTouchpadState(
                    new PanelTouchpadState.Sink() {
                        @Override
                        public void move(int dx, int dy) {
                            mouseEvents.add("move:" + dx + "," + dy);
                        }

                        @Override
                        public void button(boolean right, boolean down) {
                            mouseEvents.add((right ? "right" : "left") + (down ? "D" : "U"));
                        }

                        @Override
                        public void scroll(int v, int h) {
                            mouseEvents.add("scroll:" + v + "," + h);
                        }
                    });

    @Test
    public void singleTapClicksLeftAndFractionsAccumulate() {
        pad.down(1, 0, 0, 100);
        pad.move(1, .4f, 0);
        pad.move(1, 1.2f, 0);
        pad.up(1, false, 120);
        assertEquals("move:2,0", mouseEvents.get(0));
        assertEquals("leftD", mouseEvents.get(1));
        assertEquals("leftU", mouseEvents.get(2));
    }

    @Test
    public void twoFingerTapClicksRight() {
        pad.down(1, 0, 0, 100);
        pad.down(2, 20, 0, 110);
        pad.up(1, false, 120);
        pad.up(2, false, 125);
        assertEquals(2, mouseEvents.size());
        assertEquals("rightD", mouseEvents.get(0));
    }

    @Test
    public void scrollingCannotEndAsRightClickOrMoveAfterOneFingerLifts() {
        pad.down(1, 0, 0, 100);
        pad.down(2, 20, 0, 110);
        pad.move(1, 0, 20);
        pad.move(2, 20, 20);
        pad.up(1, false, 130);
        pad.move(2, 20, 80);
        pad.up(2, false, 150);
        assertFalse(mouseEvents.isEmpty());
        for (String event : mouseEvents) assertTrue(event.startsWith("scroll:"));
    }

    @Test
    public void smallTwoFingerJitterIsOnlyRightClick() {
        pad.down(1, 0, 0, 100);
        pad.down(2, 20, 0, 110);
        pad.move(1, 0, 2);
        pad.move(2, 20, 2);
        pad.up(1, false, 130);
        pad.up(2, false, 140);
        assertEquals(2, mouseEvents.size());
        assertEquals("rightD", mouseEvents.get(0));
    }

    @Test
    public void doubleTapDragCancelsWithPairedRelease() {
        pad.down(1, 0, 0, 100);
        pad.up(1, false, 120);
        pad.down(2, 0, 0, 200);
        pad.move(2, 20, 0);
        pad.cancel();
        assertEquals("leftD", mouseEvents.get(2));
        assertEquals("leftU", mouseEvents.get(mouseEvents.size() - 1));
        int size = mouseEvents.size();
        pad.up(2, false, 220);
        assertEquals(size, mouseEvents.size());
    }

    @Test
    public void thirdContactOrCancelNeverClicks() {
        pad.down(1, 0, 0, 100);
        pad.down(2, 20, 0, 110);
        pad.down(3, 40, 0, 120);
        pad.up(1, false, 130);
        pad.up(2, false, 140);
        pad.up(3, false, 150);
        assertTrue(mouseEvents.isEmpty());
        pad.down(1, 0, 0, 300);
        pad.down(2, 20, 0, 310);
        pad.up(1, true, 320);
        pad.up(2, false, 330);
        assertTrue(mouseEvents.isEmpty());
    }

    @Test
    public void secondFingerCancelsDragWithoutAddingRightClick() {
        pad.down(1, 0, 0, 100);
        pad.up(1, false, 120);
        pad.down(2, 0, 0, 200);
        pad.down(3, 20, 0, 210);
        pad.up(2, false, 220);
        pad.up(3, false, 230);
        assertEquals(4, mouseEvents.size());
        assertEquals("leftU", mouseEvents.get(3));
    }

    @Test
    public void repeatedKeyWithTwoContactsRepeatsOnlyOncePerTick() {
        down(1, KeyEvent.KEYCODE_SPACE);
        down(2, KeyEvent.KEYCODE_SPACE);
        keys.repeat(500);
        assertEquals(2, events.size());
        keys.release(1, false);
        keys.release(2, false);
        assertEquals(3, events.size());
    }

    @Test
    public void ownedKeyIsTransferredWhenPhysicalSourceReleasesFirst() {
        KeyOwnership state = new KeyOwnership();
        assertTrue(state.existing(1, 30, true));
        assertFalse(state.panel(30, true));
        assertFalse(state.existing(1, 30, false));
        assertTrue(state.panel(30, false));
    }
}
