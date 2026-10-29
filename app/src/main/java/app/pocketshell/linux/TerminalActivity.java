package app.pocketshell.linux;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * A plain line-buffered terminal into the Arch rootfs: not a real VT100 (no PTY is available
 * without a native helper), but enough to run pacman, shell scripts and other line-oriented tools.
 */
public final class TerminalActivity extends Activity {
    private TextView output;
    private ScrollView scroll;
    private Process shell;
    private OutputStream stdin;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final StringBuilder buffer = new StringBuilder();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!LinuxPaths.isInstalled(this)) {
            TextView t = new TextView(this);
            t.setText("Arch Linux isn't installed yet. Use the installer first.");
            t.setTextColor(Color.WHITE);
            t.setPadding(dp(20), dp(40), dp(20), dp(20));
            setContentView(t);
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0B10);

        scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextColor(0xFF9ECE6A);
        output.setTextSize(12);
        output.setPadding(dp(10), dp(10), dp(10), dp(10));
        scroll.addView(output, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final EditText input = new EditText(this);
        input.setHint("command");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF8A8A9A);
        input.setBackgroundColor(0xFF1A1A24);
        input.setTypeface(Typeface.MONOSPACE);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        input.setSingleLine(true);
        input.setPadding(dp(10), dp(10), dp(10), dp(10));
        input.setOnEditorActionListener((v, actionId, event) -> {
            send(input.getText().toString());
            input.setText("");
            return true;
        });
        root.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        startShell();
    }

    private void startShell() {
        new Thread(() -> {
            try {
                ProcessBuilder pb = LinuxShell.builder(this, "exec /bin/sh -l");
                shell = pb.start();
                stdin = shell.getOutputStream();
                appendLine("Connected to Arch Linux (proot). Type a command and press Enter.\n");
                BufferedReader r = new BufferedReader(new InputStreamReader(
                        shell.getInputStream(), StandardCharsets.UTF_8));
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) >= 0) {
                    appendLine(new String(buf, 0, n));
                }
            } catch (IOException e) {
                appendLine("\nFailed to start: " + e.getMessage() + "\n");
            }
        }, "terminal-shell").start();
    }

    private void send(String command) {
        appendLine("$ " + command + "\n");
        if (stdin == null) return;
        new Thread(() -> {
            try {
                stdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            } catch (IOException ignored) {
                // A dead shell just stops echoing output; nothing to recover here.
            }
        }, "terminal-write").start();
    }

    private void appendLine(String s) {
        buffer.append(s);
        if (buffer.length() > 200_000) buffer.delete(0, buffer.length() - 200_000);
        main.post(() -> {
            output.setText(buffer.toString());
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    @Override
    protected void onDestroy() {
        if (shell != null) shell.destroy();
        super.onDestroy();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
