package xyz.hanoman.messenger.jobs;


import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.backup.BackupFileIOError;
import xyz.hanoman.messenger.backup.BackupPassphrase;
import xyz.hanoman.messenger.backup.FullBackupExporter;
import xyz.hanoman.messenger.crypto.AttachmentSecretProvider;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.keyvalue.SignalStore;
import xyz.hanoman.messenger.notifications.NotificationChannels;
import xyz.hanoman.messenger.service.GenericForegroundService;
import xyz.hanoman.messenger.service.NotificationController;
import xyz.hanoman.messenger.util.BackupUtil;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Backup Job for installs requiring Scoped Storage.
 *
 * @see LocalBackupJob#enqueue(boolean)
 */
public final class LocalBackupJobApi29 extends BaseJob {

  public static final String KEY = "LocalBackupJobApi29";

  private static final String TAG = Log.tag(LocalBackupJobApi29.class);

  public static final String TEMP_BACKUP_FILE_PREFIX = ".backup";
  public static final String TEMP_BACKUP_FILE_SUFFIX = ".tmp";

  LocalBackupJobApi29(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    Log.i(TAG, "Executing backup job...");

    BackupFileIOError.clearNotification(context);

    if (!BackupUtil.isUserSelectionRequired(context)) {
      throw new IOException("Wrong backup job!");
    }

    Uri backupDirectoryUri = SignalStore.settings().getSignalBackupDirectory();
    if (backupDirectoryUri == null || backupDirectoryUri.getPath() == null) {
      throw new IOException("Backup Directory has not been selected!");
    }

    try (NotificationController notification = GenericForegroundService.startForegroundTask(context,
                                                                                            context.getString(R.string.LocalBackupJob_creating_backup),
                                                                                            NotificationChannels.BACKUPS,
                                                                                            R.drawable.ic_signal_backup))
    {
      notification.setIndeterminateProgress();

      String       backupPassword  = BackupPassphrase.get(context);
      DocumentFile backupDirectory = DocumentFile.fromTreeUri(context, backupDirectoryUri);
      String       timestamp       = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(new Date());
      String       fileName        = String.format("signal-%s.backup", timestamp);

      if (backupDirectory == null || !backupDirectory.canWrite()) {
        BackupFileIOError.ACCESS_ERROR.postNotification(context);
        throw new IOException("Cannot write to backup directory location.");
      }

      deleteOldTemporaryBackups(backupDirectory);

      if (backupDirectory.findFile(fileName) != null) {
        throw new IOException("Backup file already exists!");
      }

      String       temporaryName = String.format(Locale.US, "%s%s%s", TEMP_BACKUP_FILE_PREFIX, UUID.randomUUID(), TEMP_BACKUP_FILE_SUFFIX);
      DocumentFile temporaryFile = backupDirectory.createFile("application/octet-stream", temporaryName);

      if (temporaryFile == null) {
        throw new IOException("Failed to create temporary backup file.");
      }

      if (backupPassword == null) {
        throw new IOException("Backup password is null");
      }

      try {
        FullBackupExporter.export(context,
                                  AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
                                  DatabaseFactory.getBackupDatabase(context),
                                  temporaryFile,
                                  backupPassword);

        if (!temporaryFile.renameTo(fileName)) {
          Log.w(TAG, "Failed to rename temp file");
          throw new IOException("Renaming temporary backup file failed!");
        }
      } catch (IOException e) {
        Log.w(TAG, "Error during backup!", e);
        BackupFileIOError.postNotificationForException(context, e, getRunAttempt());
        throw e;
      } finally {
        DocumentFile fileToCleanUp = backupDirectory.findFile(temporaryName);
        if (fileToCleanUp != null) {
          if (fileToCleanUp.delete()) {
            Log.w(TAG, "Backup failed. Deleted temp file");
          } else {
            Log.w(TAG, "Backup failed. Failed to delete temp file " + temporaryName);
          }
        }
      }

      BackupUtil.deleteOldBackups();
    }
  }

  private static void deleteOldTemporaryBackups(@NonNull DocumentFile backupDirectory) {
    for (DocumentFile file : backupDirectory.listFiles()) {
      if (file.isFile()) {
        String name = file.getName();
        if (name != null && name.startsWith(TEMP_BACKUP_FILE_PREFIX) && name.endsWith(TEMP_BACKUP_FILE_SUFFIX)) {
          if (file.delete()) {
            Log.w(TAG, "Deleted old temporary backup file");
          } else {
            Log.w(TAG, "Could not delete old temporary backup file");
          }
        }
      }
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public void onFailure() {
  }

  public static class Factory implements Job.Factory<LocalBackupJobApi29> {
    @Override
    public @NonNull
    LocalBackupJobApi29 create(@NonNull Parameters parameters, @NonNull Data data) {
      return new LocalBackupJobApi29(parameters);
    }
  }
}
