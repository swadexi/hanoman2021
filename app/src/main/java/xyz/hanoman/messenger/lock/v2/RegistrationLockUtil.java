package xyz.hanoman.messenger.lock.v2;

import android.content.Context;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.keyvalue.SignalStore;
import xyz.hanoman.messenger.util.TextSecurePreferences;

public final class RegistrationLockUtil {

  private RegistrationLockUtil() {}

  public static boolean userHasRegistrationLock(@NonNull Context context) {
    return TextSecurePreferences.isV1RegistrationLockEnabled(context) || SignalStore.kbsValues().isV2RegistrationLockEnabled();
  }
}
