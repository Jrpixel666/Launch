package app.pocketshell;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

/** Small view-building helpers. The whole UI is built in code, so there are no layout XML files. */
final class Ui {
    private Ui() {}

    /** Set by {@link Theme#from}. Omarchy style clips corners and switches body text to mono. */
    private static volatile boolean monospace;

    static void setMonospace(boolean on) {
        monospace = on;
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, monospace ? Math.min(radiusDp, 3f) : radiusDp));
        return g;
    }

    static Drawable pressable(Context c, int color, int pressedColor, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(c, pressedColor, radiusDp));
        s.addState(new int[]{}, rounded(c, color, radiusDp));
        return s;
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (monospace) {
            t.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        } else if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    /** A tappable pill with a text label. */
    static TextView button(Context c, CharSequence s, int bg, int pressed, int fg) {
        TextView t = text(c, s, 14, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        t.setMinHeight(dp(c, 44));
        t.setBackground(pressable(c, bg, pressed, 14));
        t.setClickable(true);
        return t;
    }

    static void shadow(TextView t) {
        t.setShadowLayer(8f, 0f, 2f, 0xAA000000);
    }

    /**
     * Runs {@code action} when the view is tapped or swiped in the given direction
     * (+1 = downwards, -1 = upwards). Used for the grab handles on overlays.
     */
    static void dismissOnSwipe(View handle, final int direction, final Runnable action) {
        final Context c = handle.getContext();
        final int slop = ViewConfiguration.get(c).getScaledTouchSlop();
        final float min = dp(c, 40);
        handle.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX();
                        sy = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        float dy = (e.getRawY() - sy) * direction;
                        float adx = Math.abs(e.getRawX() - sx);
                        boolean tap = Math.abs(e.getRawY() - sy) < slop && adx < slop;
                        if (tap || (dy > min && dy > adx)) action.run();
                        return true;
                    default:
                        return true;
                }
            }
        });
    }
}
