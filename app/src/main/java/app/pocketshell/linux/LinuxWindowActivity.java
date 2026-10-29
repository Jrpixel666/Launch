package app.pocketshell.linux;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Shows a running Linux GUI session as a full, resizable window: a fullscreen WebView running
 * noVNC (loaded from a CDN at runtime — this device, not the build, fetches it) connected over a
 * websocket to the x11vnc/websockify bridge {@link LinuxSession} starts inside the Arch rootfs.
 * A swipe down from the top edge minimizes back to Pocket Shell; pinch-to-zoom is the WebView's
 * own built-in zoom, which maps directly onto the remote X session.
 */
public final class LinuxWindowActivity extends Activity {
    private TextView status;
    private WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        if (!LinuxPaths.isInstalled(this)) {
            TextView t = new TextView(this);
            t.setText("Arch Linux isn't installed yet. Use the installer first.");
            t.setTextColor(Color.WHITE);
            t.setBackgroundColor(Color.BLACK);
            t.setPadding(dp(20), dp(40), dp(20), dp(20));
            setContentView(t);
            return;
        }

        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setPadding(dp(20), dp(40), dp(20), dp(20));
        status.setText("Starting the Linux desktop…");
        root.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        web.setVisibility(View.GONE);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final GestureDetector gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null) return false;
                boolean fromTopEdge = e1.getY() < dp(48);
                boolean swipeDown = e2.getY() - e1.getY() > dp(80) && vy > 0;
                if (fromTopEdge && swipeDown) {
                    moveTaskToBack(true);
                    return true;
                }
                return false;
            }
        });
        root.setOnTouchListener((v, ev) -> {
            gestures.onTouchEvent(ev);
            return false; // never swallow: the remote session still needs every touch
        });

        setContentView(root);
        LinuxSession.get(this).ensureDesktop(new LinuxSession.StatusListener() {
            @Override
            public void onStatus(String line) {
                main.post(() -> status.setText(line));
            }

            @Override
            public void onReady() {
                main.post(() -> {
                    String html = "<html><body style='margin:0;background:#000'>"
                            + "<div id=screen style='width:100%;height:100%'></div>"
                            + "<script src='https://cdn.jsdelivr.net/npm/@novnc/novnc@1.4.0/lib/rfb.js' type='module'></script>"
                            + "<script type='module'>"
                            + "import RFB from 'https://cdn.jsdelivr.net/npm/@novnc/novnc@1.4.0/lib/rfb.js';"
                            + "const rfb = new RFB(document.getElementById('screen'), "
                            + "'ws://127.0.0.1:" + LinuxPaths.WS_PORT + "/');"
                            + "rfb.scaleViewport = true; rfb.resizeSession = true;"
                            + "</script></body></html>";
                    web.loadDataWithBaseURL("https://localhost/", html, "text/html", "utf-8", null);
                    web.setVisibility(View.VISIBLE);
                    status.setText("");
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
