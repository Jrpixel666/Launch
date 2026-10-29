package app.pocketshell;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.IntConsumer;

import app.pocketshell.linux.InstallerActivity;
import app.pocketshell.linux.LinuxPaths;
import app.pocketshell.linux.LinuxWindowActivity;
import app.pocketshell.linux.TerminalActivity;

/** In-launcher settings. Every change is applied immediately; there is no separate Apply step. */
final class SettingsView extends LinearLayout {
    private final Host host;
    private ScrollView scroll;
    private int insetTop, insetBottom;

    SettingsView(Context c, Host host) {
        super(c);
        this.host = host;
        setOrientation(VERTICAL);
        setClickable(true); // swallow touches so they never reach views underneath
        rebuild();
    }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        setPadding(0, top, 0, bottom);
    }

    void rebuild() {
        final int keepY = scroll != null ? scroll.getScrollY() : 0;
        removeAllViews();
        final Context c = getContext();
        final Theme t = host.theme();
        final Prefs p = host.prefs();
        setBackgroundColor(t.surface);
        setPadding(0, insetTop, 0, insetBottom);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        TextView back = Ui.text(c, "‹ Back", 16, t.text, true);
        back.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        back.setMinHeight(Ui.dp(c, 44));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setClickable(true);
        back.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.closeSettings();
            }
        });
        top.addView(back);
        TextView title = Ui.text(c, "Settings", 22, t.text, true);
        top.addView(title, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(top, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(VERTICAL);
        list.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 24));
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Appearance
        list.addView(header(c, t, "Appearance"));
        LinearLayout card = card(c, t);
        card.addView(label(c, t, "Theme", null));
        card.addView(segmented(c, t, new String[]{"Dark", "Light", "System"}, p.themeMode(),
                new IntConsumer() {
                    @Override
                    public void accept(int v) {
                        p.setThemeMode(v);
                        host.settingsChanged();
                    }
                }));
        card.addView(label(c, t, "Accent colour", Theme.ACCENT_NAMES[p.accent()]));
        card.addView(swatches(c, t, p));
        card.addView(label(c, t, "Style", null));
        card.addView(segmented(c, t, new String[]{"Rounded", "Omarchy"}, p.style(),
                new IntConsumer() {
                    @Override
                    public void accept(int v) {
                        p.setStyle(v);
                        host.settingsChanged();
                    }
                }));
        list.addView(card);

        // Home
        list.addView(header(c, t, "Home"));
        card = card(c, t);
        card.addView(stepper(c, t, "Columns", p.homeColumns(), 4, 5, 1, new IntConsumer() {
            @Override
            public void accept(int v) {
                p.setHomeColumns(v);
                host.home().normalize();
                host.settingsChanged();
            }
        }));
        card.addView(stepper(c, t, "Icon size (dp)", p.iconDp(), 44, 72, 4, new IntConsumer() {
            @Override
            public void accept(int v) {
                p.setIconDp(v);
                host.settingsChanged();
            }
        }));
        card.addView(toggle(c, t, "Widgets page", "Clock, status and calendar left of Home",
                p.showWidgets(), new IntConsumer() {
                    @Override
                    public void accept(int v) {
                        p.setShowWidgets(v == 1);
                        host.settingsChanged();
                    }
                }));
        list.addView(card);

        // Drawer
        list.addView(header(c, t, "App drawer"));
        card = card(c, t);
        card.addView(stepper(c, t, "Columns", p.drawerColumns(), 3, 6, 1, new IntConsumer() {
            @Override
            public void accept(int v) {
                p.setDrawerColumns(v);
                host.settingsChanged();
            }
        }));
        int hiddenCount = p.hidden().size();
        card.addView(action(c, t, "Hidden apps",
                hiddenCount == 0 ? "None hidden" : hiddenCount + " hidden. Open the drawer in Edit mode to show them.",
                "Manage", new Runnable() {
                    @Override
                    public void run() {
                        host.closeSettings();
                        host.openDrawer(true);
                    }
                }));
        list.addView(card);

        // Wallpaper and system
        list.addView(header(c, t, "System"));
        card = card(c, t);
        card.addView(action(c, t, "Wallpaper", "Pick from your gallery or wallpaper apps", "Choose",
                new Runnable() {
                    @Override
                    public void run() {
                        AppMenu.start(host, Intent.createChooser(
                                new Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"));
                    }
                }));
        card.addView(action(c, t, "Default home app", "Make Pocket Shell your launcher", "Open",
                new Runnable() {
                    @Override
                    public void run() {
                        AppMenu.start(host, new Intent(Settings.ACTION_HOME_SETTINGS));
                    }
                }));
        card.addView(action(c, t, "Reset Home layout", "Clears pages and dock, then restores defaults",
                "Reset", new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(host.activity())
                                .setTitle("Reset Home layout?")
                                .setMessage("Your pages and dock go back to the defaults. Installed apps are not affected.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int w) {
                                        host.resetLayout();
                                    }
                                })
                                .show();
                    }
                }));
        list.addView(card);

        // Agent and Linux
        list.addView(header(c, t, "Agent"));
        card = card(c, t);
        card.addView(action(c, t, "Agent command bar", "Swipe in from the left edge of Home, or ask here",
                "Open", new Runnable() {
                    @Override
                    public void run() {
                        host.closeSettings();
                        host.openAgent();
                    }
                }));
        list.addView(card);

        list.addView(header(c, t, "Linux"));
        card = card(c, t);
        boolean installed = LinuxPaths.isInstalled(c);
        card.addView(action(c, t, "Arch Linux ARM", installed ? "Installed via proot, no root needed"
                        : "Not installed — runs in a proot container, no root needed",
                installed ? "Manage" : "Install", new Runnable() {
                    @Override
                    public void run() {
                        c.startActivity(new Intent(c, InstallerActivity.class));
                    }
                }));
        card.addView(action(c, t, "Terminal", "A shell inside the Arch Linux container", "Open",
                new Runnable() {
                    @Override
                    public void run() {
                        c.startActivity(new Intent(c, TerminalActivity.class));
                    }
                }));
        card.addView(action(c, t, "Linux window", "Runs the Arch desktop full-screen, with swipe gestures",
                "Open", new Runnable() {
                    @Override
                    public void run() {
                        c.startActivity(new Intent(c, LinuxWindowActivity.class));
                    }
                }));
        list.addView(card);

        TextView about = Ui.text(c, "Pocket Shell 0.1.0\nAn Android launcher inspired by CTLST Shell. "
                + "Not affiliated with it.", 12, t.textDim, false);
        about.setPadding(Ui.dp(c, 4), Ui.dp(c, 20), Ui.dp(c, 4), 0);
        list.addView(about);

        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.scrollTo(0, keepY);
            }
        });
    }

    // ---- building blocks ----

    private TextView header(Context c, Theme t, String s) {
        TextView h = Ui.text(c, s.toUpperCase(), 12, t.textDim, true);
        h.setLetterSpacing(0.08f);
        h.setPadding(Ui.dp(c, 6), Ui.dp(c, 20), 0, Ui.dp(c, 8));
        return h;
    }

    private LinearLayout card(Context c, Theme t) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(VERTICAL);
        card.setBackground(Ui.rounded(c, t.surfaceHigh, 20));
        card.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10));
        return card;
    }

    private View label(Context c, Theme t, String title, String value) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 6));
        TextView a = Ui.text(c, title, 15, t.text, false);
        row.addView(a, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null) row.addView(Ui.text(c, value, 13, t.textDim, false));
        return row;
    }

    private View segmented(Context c, Theme t, String[] options, int selected, final IntConsumer onPick) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setPadding(0, 0, 0, Ui.dp(c, 8));
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            TextView b = Ui.button(c, options[i],
                    on ? t.accent : t.surface, on ? t.accent : t.outline, on ? t.onAccent : t.text);
            b.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onPick.accept(idx);
                }
            });
            LayoutParams lp = new LayoutParams(0, Ui.dp(c, 44), 1f);
            lp.rightMargin = i == options.length - 1 ? 0 : Ui.dp(c, 8);
            row.addView(b, lp);
        }
        return row;
    }

    private View swatches(Context c, Theme t, final Prefs p) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setPadding(0, 0, 0, Ui.dp(c, 8));
        for (int i = 0; i < Theme.ACCENTS.length; i++) {
            final int idx = i;
            View s = new View(c);
            boolean on = i == p.accent();
            android.graphics.drawable.GradientDrawable g = Ui.rounded(c, Theme.ACCENTS[i], 22);
            g.setStroke(Ui.dp(c, on ? 3 : 1), on ? t.text : t.outline);
            s.setBackground(g);
            s.setContentDescription(Theme.ACCENT_NAMES[i] + (on ? " (selected)" : ""));
            s.setClickable(true);
            s.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    p.setAccent(idx);
                    host.settingsChanged();
                }
            });
            LayoutParams lp = new LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44));
            lp.rightMargin = Ui.dp(c, 10);
            row.addView(s, lp);
        }
        return row;
    }

    private View stepper(Context c, final Theme t, String title, final int value, final int min,
                         final int max, final int step, final IntConsumer onChange) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        row.addView(Ui.text(c, title, 15, t.text, false),
                new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView minus = Ui.button(c, "−", t.surface, t.outline, t.text);
        minus.setContentDescription("Decrease " + title);
        minus.setEnabled(value > min);
        minus.setAlpha(value > min ? 1f : 0.35f);
        minus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (value > min) onChange.accept(Math.max(min, value - step));
            }
        });
        TextView val = Ui.text(c, String.valueOf(value), 16, t.text, true);
        val.setGravity(Gravity.CENTER);
        TextView plus = Ui.button(c, "+", t.surface, t.outline, t.text);
        plus.setContentDescription("Increase " + title);
        plus.setEnabled(value < max);
        plus.setAlpha(value < max ? 1f : 0.35f);
        plus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (value < max) onChange.accept(Math.min(max, value + step));
            }
        });
        row.addView(minus, new LayoutParams(Ui.dp(c, 48), Ui.dp(c, 44)));
        row.addView(val, new LayoutParams(Ui.dp(c, 48), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(plus, new LayoutParams(Ui.dp(c, 48), Ui.dp(c, 44)));
        return row;
    }

    private View toggle(Context c, Theme t, String title, String sub, final boolean on,
                        final IntConsumer onChange) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(VERTICAL);
        texts.addView(Ui.text(c, title, 15, t.text, false));
        texts.addView(Ui.text(c, sub, 12, t.textDim, false));
        row.addView(texts, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView pill = Ui.button(c, on ? "On" : "Off", on ? t.accent : t.surface,
                on ? t.accent : t.outline, on ? t.onAccent : t.text);
        pill.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onChange.accept(on ? 0 : 1);
            }
        });
        row.addView(pill, new LayoutParams(Ui.dp(c, 72), Ui.dp(c, 44)));
        return row;
    }

    private View action(Context c, Theme t, String title, String sub, String button,
                        final Runnable run) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(VERTICAL);
        texts.addView(Ui.text(c, title, 15, t.text, false));
        TextView s = Ui.text(c, sub, 12, t.textDim, false);
        texts.addView(s);
        row.addView(texts, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView b = Ui.button(c, button, t.surface, t.outline, t.text);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                run.run();
            }
        });
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 44));
        lp.leftMargin = Ui.dp(c, 12);
        row.addView(b, lp);
        return row;
    }
}
