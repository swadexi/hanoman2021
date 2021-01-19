package xyz.hanoman.messenger.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.TaskStackBuilder;

import xyz.hanoman.messenger.conversation.ConversationIntents;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.mms.SlideDeck;
import xyz.hanoman.messenger.recipients.Recipient;

public class NotificationItem {

            private final long         id;
            private final boolean      mms;
  @NonNull  private final Recipient    conversationRecipient;
  @NonNull  private final Recipient    individualRecipient;
  @Nullable private final Recipient    threadRecipient;
            private final long         threadId;
  @Nullable private final CharSequence text;
            private final long         timestamp;
            private final long         messageReceivedTimestamp;
  @Nullable private final SlideDeck    slideDeck;
            private final boolean      jumpToMessage;
            private final boolean      isJoin;
            private final boolean      canReply;
            private final long         notifiedTimestamp;

  public NotificationItem(long id,
                          boolean mms,
                          @NonNull Recipient individualRecipient,
                          @NonNull Recipient conversationRecipient,
                          @Nullable Recipient threadRecipient,
                          long threadId,
                          @Nullable CharSequence text,
                          long timestamp,
                          long messageReceivedTimestamp,
                          @Nullable SlideDeck slideDeck,
                          boolean jumpToMessage,
                          boolean isJoin,
                          boolean canReply,
                          long notifiedTimestamp)
  {
    this.id                       = id;
    this.mms                      = mms;
    this.individualRecipient      = individualRecipient;
    this.conversationRecipient    = conversationRecipient;
    this.threadRecipient          = threadRecipient;
    this.text                     = text;
    this.threadId                 = threadId;
    this.timestamp                = timestamp;
    this.messageReceivedTimestamp = messageReceivedTimestamp;
    this.slideDeck                = slideDeck;
    this.jumpToMessage            = jumpToMessage;
    this.isJoin                   = isJoin;
    this.canReply                 = canReply;
    this.notifiedTimestamp        = notifiedTimestamp;
  }

  public @NonNull  Recipient getRecipient() {
    return threadRecipient == null ? conversationRecipient : threadRecipient;
  }

  public @NonNull  Recipient getIndividualRecipient() {
    return individualRecipient;
  }

  public @Nullable CharSequence getText() {
    return text;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getThreadId() {
    return threadId;
  }

  public @Nullable SlideDeck getSlideDeck() {
    return slideDeck;
  }

  public PendingIntent getPendingIntent(Context context) {
    Recipient recipient        = threadRecipient != null ? threadRecipient : conversationRecipient;
    int       startingPosition = jumpToMessage ? getStartingPosition(context, threadId, messageReceivedTimestamp) : -1;

    Intent intent = ConversationIntents.createBuilder(context, recipient.getId(), threadId)
                                       .withStartingPosition(startingPosition)
                                       .build();

    makeIntentUniqueToPreventMerging(intent);

    return TaskStackBuilder.create(context)
                           .addNextIntentWithParentStack(intent)
                           .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public long getId() {
    return id;
  }

  public boolean isMms() {
    return mms;
  }

  public boolean isJoin() {
    return isJoin;
  }

  private static int getStartingPosition(@NonNull Context context, long threadId, long receivedTimestampMs) {
    return DatabaseFactory.getMmsSmsDatabase(context).getMessagePositionInConversation(threadId, receivedTimestampMs);
  }

  private static void makeIntentUniqueToPreventMerging(@NonNull Intent intent) {
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));
  }

  public boolean canReply() {
    return canReply;
  }

  public long getNotifiedTimestamp() {
    return notifiedTimestamp;
  }
}
