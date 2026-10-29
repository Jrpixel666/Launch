package app.pocketshell.linux;

import android.content.Context;
import android.os.Build;

import java.io.File;

/**
 * Where the Arch Linux ARM rootfs and its {@code proot} launcher live, and how to reach them.
 * Everything lives under the app's private files dir, so it is wiped with the app and needs no
 * extra storage permission.
 */
public final class LinuxPaths {
    private LinuxPaths() {}

    /** Official Arch Linux ARM base rootfs for 64-bit ARM devices. */
    public static final String ROOTFS_URL = "http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz";

    /**
     * Static, pre-built {@code proot} binaries for Android, published by the proot-static-build
     * project. proot itself ships no Android/bionic build, so a prebuilt one is required to run a
     * foreign rootfs without root.
     */
    public static final String PROOT_BASE_URL =
            "https://github.com/green-green-avk/proot-static-build/releases/latest/download/";

    public static File rootDir(Context c) {
        return new File(c.getFilesDir(), "linux");
    }

    public static File rootfsDir(Context c) {
        return new File(rootDir(c), "arch");
    }

    public static File binDir(Context c) {
        return new File(rootDir(c), "bin");
    }

    public static File prootBinary(Context c) {
        return new File(binDir(c), "proot");
    }

    public static File marker(Context c) {
        return new File(rootDir(c), ".installed");
    }

    public static boolean isInstalled(Context c) {
        return marker(c).exists() && prootBinary(c).exists() && new File(rootfsDir(c), "etc").isDirectory();
    }

    /** proot's asset naming is {@code proot-<abi>} e.g. {@code proot-aarch64}, {@code proot-arm}. */
    public static String prootAssetName() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) return "proot-aarch64";
            if ("armeabi-v7a".equals(abi)) return "proot-arm";
            if ("x86_64".equals(abi)) return "proot-x86_64";
            if ("x86".equals(abi)) return "proot-x86";
        }
        return "proot-aarch64";
    }

    /** Local VNC port the in-rootfs desktop session listens on once started. */
    public static final int VNC_PORT = 5950;
    /** Local websocket port a websockify bridge exposes for the WebView-based noVNC client. */
    public static final int WS_PORT = 5951;
}
