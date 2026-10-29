package app.pocketshell.linux;

import android.content.Context;

/**
 * Owns the one running "Linux desktop" session: an Xvfb display, a window manager, an x11vnc
 * server and a websockify bridge so the VNC protocol can reach a WebView over a websocket. Lives
 * as a process-wide singleton so the Terminal and Linux Window activities share the same running
 * Arch environment; it is not a bound Android service, so it is only guaranteed to survive while
 * the app process itself is alive.
 */
public final class LinuxSession {
    private static LinuxSession instance;

    public static synchronized LinuxSession get(Context c) {
        if (instance == null) instance = new LinuxSession(c);
        return instance;
    }

    private final Context ctx;
    private Process shellProcess;
    private Process desktopProcess;
    private boolean desktopStarting;

    private LinuxSession(Context c) {
        ctx = c.getApplicationContext();
    }

    public boolean isDesktopRunning() {
        return LinuxShell.portOpen(LinuxPaths.WS_PORT);
    }

    /** Idempotent: a call while the desktop is already up or starting is a no-op. */
    public synchronized void ensureDesktop(final StatusListener listener) {
        if (isDesktopRunning()) {
            listener.onStatus("Linux desktop already running");
            listener.onReady();
            return;
        }
        if (desktopStarting) {
            listener.onStatus("Linux desktop is starting…");
            return;
        }
        desktopStarting = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    startDesktop(listener);
                } catch (Exception e) {
                    listener.onStatus("Failed to start: " + e.getMessage());
                } finally {
                    desktopStarting = false;
                }
            }
        }, "linux-desktop").start();
    }

    private void startDesktop(StatusListener listener) throws Exception {
        listener.onStatus("Installing desktop packages (first run only)…");
        String setup = String.join(" && ", new String[]{
                "pacman-key --init >/dev/null 2>&1 || true",
                "pacman-key --populate archlinuxarm >/dev/null 2>&1 || true",
                "pacman -Sy --noconfirm --needed xorg-server-xvfb openbox x11vnc python-websockify ttf-dejavu xterm 2>&1",
        });
        Process p = LinuxShell.start(ctx, setup);
        drain(p, listener);
        p.waitFor();

        listener.onStatus("Starting the Linux desktop…");
        String display = ":0";
        String launch = String.join(" && ", new String[]{
                "export DISPLAY=" + display,
                "(Xvfb " + display + " -screen 0 1280x800x24 &)",
                "sleep 2",
                "(openbox &)",
                "(xterm &)",
                "sleep 1",
                "(x11vnc -display " + display + " -nopw -forever -shared -rfbport "
                        + LinuxPaths.VNC_PORT + " &)",
                "sleep 1",
                "websockify " + LinuxPaths.WS_PORT + " 127.0.0.1:" + LinuxPaths.VNC_PORT,
        });
        desktopProcess = LinuxShell.start(ctx, launch);
        drain(desktopProcess, listener);

        for (int i = 0; i < 40 && !isDesktopRunning(); i++) Thread.sleep(500);
        if (isDesktopRunning()) {
            listener.onStatus("Linux desktop ready");
            listener.onReady();
        } else {
            listener.onStatus("The desktop didn't come up in time. Open a terminal to check the log.");
        }
    }

    /** Runs {@code command} in the rootfs and streams its combined stdout/stderr to the listener. */
    public Process runWithOutput(String command, final StatusListener listener) throws Exception {
        Process p = LinuxShell.start(ctx, command);
        drain(p, listener);
        return p;
    }

    private void drain(final Process p, final StatusListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        listener.onStatus(line);
                    }
                } catch (Exception ignored) {
                    // The process ending closes the stream; nothing more to report.
                }
            }
        }, "linux-output").start();
    }

    public interface StatusListener {
        void onStatus(String line);

        default void onReady() {}
    }
}
