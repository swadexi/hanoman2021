package xyz.hanoman.messenger.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobs.PushNotificationReceiveJob;

public class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    ApplicationDependencies.getJobManager().add(new PushNotificationReceiveJob(context));
  }
}
