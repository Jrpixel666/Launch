package app.pocketshell.linux;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds and launches {@code proot} commands against the installed Arch Linux ARM rootfs.
 * Everything here talks to {@link ProcessBuilder}; there is no native PTY (that needs a JNI
 * helper this project doesn't bundle), so interactive line-buffered shells work but full-screen
 * terminal apps that need real termios control will not.
 */
public final class LinuxShell {
    private LinuxShell() {}

    /** Starts {@code command} (run through {@code /bin/sh -lc}) inside the Arch rootfs. */
    public static Process start(Context c, String command) throws java.io.IOException {
        return builder(c, command).start();
    }

    public static ProcessBuilder builder(Context c, String command) {
        File rootfs = LinuxPaths.rootfsDir(c);
        File tmp = new File(LinuxPaths.rootDir(c), "proot-tmp");
        tmp.mkdirs();

        List<String> argv = new ArrayList<>();
        argv.add(LinuxPaths.prootBinary(c).getAbsolutePath());
        argv.add("-0"); // fake root inside the sandbox
        argv.add("-r");
        argv.add(rootfs.getAbsolutePath());
        argv.add("-w");
        argv.add("/root");
        for (String path : new String[]{"/dev", "/proc", "/sys"}) {
            argv.add("-b");
            argv.add(path);
        }
        argv.add("/bin/sh");
        argv.add("-lc");
        argv.add(command);

        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectErrorStream(true);
        pb.environment().put("PROOT_TMP_DIR", tmp.getAbsolutePath());
        pb.environment().put("PROOT_NO_SECCOMP", "1");
        pb.environment().put("HOME", "/root");
        pb.environment().put("TERM", "xterm-256color");
        pb.environment().put("LD_LIBRARY_PATH", LinuxPaths.binDir(c).getAbsolutePath());
        return pb;
    }

    /** True once a GUI session's VNC server is listening on {@link LinuxPaths#VNC_PORT}. */
    public static boolean portOpen(int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
