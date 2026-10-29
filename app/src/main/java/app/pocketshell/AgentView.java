package app.pocketshell;

import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.pocketshell.linux.InstallerActivity;
import app.pocketshell.linux.LinuxWindowActivity;
import app.pocketshell.linux.TerminalActivity;

/**
 * The Agent bar: a single command input that either runs a built-in action (install Arch, open a
 * terminal, flip the theme, launch an app by name, …) or falls back to a fuzzy app search. Swipe
 * in from the left edge of Home, or long-press the dock's Agent icon, to open it.
 *
 * <p>This is a rule-based dispatcher, not a model-backed assistant: every command below is
 * matched locally and runs instantly with no network call, which keeps it fast and private.
 */
final class AgentView extends FrameLayout {
    private final Host host;
    private EditText input;
    private LinearLayout results;
    private int insetTop, insetBottom;

    AgentView(Context c, Host host) {
        super(c);
        this.host = host;
        setVisibility(GONE);
    }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
    }

    void reset() {
        rebuild();
        filter("");
    }

    void hideKeyboard() {
        if (input == null) return;
        InputMethodManager imm = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private void rebuild() {
        removeAllViews();
        final Context c = getContext();
        final Theme t = host.theme();
        setBackgroundColor(Ui.withAlpha(t.surface, 0xF2));
        setClickable(true);

        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(c, 16), insetTop + Ui.dp(c, 16), Ui.dp(c, 16), insetBottom + Ui.dp(c, 16));

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(Ui.rounded(c, t.surfaceHigh, 18));
        bar.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);

        input = new EditText(c);
        input.setHint("Ask or run something — \"install arch\", \"terminal\", \"open camera\"…");
        input.setHintTextColor(t.textDim);
        input.setTextColor(t.text);
        input.setBackground(null);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setOnEditorActionListener((v, actionId, event) -> {
            runTopOrSearch();
            return true;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c2, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c2) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        bar.addView(input, new LinearLayout.LayoutParams(0, Ui.dp(c, 52), 1f));

        TextView close = Ui.text(c, "✕", 18, t.textDim, true);
        close.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
        close.setClickable(true);
        close.setOnClickListener(v -> host.closeAgent());
        bar.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(c);
        results = new LinearLayout(c);
        results.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(c, 12);
        scroll.addView(results, rlp);
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    // ---- matching ----

    private static final class Action {
        final String label, sub;
        final Runnable run;

        Action(String label, String sub, Runnable run) {
            this.label = label;
            this.sub = sub;
            this.run = run;
        }
    }

    private List<Action> builtins(String q) {
        List<Action> out = new ArrayList<>();
        if (matches(q, "install arch", "arch linux", "setup linux", "install linux")) {
            out.add(new Action("Install Arch Linux ARM", "Downloads and sets up a proot container — no root needed",
                    () -> startExternal(InstallerActivity.class)));
        }
        if (matches(q, "terminal", "arch terminal", "shell", "console")) {
            out.add(new Action("Open Arch terminal", "A shell inside the Arch Linux container",
                    () -> startExternal(TerminalActivity.class)));
        }
        if (matches(q, "linux window", "desktop", "linux desktop", "gui", "x11", "run linux app")) {
            out.add(new Action("Open Linux window", "Runs the Arch desktop in a full window with swipe gestures",
                    () -> startExternal(LinuxWindowActivity.class)));
        }
        if (matches(q, "omarchy", "omarchy theme", "flat theme")) {
            out.add(new Action("Switch to Omarchy style", "Flat, square, monospaced dark theme", () -> {
                host.prefs().setStyle(Theme.STYLE_OMARCHY);
                host.settingsChanged();
                host.toast("Omarchy style on");
            }));
        }
        if (matches(q, "rounded theme", "default theme", "classic theme")) {
            out.add(new Action("Switch to Rounded style", "Pocket Shell's original look", () -> {
                host.prefs().setStyle(Theme.STYLE_ROUNDED);
                host.settingsChanged();
                host.toast("Rounded style on");
            }));
        }
        if (matches(q, "dark mode", "dark theme")) {
            out.add(new Action("Dark mode", null, () -> {
                host.prefs().setThemeMode(Prefs.MODE_DARK);
                host.settingsChanged();
            }));
        }
        if (matches(q, "light mode", "light theme")) {
            out.add(new Action("Light mode", null, () -> {
                host.prefs().setThemeMode(Prefs.MODE_LIGHT);
                host.settingsChanged();
            }));
        }
        if (matches(q, "reset layout", "reset home")) {
            out.add(new Action("Reset Home layout", "Clears pages and dock", host::resetLayout));
        }
        if (matches(q, "settings", "launcher settings")) {
            out.add(new Action("Open launcher settings", null, host::openSettings));
        }
        if (matches(q, "wallpaper")) {
            out.add(new Action("Change wallpaper", null, () -> AppMenu.start(host,
                    Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))));
        }
        return out;
    }

    private boolean matches(String q, String... phrases) {
        if (q.isEmpty()) return false;
        for (String p : phrases) {
            if (p.contains(q) || q.contains(p)) return true;
        }
        return false;
    }

    private void filter(String raw) {
        results.removeAllViews();
        final Context c = getContext();
        final Theme t = host.theme();
        String q = raw.trim().toLowerCase(Locale.ROOT);

        if (q.isEmpty()) {
            addHeader("SUGGESTED");
            for (String key : host.prefs().topUsed(4)) {
                AppInfo a = host.apps().get(key);
                if (a != null) addAppRow(a);
            }
            addHeader("TRY ASKING");
            addStatic("install arch", "Set up Arch Linux ARM in a proot container");
            addStatic("terminal", "Open an Arch Linux shell");
            addStatic("linux window", "Run the Arch desktop as a full window");
            return;
        }

        List<Action> actions = builtins(q);
        if (!actions.isEmpty()) {
            addHeader("ACTIONS");
            for (Action a : actions) addActionRow(a);
        }

        List<AppInfo> appMatches = new ArrayList<>();
        for (AppInfo a : host.apps().all()) {
            if (a.matches(q)) appMatches.add(a);
        }
        if (!appMatches.isEmpty()) {
            addHeader("APPS");
            for (AppInfo a : appMatches) addAppRow(a);
        }
        if (actions.isEmpty() && appMatches.isEmpty()) {
            addStatic("No matches", "Nothing found for \"" + raw + "\"");
        }
    }

    private void runTopOrSearch() {
        String q = input.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return;
        List<Action> actions = builtins(q);
        if (!actions.isEmpty()) {
            actions.get(0).run.run();
            return;
        }
        for (AppInfo a : host.apps().all()) {
            if (a.matches(q)) {
                host.launch(a, null);
                return;
            }
        }
        host.toast("No match for \"" + q + "\"");
    }

    private void startExternal(Class<?> activity) {
        host.closeAgent();
        getContext().startActivity(new Intent(getContext(), activity));
    }

    private void addHeader(String s) {
        Context c = getContext();
        Theme t = host.theme();
        TextView h = Ui.text(c, s, 11, t.textDim, true);
        h.setLetterSpacing(0.08f);
        h.setPadding(Ui.dp(c, 4), Ui.dp(c, 14), 0, Ui.dp(c, 6));
        results.addView(h);
    }

    private void addStatic(String title, String sub) {
        Context c = getContext();
        Theme t = host.theme();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8));
        row.addView(Ui.text(c, title, 15, t.text, false));
        row.addView(Ui.text(c, sub, 12, t.textDim, false));
        results.addView(row);
    }

    private void addActionRow(Action a) {
        Context c = getContext();
        Theme t = host.theme();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        row.setBackground(Ui.pressable(c, t.surfaceHigh, t.outline, 14));
        row.setClickable(true);
        row.addView(Ui.text(c, a.label, 15, t.text, true));
        if (a.sub != null) row.addView(Ui.text(c, a.sub, 12, t.textDim, false));
        row.setOnClickListener(v -> a.run.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(c, 8);
        results.addView(row, lp);
    }

    private void addAppRow(final AppInfo a) {
        Context c = getContext();
        Theme t = host.theme();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
        row.setBackground(Ui.pressable(c, 0x00000000, t.outline, 14));
        row.setClickable(true);

        AppCell icon = new AppCell(c, Ui.dp(c, 40), t.text, false, false);
        icon.bind(a, false);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));

        TextView label = Ui.text(c, a.label, 15, t.text, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(c, 12);
        row.addView(label, lp);

        row.setOnClickListener(v -> host.launch(a, icon));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        results.addView(row, rowLp);
    }
}
