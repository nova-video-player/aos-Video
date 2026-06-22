// Copyright 2025 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.video.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediaprovider.VideoDb;
import com.archos.mediaprovider.video.VideoOpenHelper;
import com.archos.mediascraper.MediaScraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MediaLibraryBackupService extends Service {
    private static final Logger log = LoggerFactory.getLogger(MediaLibraryBackupService.class);

    public static final String ACTION_EXPORT = "export_library";
    public static final String ACTION_IMPORT = "import_library";
    public static final String EXTRA_IMPORT_FILE = "import_file";

    private static final int NOTIFICATION_ID = 7;
    private static final String NOTIF_CHANNEL_ID = "MediaLibraryBackup_id";
    private static final String NOTIF_CHANNEL_NAME = "Media Library Backup";
    private static final String NOTIF_CHANNEL_DESCR = "Media Library Backup Service";

    private static final String DATABASE_NAME = "media.db";
    private static final String CREDENTIALS_DB_NAME = "credentials_db";
    private static final String SHORTCUTS_DB_NAME = "shortcuts_db";
    private static final String SHORTCUTS2_DB_NAME = "shortcuts2_db";
    private static final String VERSION_FILE = "db_version.txt";
    private static final String BACKUP_FILENAME = "backup.zip";
    private static final int BUFFER_SIZE = 8192;

    private NotificationManager nm;
    private NotificationCompat.Builder nb;
    private Thread mThread;

    @Override
    public void onCreate() {
        super.onCreate();
        if (log.isDebugEnabled()) log.debug("onCreate");

        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(NOTIF_CHANNEL_ID, NOTIF_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW);
            nc.setDescription(NOTIF_CHANNEL_DESCR);
            nc.setSound(null, null);
            nc.enableLights(false);
            nc.enableVibration(false);
            nm.createNotificationChannel(nc);
        }

        nb = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.drawable.nova_notification)
                .setContentTitle(getString(R.string.media_library_export_in_progress))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (log.isDebugEnabled()) log.debug("onStartCommand: intent={}", intent);

        //startForeground(NOTIFICATION_ID, nb.build());
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_EXPORT.equals(action)) {
                startExport();
            } else if (ACTION_IMPORT.equals(action)) {
                String importFile = intent.getStringExtra(EXTRA_IMPORT_FILE);
                startImport(importFile);
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startExport() {
        if (mThread != null && mThread.isAlive()) {
            log.warn("startExport: export already in progress");
            return;
        }

        mThread = new Thread(() -> {
            try {
                nb.setContentTitle(getString(R.string.media_library_export_in_progress));
                nm.notify(NOTIFICATION_ID, nb.build());

                String exportPath = exportMediaLibrary();

                showToast(getString(R.string.media_library_export_success, exportPath));
                nm.cancel(NOTIFICATION_ID);
            } catch (Exception e) {
                log.error("startExport: error exporting media library", e);
                showToast(getString(R.string.media_library_export_error));
            } finally {
                ServiceCompat.stopForeground(MediaLibraryBackupService.this, ServiceCompat.STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        });
        mThread.start();
    }

    private void startImport(String importFilePath) {
        if (mThread != null && mThread.isAlive()) {
            log.warn("startImport: import already in progress");
            return;
        }

        mThread = new Thread(() -> {
            try {
                nb.setContentTitle(getString(R.string.media_library_import_in_progress));
                nm.notify(NOTIFICATION_ID, nb.build());

                importMediaLibrary(importFilePath);

                nm.cancel(NOTIFICATION_ID);
                showToast(getString(R.string.media_library_import_success));

                // Wait 2 seconds for user to see success message, then restart app
                if (log.isDebugEnabled()) log.debug("startImport: waiting 2 seconds before restarting app");
                Thread.sleep(2000);

                restartApplication();
            } catch (Exception e) {
                log.error("startImport: error importing media library", e);
                showToast(getString(R.string.media_library_import_error));
            } finally {
                ServiceCompat.stopForeground(MediaLibraryBackupService.this, ServiceCompat.STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        });
        mThread.start();
    }

    private String exportMediaLibrary() throws IOException {
        if (log.isDebugEnabled()) log.debug("exportMediaLibrary: starting full library export");

        // Get export directory
        File exportDir = getExternalFilesDir(null);
        if (exportDir == null) {
            throw new IOException("External storage not available");
        }

        // Flush database WAL to ensure consistency
        if (log.isDebugEnabled()) log.debug("exportMediaLibrary: flushing database WAL");
        flushDatabaseWAL();

        // Create export zip file (fixed name, will overwrite previous backup)
        File zipFile = new File(exportDir, BACKUP_FILENAME);

        // Delete old backup if it exists
        if (zipFile.exists()) {
            if (log.isDebugEnabled()) log.debug("exportMediaLibrary: deleting existing backup file: {}", zipFile.getAbsolutePath());
            if (!zipFile.delete()) {
                log.warn("exportMediaLibrary: failed to delete existing backup file");
            }
        }

        if (log.isDebugEnabled()) log.debug("exportMediaLibrary: creating new backup file: {}", zipFile.getAbsolutePath());

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // Export database version first
            int dbVersion = VideoOpenHelper.getDatabaseVersion();
            if (log.isDebugEnabled()) log.debug("exportMediaLibrary: adding database version={}", dbVersion);
            addVersionToZip(zos, dbVersion);

            // Export media database
            File dbFile = getDatabasePath(DATABASE_NAME);
            if (dbFile.exists()) {
                if (log.isDebugEnabled()) log.debug("exportMediaLibrary: exporting media database");
                addFileToZip(zos, dbFile, DATABASE_NAME);
            } else {
                log.warn("exportMediaLibrary: media database file not found: {}", dbFile.getAbsolutePath());
            }

            // Export credentials database (SMB/FTP/SFTP/WebDAV credentials)
            File credentialsDbFile = getDatabasePath(CREDENTIALS_DB_NAME);
            if (credentialsDbFile.exists()) {
                if (log.isDebugEnabled()) log.debug("exportMediaLibrary: exporting credentials database");
                addFileToZip(zos, credentialsDbFile, CREDENTIALS_DB_NAME);
            } else {
                if (log.isDebugEnabled()) log.debug("exportMediaLibrary: credentials database not found (no saved credentials)");
            }

            // Export shortcuts database (network shortcuts/bookmarks)
            File shortcutsDbFile = getDatabasePath(SHORTCUTS_DB_NAME);
            if (shortcutsDbFile.exists()) {
                if (log.isDebugEnabled()) log.debug("exportMediaLibrary: exporting shortcuts database");
                addFileToZip(zos, shortcutsDbFile, SHORTCUTS_DB_NAME);
            } else {
                if (log.isDebugEnabled()) log.debug("exportMediaLibrary: shortcuts database not found (no saved shortcuts)");
            }

            // Export shortcuts2 database (newer network shortcuts/bookmarks)
            File shortcuts2DbFile = getDatabasePath(SHORTCUTS2_DB_NAME);
            if (shortcuts2DbFile.exists()) {
                if (log.isDebugEnabled()) log.debug("exportMediaLibrary: exporting shortcuts2 database");
                addFileToZip(zos, shortcuts2DbFile, SHORTCUTS2_DB_NAME);
            } else {
                if (log.isDebugEnabled()) log.debug("exportMediaLibrary: shortcuts2 database not found (no saved shortcuts)");
            }

            // Export poster directory
            File posterDir = MediaScraper.getPosterDirectory(this);
            if (posterDir.exists()) {
                addDirectoryToZip(zos, posterDir, "scraper_posters");
            }

            // Export backdrop directory
            File backdropDir = MediaScraper.getBackdropDirectory(this);
            if (backdropDir.exists()) {
                addDirectoryToZip(zos, backdropDir, "scraper_backdrops");
            }

            // Export picture directory
            File pictureDir = MediaScraper.getPictureDirectory(this);
            if (pictureDir.exists()) {
                addDirectoryToZip(zos, pictureDir, "scraper_pictures");
            }
        }

        if (log.isDebugEnabled()) log.debug("exportMediaLibrary: export completed to {}", zipFile.getAbsolutePath());
        return zipFile.getAbsolutePath();
    }

    private void importMediaLibrary(String importFilePath) throws IOException {
        if (log.isDebugEnabled()) log.debug("importMediaLibrary: starting FULL REPLACEMENT import from {}", importFilePath);

        if (importFilePath == null || importFilePath.isEmpty()) {
            throw new IOException("Import file path is null or empty");
        }

        File zipFile = new File(importFilePath);
        if (!zipFile.exists()) {
            throw new IOException("Import file not found: " + importFilePath);
        }

        // STEP 1: Check database version compatibility
        if (log.isDebugEnabled()) log.debug("importMediaLibrary: checking database version compatibility");
        int backupDbVersion = -1;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(VERSION_FILE)) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                    String versionStr = reader.readLine();
                    try {
                        backupDbVersion = Integer.parseInt(versionStr.trim());
                    } catch (NumberFormatException e) {
                        log.error("importMediaLibrary: invalid version format: {}", versionStr, e);
                    }
                    break;
                }
                zis.closeEntry();
            }
        }

        int currentDbVersion = VideoOpenHelper.getDatabaseVersion();
        if (log.isDebugEnabled()) log.debug("importMediaLibrary: backup DB version={}, current DB version={}", backupDbVersion, currentDbVersion);

        if (backupDbVersion == -1) {
            throw new IOException("Backup file does not contain version information");
        }

        if (backupDbVersion != currentDbVersion) {
            throw new IOException("Incompatible database version. Backup version: " + backupDbVersion +
                    ", Current version: " + currentDbVersion + ". Cannot import.");
        }

        // STEP 2: FULL CLEANUP - Delete all existing data
        if (log.isDebugEnabled()) log.debug("importMediaLibrary: performing FULL CLEANUP of existing data");
        cleanupExistingData();

        // STEP 3: Extract all files from backup
        if (log.isDebugEnabled()) log.debug("importMediaLibrary: extracting files from backup");
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];

            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                if (log.isDebugEnabled()) log.debug("importMediaLibrary: extracting {}", fileName);

                // Skip version file
                if (fileName.equals(VERSION_FILE)) {
                    zis.closeEntry();
                    continue;
                }

                File outputFile = null;

                if (fileName.equals(DATABASE_NAME)) {
                    outputFile = getDatabasePath(DATABASE_NAME);
                } else if (fileName.equals(CREDENTIALS_DB_NAME)) {
                    outputFile = getDatabasePath(CREDENTIALS_DB_NAME);
                } else if (fileName.equals(SHORTCUTS_DB_NAME)) {
                    outputFile = getDatabasePath(SHORTCUTS_DB_NAME);
                } else if (fileName.equals(SHORTCUTS2_DB_NAME)) {
                    outputFile = getDatabasePath(SHORTCUTS2_DB_NAME);
                } else if (fileName.startsWith("scraper_posters/")) {
                    String relativePath = fileName.substring("scraper_posters/".length());
                    outputFile = new File(MediaScraper.getPosterDirectory(this), relativePath);
                } else if (fileName.startsWith("scraper_backdrops/")) {
                    String relativePath = fileName.substring("scraper_backdrops/".length());
                    outputFile = new File(MediaScraper.getBackdropDirectory(this), relativePath);
                } else if (fileName.startsWith("scraper_pictures/")) {
                    String relativePath = fileName.substring("scraper_pictures/".length());
                    outputFile = new File(MediaScraper.getPictureDirectory(this), relativePath);
                }

                if (outputFile != null && !entry.isDirectory()) {
                    // Create parent directories if needed
                    File parentDir = outputFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    // Extract file
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }

                zis.closeEntry();
            }
        }

        if (log.isDebugEnabled()) log.debug("importMediaLibrary: FULL REPLACEMENT import completed successfully");
    }

    private void flushDatabaseWAL() {
        try {
            File dbFile = getDatabasePath(DATABASE_NAME);
            if (dbFile.exists()) {
                if (log.isDebugEnabled()) log.debug("flushDatabaseWAL: opening database for WAL checkpoint");
                SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null,
                        SQLiteDatabase.OPEN_READWRITE);
                // Checkpoint WAL to main database file
                if (log.isDebugEnabled()) log.debug("flushDatabaseWAL: executing PRAGMA wal_checkpoint(FULL)");
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close();
                db.close();
                if (log.isDebugEnabled()) log.debug("flushDatabaseWAL: database WAL flushed successfully");
            }
        } catch (Exception e) {
            log.warn("flushDatabaseWAL: failed to flush WAL, continuing anyway", e);
        }
    }

    private void addVersionToZip(ZipOutputStream zos, int version) throws IOException {
        if (log.isDebugEnabled()) log.debug("addVersionToZip: adding version={}", version);

        ZipEntry zipEntry = new ZipEntry(VERSION_FILE);
        zos.putNextEntry(zipEntry);

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zos));
        writer.write(String.valueOf(version));
        writer.newLine();
        writer.flush();

        zos.closeEntry();
    }

    private void cleanupExistingData() {
        if (log.isDebugEnabled()) log.debug("cleanupExistingData: DELETING all existing media library data");

        // Close database helper and connections before deleting files to release locks and files
        if (log.isDebugEnabled()) log.debug("cleanupExistingData: closing database connections");
        VideoDb.getHolder(this).close();
        ShortcutDbAdapter.VIDEO.close();

        // Delete media database files
        File dbFile = getDatabasePath(DATABASE_NAME);
        File dbWalFile = new File(dbFile.getAbsolutePath() + "-wal");
        File dbShmFile = new File(dbFile.getAbsolutePath() + "-shm");

        if (dbFile.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting {}", dbFile.getAbsolutePath());
            dbFile.delete();
        }
        if (dbWalFile.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting {}", dbWalFile.getAbsolutePath());
            dbWalFile.delete();
        }
        if (dbShmFile.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting {}", dbShmFile.getAbsolutePath());
            dbShmFile.delete();
        }

        // Delete credentials database files
        File credentialsDbFile = getDatabasePath(CREDENTIALS_DB_NAME);
        File credentialsDbWalFile = new File(credentialsDbFile.getAbsolutePath() + "-wal");
        File credentialsDbShmFile = new File(credentialsDbFile.getAbsolutePath() + "-shm");

        if (credentialsDbFile.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting {}", credentialsDbFile.getAbsolutePath());
            credentialsDbFile.delete();
        }
        if (credentialsDbWalFile.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting {}", credentialsDbWalFile.getAbsolutePath());
            credentialsDbWalFile.delete();
        }
        if (credentialsDbShmFile.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting {}", credentialsDbShmFile.getAbsolutePath());
            credentialsDbShmFile.delete();
        }

        // Delete shortcuts database files
        File shortcutsDbFile = getDatabasePath(SHORTCUTS_DB_NAME);
        File shortcutsDbWalFile = new File(shortcutsDbFile.getAbsolutePath() + "-wal");
        File shortcutsDbShmFile = new File(shortcutsDbFile.getAbsolutePath() + "-shm");

        if (shortcutsDbFile.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting {}", shortcutsDbFile.getAbsolutePath());
            shortcutsDbFile.delete();
        }
        if (shortcutsDbWalFile.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting {}", shortcutsDbWalFile.getAbsolutePath());
            shortcutsDbWalFile.delete();
        }
        if (shortcutsDbShmFile.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting {}", shortcutsDbShmFile.getAbsolutePath());
            shortcutsDbShmFile.delete();
        }

        // Delete shortcuts2 database files
        File shortcuts2DbFile = getDatabasePath(SHORTCUTS2_DB_NAME);
        File shortcuts2DbWalFile = new File(shortcuts2DbFile.getAbsolutePath() + "-wal");
        File shortcuts2DbShmFile = new File(shortcuts2DbFile.getAbsolutePath() + "-shm");

        if (shortcuts2DbFile.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting {}", shortcuts2DbFile.getAbsolutePath());
            shortcuts2DbFile.delete();
        }
        if (shortcuts2DbWalFile.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting {}", shortcuts2DbWalFile.getAbsolutePath());
            shortcuts2DbWalFile.delete();
        }
        if (shortcuts2DbShmFile.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting {}", shortcuts2DbShmFile.getAbsolutePath());
            shortcuts2DbShmFile.delete();
        }

        // Delete all poster files
        File posterDir = MediaScraper.getPosterDirectory(this);
        if (posterDir.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting all files in {}", posterDir.getAbsolutePath());
            deleteDirectoryContents(posterDir);
        }

        // Delete all backdrop files
        File backdropDir = MediaScraper.getBackdropDirectory(this);
        if (backdropDir.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting all files in {}", backdropDir.getAbsolutePath());
            deleteDirectoryContents(backdropDir);
        }

        // Delete all picture files
        File pictureDir = MediaScraper.getPictureDirectory(this);
        if (pictureDir.exists()) {
            if (log.isDebugEnabled()) log.debug("cleanupExistingData: deleting all files in {}", pictureDir.getAbsolutePath());
            deleteDirectoryContents(pictureDir);
        }

        if (log.isDebugEnabled()) log.debug("cleanupExistingData: cleanup completed");
    }

    private void deleteDirectoryContents(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                deleteDirectoryContents(file);
                file.delete();
            } else {
                file.delete();
            }
        }
    }

    private void addFileToZip(ZipOutputStream zos, File file, String zipEntryName) throws IOException {
        if (log.isDebugEnabled()) log.debug("addFileToZip: {}", zipEntryName);

        ZipEntry zipEntry = new ZipEntry(zipEntryName);
        zos.putNextEntry(zipEntry);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
        }

        zos.closeEntry();
    }

    private void addDirectoryToZip(ZipOutputStream zos, File dir, String zipDirName) throws IOException {
        if (log.isDebugEnabled()) log.debug("addDirectoryToZip: {}", zipDirName);

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String zipEntryName = zipDirName + "/" + file.getName();

            if (file.isDirectory()) {
                addDirectoryToZip(zos, file, zipEntryName);
            } else {
                addFileToZip(zos, file, zipEntryName);
            }
        }
    }

    private void showToast(final String message) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                Toast.makeText(MediaLibraryBackupService.this, message, Toast.LENGTH_LONG).show()
        );
    }

    private void restartApplication() {
        if (log.isDebugEnabled()) log.debug("restartApplication: restarting app after import");

        try {
            // Get the main activity intent
            Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                // Schedule app restart after a short delay
                int pendingIntentFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
                        : android.app.PendingIntent.FLAG_UPDATE_CURRENT;

                android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        pendingIntentFlags
                );

                android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null) {
                    alarmManager.set(
                            android.app.AlarmManager.RTC,
                            System.currentTimeMillis() + 500, // 500ms delay
                            pendingIntent
                    );
                }

                if (log.isDebugEnabled()) log.debug("restartApplication: app restart scheduled, exiting process");

                // Exit the current process
                System.exit(0);
            }
        } catch (Exception e) {
            log.error("restartApplication: failed to restart app", e);
        }
    }

    @Override
    public void onDestroy() {
        if (log.isDebugEnabled()) log.debug("onDestroy");
        super.onDestroy();
    }
}
