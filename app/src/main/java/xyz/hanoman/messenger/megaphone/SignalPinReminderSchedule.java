package xyz.hanoman.messenger.megaphone;

import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.keyvalue.SignalStore;
import xyz.hanoman.messenger.util.TextSecurePreferences;

final class SignalPinReminderSchedule implements MegaphoneSchedule {

  @Override
  public boolean shouldDisplay(int seenCount, long lastSeen, long firstVisible, long currentTime) {
    if (SignalStore.kbsValues().hasOptedOut()) {
      return false;
    }

    if (!SignalStore.kbsValues().hasPin()) {
      return false;
    }

    if (!SignalStore.pinValues().arePinRemindersEnabled()) {
      return false;
    }

    if (!TextSecurePreferences.isPushRegistered(ApplicationDependencies.getApplication())) {
      return false;
    }

    long lastSuccessTime = SignalStore.pinValues().getLastSuccessfulEntryTime();
    long interval        = SignalStore.pinValues().getCurrentInterval();

    return currentTime - lastSuccessTime >= interval;
  }
}
