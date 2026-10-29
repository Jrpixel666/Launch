package app.pocketshell.linux;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** Downloads proot and the Arch Linux ARM rootfs, with a log and a progress bar. */
public final class InstallerActivity extends Activity {
    private TextView status;
    private TextView log;
    private ProgressBar bar;
    private Button action;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final StringBuilder logText = new StringBuilder();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF11111B);
        int pad = dp(20);
        root.setPadding(pad, pad + dp(16), pad, pad);

        TextView title = text("Arch Linux ARM", 22, true);
        root.addView(title);
        TextView sub = text("Installed without root, via proot. ~600 MB download.", 13, false);
        sub.setTextColor(0xFF8A8A9A);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        status = text(LinuxPaths.isInstalled(this) ? "Already installed." : "Not installed.", 15, false);
        root.addView(status);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        barLp.topMargin = dp(12);
        barLp.bottomMargin = dp(16);
        root.addView(bar, barLp);

        action = new Button(this);
        action.setText(LinuxPaths.isInstalled(this) ? "Reinstall" : "Start installation");
        action.setOnClickListener(v -> startInstall());
        root.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        log = text("", 11, false);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextColor(0xFF8A8A9A);
        scroll.addView(log, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button close = new Button(this);
        close.setText("Close");
        close.setOnClickListener(v -> finish());
        root.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void startInstall() {
        action.setEnabled(false);
        appendLog("Starting install…");
        new ProotInstaller(this).install(new ProotInstaller.Listener() {
            @Override
            public void onProgress(final String step, final long done, final long total) {
                main.post(() -> {
                    status.setText(step);
                    if (total > 0) {
                        bar.setMax(1000);
                        bar.setProgress((int) (1000L * done / total));
                        appendLog(step + ": " + (done / 1024 / 1024) + " / " + (total / 1024 / 1024) + " MB");
                    } else {
                        appendLog(step + "…");
                    }
                });
            }

            @Override
            public void onDone() {
                main.post(() -> {
                    status.setText("Installed. Open a terminal or the Linux window from Pocket Shell's Agent bar.");
                    bar.setProgress(1000);
                    action.setText("Reinstall");
                    action.setEnabled(true);
                    appendLog("Done.");
                });
            }

            @Override
            public void onError(final String message) {
                main.post(() -> {
                    status.setText("Failed: " + message);
                    action.setEnabled(true);
                    appendLog("ERROR: " + message);
                });
            }
        });
    }

    private void appendLog(String line) {
        logText.append(line).append('\n');
        if (log != null) main.post(() -> log.setText(logText.toString()));
    }

    private TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.START);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
