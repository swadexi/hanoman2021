package xyz.hanoman.messenger.service;


import android.content.Context;
import android.content.Intent;

import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobs.RotateCertificateJob;
import xyz.hanoman.messenger.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

public class RotateSenderCertificateListener extends PersistentAlarmManagerListener {

  private static final long INTERVAL = TimeUnit.DAYS.toMillis(1);

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return TextSecurePreferences.getUnidentifiedAccessCertificateRotationTime(context);
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    ApplicationDependencies.getJobManager().add(new RotateCertificateJob());

    long nextTime = System.currentTimeMillis() + INTERVAL;
    TextSecurePreferences.setUnidentifiedAccessCertificateRotationTime(context, nextTime);

    return nextTime;
  }

  public static void schedule(Context context) {
    new RotateSenderCertificateListener().onReceive(context, new Intent());
  }

}
