package com.termux.terminal;

import java.nio.charset.StandardCharsets;

/** A client which receives callbacks from events triggered by feeding input to a {@link TerminalEmulator}. */
/** A abstract {@link TerminalSession} */
public abstract class TerminalOutput {

    /** Write a string using the UTF-8 encoding to the terminal client. */
    public final void write(String data) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        write(bytes, 0, bytes.length);
    }

    /** Write bytes to the terminal client. */
    public abstract void write(byte[] data, int offset, int count);

    /** Notify the terminal client that the terminal title has changed. */
    public abstract void titleChanged(String oldTitle, String newTitle);

    /** Notify the terminal client that the terminal title has changed. */
    public abstract void clipboardText(String text);

    /** Notify the terminal client that a bell character (ASCII 7, bell, BEL, \a, ^G)) has been received. */
    public abstract void onBell();

    public abstract void onColorsChanged();

    /** Write the Unicode code point to the terminal encoded in UTF-8. */
    public abstract void writeCodePoint(boolean prependEscape, int codePoint);

    /** Inform the attached pty of the new size and reflow or initialize the emulator. */
    public abstract void updateSize(int columns, int rows);

    public abstract TerminalEmulator getEmulator();

    public abstract boolean isRunning();
}
