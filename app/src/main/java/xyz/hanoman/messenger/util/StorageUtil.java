package xyz.hanoman.messenger.util;

import android.Manifest;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import xyz.hanoman.messenger.BuildConfig;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.database.NoExternalStorageException;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.permissions.Permissions;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class StorageUtil {

  private static final String PRODUCTION_PACKAGE_ID = "xyz.hanoman.messenger";

  public static File getOrCreateBackupDirectory() throws NoExternalStorageException {
    File storage = Environment.getExternalStorageDirectory();

    if (!storage.canWrite()) {
      throw new NoExternalStorageException();
    }

    File backups = getBackupDirectory();

    if (!backups.exists()) {
      if (!backups.mkdirs()) {
        throw new NoExternalStorageException("Unable to create backup directory...");
      }
    }

    return backups;
  }

  public static File getBackupDirectory() throws NoExternalStorageException {
    File storage = Environment.getExternalStorageDirectory();
    File signal  = new File(storage, "Signal");
    File backups = new File(signal, "Backups");

    //noinspection ConstantConditions
    if (BuildConfig.APPLICATION_ID.startsWith(PRODUCTION_PACKAGE_ID + ".")) {
      backups = new File(backups, BuildConfig.APPLICATION_ID.substring(PRODUCTION_PACKAGE_ID.length() + 1));
    }

    return backups;
  }

  @RequiresApi(24)
  public static @NonNull String getDisplayPath(@NonNull Context context, @NonNull Uri uri) {
    String lastPathSegment = Objects.requireNonNull(uri.getLastPathSegment());
    String backupVolume    = lastPathSegment.replaceFirst(":.*", "");
    String backupName      = lastPathSegment.replaceFirst(".*:", "");

    StorageManager      storageManager = ServiceUtil.getStorageManager(context);
    List<StorageVolume> storageVolumes = storageManager.getStorageVolumes();
    StorageVolume       storageVolume  = null;

    for (StorageVolume volume : storageVolumes) {
      if (Objects.equals(volume.getUuid(), backupVolume)) {
        storageVolume = volume;
        break;
      }
    }

    if (storageVolume == null) {
      return backupName;
    } else {
      return context.getString(R.string.StorageUtil__s_s, storageVolume.getDescription(context), backupName);
    }
  }

  public static File getBackupCacheDirectory(Context context) {
    return context.getExternalCacheDir();
  }

  private static File getSignalStorageDir() throws NoExternalStorageException {
    final File storage = Environment.getExternalStorageDirectory();

    if (!storage.canWrite()) {
      throw new NoExternalStorageException();
    }

    return storage;
  }

  public static boolean canWriteInSignalStorageDir() {
    File storage;

    try {
      storage = getSignalStorageDir();
    } catch (NoExternalStorageException e) {
      return false;
    }

    return storage.canWrite();
  }

  public static File getLegacyBackupDirectory() throws NoExternalStorageException {
    return getSignalStorageDir();
  }

  public static boolean canWriteToMediaStore() {
    return Build.VERSION.SDK_INT > 28 ||
           Permissions.hasAll(ApplicationDependencies.getApplication(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
  }

  public static boolean canReadFromMediaStore() {
    return Permissions.hasAll(ApplicationDependencies.getApplication(), Manifest.permission.READ_EXTERNAL_STORAGE);
  }

  public static @NonNull Uri getVideoUri() {
    if (Build.VERSION.SDK_INT < 21) {
      return getLegacyUri(Environment.DIRECTORY_MOVIES);
    } else {
      return MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    }
  }

  public static @NonNull Uri getAudioUri() {
    if (Build.VERSION.SDK_INT < 29) {
      return getLegacyUri(Environment.DIRECTORY_MUSIC);
    } else {
      return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    }
  }

  public static @NonNull Uri getImageUri() {
    if (Build.VERSION.SDK_INT < 21) {
      return getLegacyUri(Environment.DIRECTORY_PICTURES);
    } else {
      return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    }
  }

  public static @NonNull Uri getDownloadUri() {
    if (Build.VERSION.SDK_INT < 29) {
      return getLegacyUri(Environment.DIRECTORY_DOWNLOADS);
    } else {
      return MediaStore.Downloads.EXTERNAL_CONTENT_URI;
    }
  }

  public static @NonNull Uri getLegacyUri(@NonNull String directory) {
    return Uri.fromFile(Environment.getExternalStoragePublicDirectory(directory));
  }

  public static @Nullable String getCleanFileName(@Nullable String fileName) {
    if (fileName == null) return null;

    fileName = fileName.replace('\u202D', '\uFFFD');
    fileName = fileName.replace('\u202E', '\uFFFD');

    return fileName;
  }
}
