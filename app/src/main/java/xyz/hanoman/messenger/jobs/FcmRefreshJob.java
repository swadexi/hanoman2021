/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package xyz.hanoman.messenger.jobs;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.PlayServicesProblemActivity;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.gcm.FcmUtil;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.jobmanager.impl.NetworkConstraint;
import xyz.hanoman.messenger.notifications.NotificationChannels;
import xyz.hanoman.messenger.notifications.NotificationIds;
import xyz.hanoman.messenger.transport.RetryLaterException;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class FcmRefreshJob extends BaseJob {

  public static final String KEY = "FcmRefreshJob";

  private static final String TAG = FcmRefreshJob.class.getSimpleName();

  public FcmRefreshJob() {
    this(new Job.Parameters.Builder()
                           .setQueue("FcmRefreshJob")
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(1)
                           .setLifespan(TimeUnit.MINUTES.toMillis(5))
                           .setMaxInstancesForFactory(1)
                           .build());
  }

  private FcmRefreshJob(@NonNull Job.Parameters parameters) {
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
  public void onRun() throws Exception {
    if (TextSecurePreferences.isFcmDisabled(context)) return;

    Log.i(TAG, "Reregistering FCM...");

    int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);

    if (result != ConnectionResult.SUCCESS) {
      notifyFcmFailure();
    } else {
      Optional<String> token = FcmUtil.getToken();

      if (token.isPresent()) {
        String oldToken = TextSecurePreferences.getFcmToken(context);

        if (!token.get().equals(oldToken)) {
          int oldLength = oldToken != null ? oldToken.length() : -1;
          Log.i(TAG, "Token changed. oldLength: " + oldLength + "  newLength: " + token.get().length());
        } else {
          Log.i(TAG, "Token didn't change.");
        }

        ApplicationDependencies.getSignalServiceAccountManager().setGcmId(token);
        TextSecurePreferences.setFcmToken(context, token.get());
        TextSecurePreferences.setFcmTokenLastSetTime(context, System.currentTimeMillis());
        TextSecurePreferences.setWebsocketRegistered(context, true);
      } else {
        throw new RetryLaterException(new IOException("Failed to retrieve a token."));
      }
    }
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "GCM reregistration failed after retry attempt exhaustion!");
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception throwable) {
    if (throwable instanceof NonSuccessfulResponseCodeException) return false;
    return true;
  }

  private void notifyFcmFailure() {
    Intent                     intent        = new Intent(context, PlayServicesProblemActivity.class);
    PendingIntent              pendingIntent = PendingIntent.getActivity(context, 1122, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    NotificationCompat.Builder builder       = new NotificationCompat.Builder(context, NotificationChannels.FAILURES);

    builder.setSmallIcon(R.drawable.ic_notification);
    builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                                                      R.drawable.ic_action_warning_red));
    builder.setContentTitle(context.getString(R.string.GcmRefreshJob_Permanent_Signal_communication_failure));
    builder.setContentText(context.getString(R.string.GcmRefreshJob_Signal_was_unable_to_register_with_Google_Play_Services));
    builder.setTicker(context.getString(R.string.GcmRefreshJob_Permanent_Signal_communication_failure));
    builder.setVibrate(new long[] {0, 1000});
    builder.setContentIntent(pendingIntent);

    ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
        .notify(NotificationIds.FCM_FAILURE, builder.build());
  }

  public static final class Factory implements Job.Factory<FcmRefreshJob> {
    @Override
    public @NonNull FcmRefreshJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new FcmRefreshJob(parameters);
    }
  }
}
