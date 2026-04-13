package com.apkforge.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * APK Forge — File Utilities (Java)
 * Handles APK download, extraction, validation and install prep
 */
public final class FileUtils {

    private static final String TAG = "FileUtils";
    private static final int BUFFER_SIZE = 8192;

    private FileUtils() {}

    // ── Directories ────────────────────────────────────────────────────────

    public static File getApkDownloadDir(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "apk-forge");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getTempDir(Context ctx) {
        File dir = new File(ctx.getCacheDir(), "apk-forge-temp");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ── Copy / Extract ─────────────────────────────────────────────────────

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
    }

    /**
     * Extract first APK found inside a ZIP (GitHub artifact is a zip of zip)
     */
    public static File extractApkFromZip(File zipFile, File destDir) throws IOException {
        File apkFile = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                Log.d(TAG, "ZIP entry: " + name);
                if (name.endsWith(".apk") && !entry.isDirectory()) {
                    apkFile = new File(destDir, new File(name).getName());
                    try (FileOutputStream fos = new FileOutputStream(apkFile)) {
                        copyStream(zis, fos);
                    }
                    Log.i(TAG, "Extracted APK: " + apkFile.getAbsolutePath()
                            + " (" + formatSize(apkFile.length()) + ")");
                    break;
                }
                zis.closeEntry();
            }
        }
        return apkFile;
    }

    // ── Validation ─────────────────────────────────────────────────────────

    /**
     * Validate APK by checking ZIP magic bytes (PK\x03\x04)
     * Uses Java fallback (C++ version available in NativeFileOps)
     */
    public static boolean isValidApk(File file) {
        if (file == null || !file.exists()) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            if (fis.read(magic) != 4) return false;
            return magic[0] == 0x50 && magic[1] == 0x4B
                    && magic[2] == 0x03 && magic[3] == 0x04;
        } catch (IOException e) {
            Log.e(TAG, "APK validation error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get MIME type of a file
     */
    public static String getMimeType(File file) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime != null ? mime : "application/octet-stream";
    }

    // ── Formatting ─────────────────────────────────────────────────────────

    public static String formatSize(long bytes) {
        if (bytes < 0) return "Unknown";
        if (bytes < 1024) return bytes + " B";
        DecimalFormat df = new DecimalFormat("0.##");
        if (bytes < 1024 * 1024) return df.format(bytes / 1024.0) + " KB";
        if (bytes < 1024L * 1024 * 1024) return df.format(bytes / (1024.0 * 1024)) + " MB";
        return df.format(bytes / (1024.0 * 1024 * 1024)) + " GB";
    }

    public static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // ── Cleanup ────────────────────────────────────────────────────────────

    public static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        boolean deleted = file.delete();
        Log.d(TAG, "Deleted " + file.getPath() + ": " + deleted);
    }

    public static long getFolderSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                size += f.isDirectory() ? getFolderSize(f) : f.length();
            }
        }
        return size;
    }
}
