package xyz.hanoman.messenger.components.reminder;

import android.content.Context;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.recipients.Recipient;

public final class FirstInviteReminder extends Reminder {

  public FirstInviteReminder(final @NonNull Context context,
                             final @NonNull Recipient recipient,
                             final int percentIncrease) {
    super(context.getString(R.string.FirstInviteReminder__title),
          context.getString(R.string.FirstInviteReminder__description, percentIncrease));

    addAction(new Action(context.getString(R.string.InsightsReminder__invite), R.id.reminder_action_invite));
    addAction(new Action(context.getString(R.string.InsightsReminder__view_insights), R.id.reminder_action_view_insights));
  }
}
