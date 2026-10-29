package app.pocketshell;

import android.content.Context;
import android.content.res.Configuration;

/** Resolved colour roles. Rebuilt whenever the theme, accent, style or system night mode changes. */
final class Theme {
    static final int STYLE_ROUNDED = 0;
    /** Omarchy: a flat, square-cornered, monospaced dark style. */
    static final int STYLE_OMARCHY = 1;

    static final int[] ACCENTS = {
            0xFF5B8CFF, 0xFF2DD4BF, 0xFFA78BFA, 0xFFFB7185, 0xFFFBBF24, 0xFF4ADE80
    };
    static final String[] ACCENT_NAMES = {"Blue", "Teal", "Violet", "Rose", "Amber", "Green"};

    /** Omarchy's own accent — a muted terminal green over near-black surfaces. */
    private static final int OMARCHY_ACCENT = 0xFF9ECE6A;

    final boolean dark;
    final boolean flat;
    final int accent;
    final int onAccent;
    final int surface;
    final int surfaceHigh;
    final int text;
    final int textDim;
    final int outline;

    private Theme(boolean dark, boolean flat, int accent) {
        this.dark = dark;
        this.flat = flat;
        this.accent = accent;
        if (flat) {
            // Omarchy palette: near-black background, square panels, high-contrast mono text.
            onAccent = 0xFF101014;
            surface = 0xFF11111B;
            surfaceHigh = 0xFF1A1A24;
            text = 0xFFE0E0E8;
            textDim = 0xFF8A8A9A;
            outline = 0xFF2A2A38;
        } else {
            onAccent = 0xFF0B0D10;
            if (dark) {
                surface = 0xFF14161A;
                surfaceHigh = 0xFF1F2227;
                text = 0xFFEDEFF2;
                textDim = 0xFF9AA1AB;
                outline = 0xFF2C3036;
            } else {
                surface = 0xFFF6F7F9;
                surfaceHigh = 0xFFFFFFFF;
                text = 0xFF16181C;
                textDim = 0xFF5F6670;
                outline = 0xFFD9DDE3;
            }
        }
    }

    static Theme from(Context c, Prefs p) {
        boolean systemDark = (c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int mode = p.themeMode();
        boolean dark = mode == Prefs.MODE_DARK || (mode == Prefs.MODE_SYSTEM && systemDark);
        boolean flat = p.style() == STYLE_OMARCHY;
        int accent = flat ? OMARCHY_ACCENT : ACCENTS[p.accent()];
        Theme t = new Theme(flat ? true : dark, flat, accent);
        Ui.setMonospace(flat);
        return t;
    }
}
