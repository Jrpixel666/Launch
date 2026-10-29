package app.pocketshell.linux;

import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * A minimal, dependency-free ustar/GNU tar reader, enough to unpack the Arch Linux ARM rootfs.
 * Android ships no tar binary it is safe to rely on across devices, and pulling in a library just
 * for this would be the only external dependency in the whole app.
 */
final class TarExtractor {
    interface Progress {
        void onEntry(String name, long doneBytes);
    }

    private TarExtractor() {}

    /** Extracts a gzip-compressed tar stream into {@code destDir}, which must already exist. */
    static void extractTarGz(InputStream rawIn, File destDir, Progress progress) throws IOException {
        long bytesDone = 0;
        try (GZIPInputStream gz = new GZIPInputStream(rawIn, 1 << 16)) {
            byte[] header = new byte[512];
            Map<String, String> pendingLongName = new HashMap<>();
            while (true) {
                int read = readFully(gz, header);
                if (read == 0) break; // clean EOF
                if (read < 512) break;
                if (isZeroBlock(header)) {
                    // Two zero blocks in a row mark the end of the archive; one alone is padding.
                    continue;
                }
                String name = cString(header, 0, 100);
                long size = octal(header, 124, 12);
                int typeFlag = header[156];
                String linkName = cString(header, 157, 100);
                String prefix = cString(header, 345, 155);
                if (!prefix.isEmpty()) name = prefix + "/" + name;

                if (typeFlag == 'L') { // GNU long-name entry: its body is the real name
                    byte[] data = new byte[(int) size];
                    readFully(gz, data);
                    skipPadding(gz, size);
                    pendingLongName.put("name", new String(data, StandardCharsets.UTF_8).trim());
                    continue;
                }
                if (name.isEmpty()) continue;
                String realName = pendingLongName.remove("name");
                if (realName != null) name = realName;

                File out = safeChild(destDir, name);
                if (out == null) { // path escapes destDir via ".." — skip, never follow it
                    skipPadding(gz, size);
                    continue;
                }

                switch (typeFlag) {
                    case '5': // directory
                        out.mkdirs();
                        break;
                    case '2': // symlink
                        out.getParentFile().mkdirs();
                        try {
                            Os.symlink(linkName, out.getAbsolutePath());
                        } catch (Exception ignored) {
                            // A broken or duplicate symlink is not fatal to the whole install.
                        }
                        break;
                    case '1': // hard link — best effort: copy nothing, skip (rare in this rootfs)
                        break;
                    default: // regular file ('0' or '\0')
                        out.getParentFile().mkdirs();
                        try (OutputStream os = new FileOutputStream(out)) {
                            copyExact(gz, os, size);
                        }
                        applyMode(out, header);
                        skipPadding(gz, size);
                        progress.onEntry(name, bytesDone += size);
                        continue;
                }
                skipPadding(gz, size);
                progress.onEntry(name, bytesDone += size);
            }
        }
    }

    private static boolean isZeroBlock(byte[] b) {
        for (byte v : b) if (v != 0) return false;
        return true;
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    private static void copyExact(InputStream in, OutputStream out, long size) throws IOException {
        byte[] buf = new byte[1 << 16];
        long remaining = size;
        while (remaining > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (n < 0) throw new IOException("Unexpected end of archive");
            out.write(buf, 0, n);
            remaining -= n;
        }
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        long pad = (512 - (size % 512)) % 512;
        while (pad > 0) {
            long skipped = in.skip(pad);
            if (skipped <= 0) {
                if (in.read() < 0) break;
                skipped = 1;
            }
            pad -= skipped;
        }
    }

    private static String cString(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static long octal(byte[] b, int off, int len) {
        long v = 0;
        for (int i = off; i < off + len; i++) {
            byte c = b[i];
            if (c < '0' || c > '7') continue;
            v = v * 8 + (c - '0');
        }
        return v;
    }

    private static void applyMode(File f, byte[] header) {
        long mode = octal(header, 100, 8);
        try {
            Os.chmod(f.getAbsolutePath(), (int) (mode & 0777));
        } catch (Exception ignored) {
            // Falling back to the extracted file's default mode is fine for most entries.
        }
    }

    /** Resolves {@code name} under {@code base}, refusing to escape it via a ".." path segment. */
    private static File safeChild(File base, String name) {
        File f = new File(base, name);
        try {
            String basePath = base.getCanonicalPath() + File.separator;
            String childPath = f.getCanonicalPath();
            if (!(childPath + File.separator).startsWith(basePath) && !childPath.equals(base.getCanonicalPath())) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        return f;
    }
}
