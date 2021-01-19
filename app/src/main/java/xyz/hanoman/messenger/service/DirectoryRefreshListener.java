package xyz.hanoman.messenger.service;


import android.content.Context;
import android.content.Intent;

import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobs.DirectoryRefreshJob;
import xyz.hanoman.messenger.util.FeatureFlags;
import xyz.hanoman.messenger.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

public class DirectoryRefreshListener extends PersistentAlarmManagerListener {

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return TextSecurePreferences.getDirectoryRefreshTime(context);
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    if (scheduledTime != 0 && TextSecurePreferences.isPushRegistered(context)) {
      ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(true));
    }

    long interval = TimeUnit.SECONDS.toMillis(FeatureFlags.cdsRefreshIntervalSeconds());
    long newTime  = System.currentTimeMillis() + interval;

    TextSecurePreferences.setDirectoryRefreshTime(context, newTime);

    return newTime;
  }

  public static void schedule(Context context) {
    new DirectoryRefreshListener().onReceive(context, new Intent());
  }
}
