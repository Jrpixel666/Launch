package app.pocketshell.linux;

import android.content.Context;
import android.system.Os;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Downloads a static {@code proot} binary and the Arch Linux ARM base rootfs, unpacks them into
 * the app's private storage, and writes the helper scripts {@link LinuxShell} runs. No root is
 * needed: proot intercepts syscalls so the rootfs believes it is a real root filesystem.
 */
public final class ProotInstaller {
    public interface Listener {
        /** {@code step} is a short label ("Downloading proot", "Extracting Arch Linux ARM", ...). */
        void onProgress(String step, long done, long total);

        void onDone();

        void onError(String message);
    }

    private final Context ctx;

    public ProotInstaller(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** Runs the whole install on a background thread; callbacks land on that same thread. */
    public void install(final Listener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runInstall(listener);
                    listener.onDone();
                } catch (Exception e) {
                    listener.onError(e.getMessage() == null ? e.toString() : e.getMessage());
                }
            }
        }, "arch-install").start();
    }

    private void runInstall(Listener listener) throws IOException {
        File root = LinuxPaths.rootDir(ctx);
        File rootfs = LinuxPaths.rootfsDir(ctx);
        File binDir = LinuxPaths.binDir(ctx);
        rootfs.mkdirs();
        binDir.mkdirs();

        downloadTo(LinuxPaths.PROOT_BASE_URL + LinuxPaths.prootAssetName(),
                LinuxPaths.prootBinary(ctx), "Downloading proot", listener);
        try {
            Os.chmod(LinuxPaths.prootBinary(ctx).getAbsolutePath(), 0755);
        } catch (Exception e) {
            throw new IOException("Couldn't make proot executable: " + e.getMessage());
        }

        listener.onProgress("Downloading Arch Linux ARM", 0, 0);
        URL url = new URL(LinuxPaths.ROOTFS_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(20000);
        long total = conn.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
            listener.onProgress("Extracting Arch Linux ARM", 0, total);
            final long totalFinal = total;
            TarExtractor.extractTarGz(in, rootfs, new TarExtractor.Progress() {
                @Override
                public void onEntry(String name, long doneBytes) {
                    listener.onProgress("Extracting Arch Linux ARM", doneBytes, totalFinal);
                }
            });
        } finally {
            conn.disconnect();
        }

        // DNS inside the rootfs: Android has no /etc/resolv.conf proot can bind to by default.
        writeFile(new File(rootfs, "etc/resolv.conf"), "nameserver 8.8.8.8\nnameserver 1.1.1.1\n");
        File marker = LinuxPaths.marker(ctx);
        writeFile(marker, String.valueOf(System.currentTimeMillis()));
    }

    private void downloadTo(String urlStr, File dest, String step, Listener listener) throws IOException {
        listener.onProgress(step, 0, 0);
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(20000);
        long total = conn.getContentLengthLong();
        long done = 0;
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                done += n;
                listener.onProgress(step, done, total);
            }
        } finally {
            conn.disconnect();
        }
    }

    private void writeFile(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
