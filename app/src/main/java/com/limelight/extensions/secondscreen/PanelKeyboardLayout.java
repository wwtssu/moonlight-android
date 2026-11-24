package com.limelight.extensions.secondscreen;

import android.view.KeyEvent;

import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.List;

/** Fixed Android/QWERTY codes. Shift changes captions, never the transmitted key. */
public final class PanelKeyboardLayout {
    public static final class Key {
        public final String label, shifted;
        public final int code, modifier;
        public final float units;
        public final boolean repeat;

        Key(String label, String shifted, int code, float units, int modifier, boolean repeat) {
            this.label = label;
            this.shifted = shifted;
            this.code = code;
            this.units = units;
            this.modifier = modifier;
            this.repeat = repeat;
        }

        public String label(boolean shift) {
            return shift && !shifted.isEmpty() ? shifted : label;
        }
    }

    private static Key k(String text, int code) {
        return new Key(text, "", code, 1, 0, false);
    }

    private static Key k(String text, int code, float width) {
        return new Key(text, "", code, width, 0, true);
    }

    private static Key p(String text, String shift, int code) {
        return new Key(text, shift, code, 1, 0, true);
    }

    private static Key m(String text, int code, int mask, float width) {
        return new Key(text, "", code, width, mask, false);
    }

    private static Key[] row(Object... keys) {
        List<Key> result = new ArrayList<>();
        for (Object item : keys) {
            if (item instanceof Key[]) for (Key key : (Key[]) item) result.add(key);
            else result.add((Key) item);
        }
        return result.toArray(new Key[0]);
    }

    private static Key[] letters(String text) {
        Key[] result = new Key[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            result[i] =
                    p(
                            String.valueOf(Character.toLowerCase(c)),
                            String.valueOf(c),
                            KeyEvent.KEYCODE_A + c - 'A');
        }
        return result;
    }

    public static final Key[][] MAIN;
    public static final Key[][] NAV;
    public static final Key[] NUM;

    static {
        Key[] functions = new Key[15];
        functions[0] = k("Esc", KeyEvent.KEYCODE_ESCAPE);
        for (int i = 1; i <= 12; i++) functions[i] = k("F" + i, KeyEvent.KEYCODE_F1 + i - 1);
        functions[13] = k("Del", KeyEvent.KEYCODE_FORWARD_DEL, 1);
        functions[14] = k("PrtSc", KeyEvent.KEYCODE_SYSRQ);
        Key[] numbers = new Key[10];
        String labels = "1234567890", symbols = "!@#$%^&*()";
        for (int i = 0; i < 10; i++)
            numbers[i] =
                    p(
                            labels.substring(i, i + 1),
                            symbols.substring(i, i + 1),
                            KeyEvent.KEYCODE_0 + labels.charAt(i) - '0');
        MAIN =
                new Key[][] {
                    functions,
                    row(
                            p("`", "~", KeyEvent.KEYCODE_GRAVE),
                            numbers,
                            p("-", "_", KeyEvent.KEYCODE_MINUS),
                            p("=", "+", KeyEvent.KEYCODE_EQUALS),
                            k("⌫", KeyEvent.KEYCODE_DEL, 2)),
                    row(
                            k("Tab", KeyEvent.KEYCODE_TAB, 1.5f),
                            letters("QWERTYUIOP"),
                            p("[", "{", KeyEvent.KEYCODE_LEFT_BRACKET),
                            p("]", "}", KeyEvent.KEYCODE_RIGHT_BRACKET),
                            new Key("\\", "|", KeyEvent.KEYCODE_BACKSLASH, 1.5f, 0, true)),
                    row(
                            new Key("Caps", "", KeyEvent.KEYCODE_CAPS_LOCK, 1.75f, 0, false),
                            letters("ASDFGHJKL"),
                            p(";", ":", KeyEvent.KEYCODE_SEMICOLON),
                            p("'", "\"", KeyEvent.KEYCODE_APOSTROPHE),
                            k("Enter", KeyEvent.KEYCODE_ENTER, 2.25f)),
                    row(
                            m(
                                    "Shift",
                                    KeyEvent.KEYCODE_SHIFT_LEFT,
                                    KeyboardPacket.MODIFIER_SHIFT,
                                    2.25f),
                            letters("ZXCVBNM"),
                            p(",", "<", KeyEvent.KEYCODE_COMMA),
                            p(".", ">", KeyEvent.KEYCODE_PERIOD),
                            p("/", "?", KeyEvent.KEYCODE_SLASH),
                            m(
                                    "Shift",
                                    KeyEvent.KEYCODE_SHIFT_RIGHT,
                                    KeyboardPacket.MODIFIER_SHIFT,
                                    2.75f)),
                    row(
                            m(
                                    "Ctrl",
                                    KeyEvent.KEYCODE_CTRL_LEFT,
                                    KeyboardPacket.MODIFIER_CTRL,
                                    1.25f),
                            m(
                                    "Win",
                                    KeyEvent.KEYCODE_META_LEFT,
                                    KeyboardPacket.MODIFIER_META,
                                    1.25f),
                            m("Alt", KeyEvent.KEYCODE_ALT_LEFT, KeyboardPacket.MODIFIER_ALT, 1.25f),
                            k("空格", KeyEvent.KEYCODE_SPACE, 8.75f),
                            m(
                                    "Alt",
                                    KeyEvent.KEYCODE_ALT_RIGHT,
                                    KeyboardPacket.MODIFIER_ALT,
                                    1.25f),
                            m(
                                    "Ctrl",
                                    KeyEvent.KEYCODE_CTRL_RIGHT,
                                    KeyboardPacket.MODIFIER_CTRL,
                                    1.25f))
                };
        NAV =
                new Key[][] {
                    row(
                            k("PrtSc", KeyEvent.KEYCODE_SYSRQ),
                            k("ScrLk", KeyEvent.KEYCODE_SCROLL_LOCK),
                            k("Pause", KeyEvent.KEYCODE_BREAK)),
                    row(
                            k("Ins", KeyEvent.KEYCODE_INSERT),
                            k("Home", KeyEvent.KEYCODE_MOVE_HOME, 1),
                            k("PgUp", KeyEvent.KEYCODE_PAGE_UP, 1)),
                    row(
                            k("Del", KeyEvent.KEYCODE_FORWARD_DEL, 1),
                            k("End", KeyEvent.KEYCODE_MOVE_END, 1),
                            k("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, 1)),
                    new Key[] {null, k("↑", KeyEvent.KEYCODE_DPAD_UP, 1), null},
                    row(
                            k("←", KeyEvent.KEYCODE_DPAD_LEFT, 1),
                            k("↓", KeyEvent.KEYCODE_DPAD_DOWN, 1),
                            k("→", KeyEvent.KEYCODE_DPAD_RIGHT, 1)),
                    row(
                            m("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.MODIFIER_CTRL, 1),
                            m("Win", KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.MODIFIER_META, 1),
                            m("Alt", KeyEvent.KEYCODE_ALT_LEFT, KeyboardPacket.MODIFIER_ALT, 1),
                            m(
                                    "Shift",
                                    KeyEvent.KEYCODE_SHIFT_LEFT,
                                    KeyboardPacket.MODIFIER_SHIFT,
                                    1),
                            k("Esc", KeyEvent.KEYCODE_ESCAPE))
                };
        NUM =
                new Key[] {
                    k("Num", KeyEvent.KEYCODE_NUM_LOCK),
                    k("÷", KeyEvent.KEYCODE_NUMPAD_DIVIDE, 1),
                    k("×", KeyEvent.KEYCODE_NUMPAD_MULTIPLY, 1),
                    k("−", KeyEvent.KEYCODE_NUMPAD_SUBTRACT, 1),
                    k("7", KeyEvent.KEYCODE_NUMPAD_7, 1),
                    k("8", KeyEvent.KEYCODE_NUMPAD_8, 1),
                    k("9", KeyEvent.KEYCODE_NUMPAD_9, 1),
                    k("+", KeyEvent.KEYCODE_NUMPAD_ADD, 1),
                    k("4", KeyEvent.KEYCODE_NUMPAD_4, 1),
                    k("5", KeyEvent.KEYCODE_NUMPAD_5, 1),
                    k("6", KeyEvent.KEYCODE_NUMPAD_6, 1),
                    k("1", KeyEvent.KEYCODE_NUMPAD_1, 1),
                    k("2", KeyEvent.KEYCODE_NUMPAD_2, 1),
                    k("3", KeyEvent.KEYCODE_NUMPAD_3, 1),
                    k("Enter", KeyEvent.KEYCODE_ENTER, 1),
                    k("0", KeyEvent.KEYCODE_NUMPAD_0, 1),
                    k(".", KeyEvent.KEYCODE_NUMPAD_DOT, 1)
                };
    }

    private PanelKeyboardLayout() {}
}
