package xyz.hanoman.messenger.notifications;


import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import xyz.hanoman.messenger.MainActivity;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.database.RecipientDatabase;
import xyz.hanoman.messenger.preferences.widgets.NotificationPrivacyPreference;
import xyz.hanoman.messenger.util.TextSecurePreferences;

public class PendingMessageNotificationBuilder extends AbstractNotificationBuilder {

  public PendingMessageNotificationBuilder(Context context, NotificationPrivacyPreference privacy) {
    super(context, privacy);

    setSmallIcon(R.drawable.ic_notification);
    setColor(context.getResources().getColor(R.color.core_ultramarine));
    setCategory(NotificationCompat.CATEGORY_MESSAGE);

    setContentTitle(context.getString(R.string.MessageNotifier_you_may_have_new_messages));
    setContentText(context.getString(R.string.MessageNotifier_open_signal_to_check_for_recent_notifications));
    setTicker(context.getString(R.string.MessageNotifier_open_signal_to_check_for_recent_notifications));

    // TODO [greyson] Navigation
    setContentIntent(PendingIntent.getActivity(context, 0, MainActivity.clearTop(context), 0));
    setAutoCancel(true);
    setAlarms(null, RecipientDatabase.VibrateState.DEFAULT);

    setOnlyAlertOnce(true);

    if (!NotificationChannels.supported()) {
      setPriority(TextSecurePreferences.getNotificationPriority(context));
    }
  }
}
