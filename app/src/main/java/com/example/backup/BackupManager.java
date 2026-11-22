package com.example.backup;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.example.db.DatabaseHelper;
import com.example.util.DateUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BackupManager {

    public static final String BACKUP_FOLDER_NAME = "Gam3yaBackup";
    public static final String LATEST_BACKUP_FILE_NAME = "gam3ya_latest.json";
    public static final String LEGACY_BACKUP_FILE_NAME = "gam3ya_backup.json";

    private static final String PREFS_NAME = "gam3ya_prefs";
    private static final String KEY_LAST_BACKUP_TIME = "last_backup_time";
    private static final String KEY_INITIAL_RESTORE_PROMPTED = "initial_restore_prompted";

    public static class BackupEntry {
        public String fileName;
        public String metaLabel;
        public boolean isLatest;
        public Uri mediaStoreUri;
        public File legacyFile;
    }

    private BackupManager() {
    }

    public static File getBackupFolder() {
        File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        return new File(docsDir, BACKUP_FOLDER_NAME);
    }

    public static String getLastBackupTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_BACKUP_TIME, null);
    }

    public static boolean wasInitialRestorePrompted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_INITIAL_RESTORE_PROMPTED, false);
    }

    public static void markInitialRestorePrompted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_INITIAL_RESTORE_PROMPTED, true).apply();
    }

    /**
     * Manual backup: saves both gam3ya_latest.json AND a timestamped snapshot
     * gam3ya_backup_yyyy-MM-dd_HH-mm.json inside Documents/Gam3yaBackup.
     */
    public static boolean backupNow(Context context, DatabaseHelper dbHelper) {
        try {
            String json = dbHelper.exportDatabaseToJson();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            boolean latestOk = writeBackupFile(context, LATEST_BACKUP_FILE_NAME, bytes, true);
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date());
            String datedName = "gam3ya_backup_" + stamp + ".json";
            boolean datedOk = writeBackupFile(context, datedName, bytes, true);

            if (latestOk || datedOk) {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putString(KEY_LAST_BACKUP_TIME, DateUtils.getNowDateTimeIso()).apply();
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Silent automatic backup of gam3ya_latest.json before destructive or archival events.
     */
    public static void backupLatestSilently(Context context, DatabaseHelper dbHelper) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            String json = dbHelper.exportDatabaseToJson();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            if (writeBackupFile(context, LATEST_BACKUP_FILE_NAME, bytes, true)) {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putString(KEY_LAST_BACKUP_TIME, DateUtils.getNowDateTimeIso()).apply();
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean writeBackupFile(
            Context context,
            String fileName,
            byte[] bytes,
            boolean replaceExisting
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = context.getContentResolver();
                Uri collection = MediaStore.Files.getContentUri("external");
                String relativePath = Environment.DIRECTORY_DOCUMENTS + "/" + BACKUP_FOLDER_NAME;

                if (replaceExisting) {
                    resolver.delete(
                            collection,
                            MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND " +
                                    MediaStore.MediaColumns.DISPLAY_NAME + " = ?",
                            new String[]{"%" + BACKUP_FOLDER_NAME + "%", fileName}
                    );
                }

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);

                Uri itemUri = resolver.insert(collection, values);
                if (itemUri == null) {
                    return false;
                }
                try (OutputStream os = resolver.openOutputStream(itemUri, "w")) {
                    if (os == null) {
                        return false;
                    }
                    os.write(bytes);
                    os.flush();
                }
                return true;
            } else {
                File folder = getBackupFolder();
                if (!folder.exists() && !folder.mkdirs()) {
                    return false;
                }
                File target = new File(folder, fileName);
                try (FileOutputStream fos = new FileOutputStream(target, false)) {
                    fos.write(bytes);
                    fos.flush();
                }
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks gam3ya_latest.json first (and falls back to legacy gam3ya_backup.json) for startup auto-restore.
     */
    public static String findDirectBackupJson(Context context) {
        String latest = readSpecificFile(context, LATEST_BACKUP_FILE_NAME);
        if (latest != null) {
            return latest;
        }
        return readSpecificFile(context, LEGACY_BACKUP_FILE_NAME);
    }

    private static String readSpecificFile(Context context, String fileName) {
        try {
            File legacyFile = new File(getBackupFolder(), fileName);
            if (legacyFile.exists() && legacyFile.canRead()) {
                try (FileInputStream fis = new FileInputStream(legacyFile)) {
                    String content = readStreamToString(fis);
                    if (content != null && !content.trim().isEmpty()) {
                        return content;
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = context.getContentResolver();
                Uri collection = MediaStore.Files.getContentUri("external");
                String[] projection = new String[]{MediaStore.MediaColumns._ID};
                String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND " +
                        MediaStore.MediaColumns.DISPLAY_NAME + " = ?";
                String[] args = new String[]{"%" + BACKUP_FOLDER_NAME + "%", fileName};

                try (Cursor cursor = resolver.query(collection, projection, selection, args,
                        MediaStore.MediaColumns.DATE_MODIFIED + " DESC")) {
                    if (cursor != null && cursor.moveToFirst()) {
                        long id = cursor.getLong(0);
                        Uri uri = ContentUris.withAppendedId(collection, id);
                        try (InputStream is = resolver.openInputStream(uri)) {
                            if (is != null) {
                                String content = readStreamToString(is);
                                if (content != null && !content.trim().isEmpty()) {
                                    return content;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static List<BackupEntry> listAvailableBackups(Context context) {
        List<BackupEntry> entries = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        // 1. Query MediaStore on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentResolver resolver = context.getContentResolver();
                Uri collection = MediaStore.Files.getContentUri("external");
                String[] projection = new String[]{
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.DATE_MODIFIED
                };
                String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND " +
                        MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?";
                String[] args = new String[]{"%" + BACKUP_FOLDER_NAME + "%", "%.json"};

                try (Cursor cursor = resolver.query(collection, projection, selection, args,
                        MediaStore.MediaColumns.DATE_MODIFIED + " DESC")) {
                    if (cursor != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(0);
                            String name = cursor.getString(1);
                            long modSec = cursor.getLong(2);
                            if (name != null && !seenNames.contains(name)) {
                                seenNames.add(name);
                                BackupEntry e = new BackupEntry();
                                e.fileName = name;
                                e.isLatest = LATEST_BACKUP_FILE_NAME.equals(name);
                                e.mediaStoreUri = ContentUris.withAppendedId(collection, id);
                                String dateStr = modSec > 0 ? sdf.format(new Date(modSec * 1000L)) : "";
                                e.metaLabel = e.isLatest
                                        ? "أحدث نسخة تلقائية • " + dateStr
                                        : "نسخة مؤرخة • " + dateStr;
                                entries.add(e);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Also scan direct Documents/Gam3yaBackup folder
        try {
            File folder = getBackupFolder();
            if (folder.exists() && folder.isDirectory()) {
                File[] files = folder.listFiles();
                if (files != null) {
                    Arrays.sort(files, new Comparator<File>() {
                        @Override
                        public int compare(File a, File b) {
                            return Long.compare(b.lastModified(), a.lastModified());
                        }
                    });
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                    for (File f : files) {
                        if (f.isFile() && f.getName().endsWith(".json") && !seenNames.contains(f.getName())) {
                            seenNames.add(f.getName());
                            BackupEntry e = new BackupEntry();
                            e.fileName = f.getName();
                            e.isLatest = LATEST_BACKUP_FILE_NAME.equals(f.getName());
                            e.legacyFile = f;
                            String dateStr = sdf.format(new Date(f.lastModified()));
                            e.metaLabel = e.isLatest
                                    ? "أحدث نسخة تلقائية • " + dateStr
                                    : "نسخة مؤرخة • " + dateStr;
                            entries.add(e);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return entries;
    }

    public static boolean restoreFromBackupEntry(Context context, BackupEntry entry, DatabaseHelper dbHelper) {
        if (entry == null) {
            return false;
        }
        if (entry.mediaStoreUri != null) {
            return restoreFromUri(context, entry.mediaStoreUri, dbHelper);
        }
        if (entry.legacyFile != null && entry.legacyFile.exists()) {
            try (FileInputStream fis = new FileInputStream(entry.legacyFile)) {
                String json = readStreamToString(fis);
                if (json != null && !json.trim().isEmpty()) {
                    dbHelper.restoreDatabaseFromJson(json);
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean restoreFromJsonString(DatabaseHelper dbHelper, String json) {
        try {
            dbHelper.restoreDatabaseFromJson(json);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean restoreFromUri(Context context, Uri uri, DatabaseHelper dbHelper) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return false;
            }
            String json = readStreamToString(is);
            if (json == null || json.trim().isEmpty()) {
                return false;
            }
            dbHelper.restoreDatabaseFromJson(json);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String readStreamToString(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
