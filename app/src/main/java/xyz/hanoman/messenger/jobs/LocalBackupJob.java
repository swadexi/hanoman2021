package xyz.hanoman.messenger.jobs;


import android.Manifest;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.backup.BackupFileIOError;
import xyz.hanoman.messenger.backup.BackupPassphrase;
import xyz.hanoman.messenger.backup.FullBackupExporter;
import xyz.hanoman.messenger.crypto.AttachmentSecretProvider;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.NoExternalStorageException;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.jobmanager.JobManager;
import xyz.hanoman.messenger.jobmanager.impl.ChargingConstraint;
import xyz.hanoman.messenger.notifications.NotificationChannels;
import xyz.hanoman.messenger.permissions.Permissions;
import xyz.hanoman.messenger.service.GenericForegroundService;
import xyz.hanoman.messenger.service.NotificationController;
import xyz.hanoman.messenger.util.BackupUtil;
import xyz.hanoman.messenger.util.StorageUtil;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class LocalBackupJob extends BaseJob {

  public static final String KEY = "LocalBackupJob";

  private static final String TAG = Log.tag(LocalBackupJob.class);

  private static final String QUEUE = "__LOCAL_BACKUP__";

  public static final String TEMP_BACKUP_FILE_PREFIX = ".backup";
  public static final String TEMP_BACKUP_FILE_SUFFIX = ".tmp";

  public static void enqueue(boolean force) {
    JobManager         jobManager = ApplicationDependencies.getJobManager();
    Parameters.Builder parameters = new Parameters.Builder()
                                                  .setQueue(QUEUE)
                                                  .setMaxInstancesForFactory(1)
                                                  .setMaxAttempts(3);
    if (force) {
      jobManager.cancelAllInQueue(QUEUE);
    } else {
      parameters.addConstraint(ChargingConstraint.KEY);
    }

    if (BackupUtil.isUserSelectionRequired(ApplicationDependencies.getApplication())) {
      jobManager.add(new LocalBackupJobApi29(parameters.build()));
    } else {
      jobManager.add(new LocalBackupJob(parameters.build()));
    }
  }

  private LocalBackupJob(@NonNull Job.Parameters parameters) {
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
  public void onRun() throws NoExternalStorageException, IOException {
    Log.i(TAG, "Executing backup job...");

    BackupFileIOError.clearNotification(context);

    if (!Permissions.hasAll(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      throw new IOException("No external storage permission!");
    }

    try (NotificationController notification = GenericForegroundService.startForegroundTask(context,
                                                                     context.getString(R.string.LocalBackupJob_creating_backup),
                                                                     NotificationChannels.BACKUPS,
                                                                     R.drawable.ic_signal_backup))
    {
      notification.setIndeterminateProgress();

      String backupPassword  = BackupPassphrase.get(context);
      File   backupDirectory = StorageUtil.getOrCreateBackupDirectory();
      String timestamp       = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(new Date());
      String fileName        = String.format("signal-%s.backup", timestamp);
      File   backupFile      = new File(backupDirectory, fileName);

      deleteOldTemporaryBackups(backupDirectory);

      if (backupFile.exists()) {
        throw new IOException("Backup file already exists?");
      }

      if (backupPassword == null) {
        throw new IOException("Backup password is null");
      }

      File tempFile = File.createTempFile(TEMP_BACKUP_FILE_PREFIX, TEMP_BACKUP_FILE_SUFFIX, backupDirectory);

      try {
        FullBackupExporter.export(context,
                                  AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
                                  DatabaseFactory.getBackupDatabase(context),
                                  tempFile,
                                  backupPassword);

        if (!tempFile.renameTo(backupFile)) {
          Log.w(TAG, "Failed to rename temp file");
          throw new IOException("Renaming temporary backup file failed!");
        }
      } catch (IOException e) {
        BackupFileIOError.postNotificationForException(context, e, getRunAttempt());
        throw e;
      } finally {
        if (tempFile.exists()) {
          if (tempFile.delete()) {
            Log.w(TAG, "Backup failed. Deleted temp file");
          } else {
            Log.w(TAG, "Backup failed. Failed to delete temp file " + tempFile);
          }
        }
      }

      BackupUtil.deleteOldBackups();
    }
  }

  private static void deleteOldTemporaryBackups(@NonNull File backupDirectory) {
    for (File file : backupDirectory.listFiles()) {
      if (file.isFile()) {
        String name = file.getName();
        if (name.startsWith(TEMP_BACKUP_FILE_PREFIX) && name.endsWith(TEMP_BACKUP_FILE_SUFFIX)) {
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

  public static class Factory implements Job.Factory<LocalBackupJob> {
    @Override
    public @NonNull LocalBackupJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new LocalBackupJob(parameters);
    }
  }
}
