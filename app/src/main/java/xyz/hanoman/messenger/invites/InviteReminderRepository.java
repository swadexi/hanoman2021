package xyz.hanoman.messenger.invites;

import android.content.Context;

import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.MmsSmsDatabase;
import xyz.hanoman.messenger.database.RecipientDatabase;
import xyz.hanoman.messenger.recipients.Recipient;

public final class InviteReminderRepository implements InviteReminderModel.Repository {

  private final Context context;

  public InviteReminderRepository(Context context) {
    this.context = context;
  }

  @Override
  public void setHasSeenFirstInviteReminder(Recipient recipient) {
    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    recipientDatabase.setSeenFirstInviteReminder(recipient.getId());
  }

  @Override
  public void setHasSeenSecondInviteReminder(Recipient recipient) {
    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    recipientDatabase.setSeenSecondInviteReminder(recipient.getId());
  }

  @Override
  public int getPercentOfInsecureMessages(int insecureCount) {
    MmsSmsDatabase mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(context);
    int            insecure       = mmsSmsDatabase.getInsecureMessageCountForInsights();
    int            secure         = mmsSmsDatabase.getSecureMessageCountForInsights();

    if (insecure + secure == 0) return 0;
    return Math.round(100f * (insecureCount / (float) (insecure + secure)));
  }
}
