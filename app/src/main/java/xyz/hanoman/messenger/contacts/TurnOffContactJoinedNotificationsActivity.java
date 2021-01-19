package xyz.hanoman.messenger.contacts;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.MessageDatabase;
import xyz.hanoman.messenger.database.ThreadDatabase;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.notifications.MarkReadReceiver;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import xyz.hanoman.messenger.util.concurrent.SimpleTask;

import java.util.List;

/**
 * Activity which displays a dialog to confirm whether to turn off "Contact Joined Signal" notifications.
 */
public class TurnOffContactJoinedNotificationsActivity extends AppCompatActivity {

  private final static String EXTRA_THREAD_ID = "thread_id";

  public static Intent newIntent(@NonNull Context context, long threadId) {
    Intent intent = new Intent(context, TurnOffContactJoinedNotificationsActivity.class);

    intent.putExtra(EXTRA_THREAD_ID, threadId);

    return intent;
  }

  @Override
  protected void onResume() {
    super.onResume();

    new AlertDialog.Builder(this)
                   .setMessage(R.string.TurnOffContactJoinedNotificationsActivity__turn_off_contact_joined_signal)
                   .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                     handlePositiveAction(dialog);
                   })
                   .setNegativeButton(android.R.string.cancel, ((dialog, which) -> {
                     dialog.dismiss();
                     finish();
                   }))
                   .show();
  }

  private void handlePositiveAction(@NonNull DialogInterface dialog) {
    SimpleTask.run(getLifecycle(), () -> {
      ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(this);

      List<MessageDatabase.MarkedMessageInfo> marked = threadDatabase.setRead(getIntent().getLongExtra(EXTRA_THREAD_ID, -1), false);
      MarkReadReceiver.process(this, marked);

      TextSecurePreferences.setNewContactsNotificationEnabled(this, false);
      ApplicationDependencies.getMessageNotifier().updateNotification(this);

      return null;
    }, unused -> {
      dialog.dismiss();
      finish();
    });
  }
}
