/*
 * Copyright (C) 2011 Whisper Systems
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
package xyz.hanoman.messenger.notifications;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.TransactionTooLargeException;
import android.service.notification.StatusBarNotification;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.contactshare.Contact;
import xyz.hanoman.messenger.contactshare.ContactUtil;
import xyz.hanoman.messenger.conversation.ConversationIntents;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.MentionUtil;
import xyz.hanoman.messenger.database.MmsSmsColumns;
import xyz.hanoman.messenger.database.MmsSmsDatabase;
import xyz.hanoman.messenger.database.RecipientDatabase;
import xyz.hanoman.messenger.database.ThreadBodyUtil;
import xyz.hanoman.messenger.database.model.MessageRecord;
import xyz.hanoman.messenger.database.model.MmsMessageRecord;
import xyz.hanoman.messenger.database.model.ReactionRecord;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.messages.IncomingMessageObserver;
import xyz.hanoman.messenger.mms.Slide;
import xyz.hanoman.messenger.mms.SlideDeck;
import xyz.hanoman.messenger.preferences.widgets.NotificationPrivacyPreference;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientUtil;
import xyz.hanoman.messenger.service.KeyCachingService;
import xyz.hanoman.messenger.util.BubbleUtil;
import xyz.hanoman.messenger.util.MediaUtil;
import xyz.hanoman.messenger.util.MessageRecordUtil;
import xyz.hanoman.messenger.util.ServiceUtil;
import xyz.hanoman.messenger.util.SpanUtil;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import xyz.hanoman.messenger.webrtc.CallNotificationBuilder;
import org.whispersystems.signalservice.internal.util.Util;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.leolin.shortcutbadger.ShortcutBadger;


/**
 * Handles posting system notifications for new messages.
 *
 *
 * @author Moxie Marlinspike
 */
public class DefaultMessageNotifier implements MessageNotifier {

  private static final String TAG = DefaultMessageNotifier.class.getSimpleName();

  public static final  String EXTRA_REMOTE_REPLY = "extra_remote_reply";
  public static final  String NOTIFICATION_GROUP = "messages";

  private static final String EMOJI_REPLACEMENT_STRING  = "__EMOJI__";
  private static final long   MIN_AUDIBLE_PERIOD_MILLIS = TimeUnit.SECONDS.toMillis(2);
  private static final long   DESKTOP_ACTIVITY_PERIOD   = TimeUnit.MINUTES.toMillis(1);

  private volatile long                     visibleThread                = -1;
  private volatile long                     lastDesktopActivityTimestamp = -1;
  private volatile long                     lastAudibleNotification      = -1;
  private          final CancelableExecutor executor                     = new CancelableExecutor();

  @Override
  public void setVisibleThread(long threadId) {
    visibleThread = threadId;
  }

  @Override
  public long getVisibleThread() {
    return visibleThread;
  }

  @Override
  public void clearVisibleThread() {
    setVisibleThread(-1);
  }

  @Override
  public void setLastDesktopActivityTimestamp(long timestamp) {
    lastDesktopActivityTimestamp = timestamp;
  }

  @Override
  public void notifyMessageDeliveryFailed(Context context, Recipient recipient, long threadId) {
    if (visibleThread == threadId) {
      sendInThreadNotification(context, recipient);
    } else {
      Intent                    intent  = ConversationIntents.createBuilder(context, recipient.getId(), threadId)
                                                             .withDataUri(Uri.parse("custom://" + System.currentTimeMillis()))
                                                             .build();
      FailedNotificationBuilder builder = new FailedNotificationBuilder(context, TextSecurePreferences.getNotificationPrivacy(context), intent);

      ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
        .notify((int)threadId, builder.build());
    }
  }

  @Override
  public void cancelDelayedNotifications() {
    executor.cancel();
  }

  private static boolean isDisplayingSummaryNotification(@NonNull Context context) {
    if (Build.VERSION.SDK_INT >= 23) {
      try {
        NotificationManager     notificationManager = ServiceUtil.getNotificationManager(context);
        StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();

        for (StatusBarNotification activeNotification : activeNotifications) {
          if (activeNotification.getId() == NotificationIds.MESSAGE_SUMMARY) {
            return true;
          }
        }

        return false;

      } catch (Throwable e) {
        // XXX Android ROM Bug, see #6043
        Log.w(TAG, e);
        return false;
      }
    } else {
      return false;
    }
  }

  private static void cancelOrphanedNotifications(@NonNull Context context, NotificationState notificationState) {
    if (Build.VERSION.SDK_INT >= 23) {
      try {
        NotificationManager     notifications       = ServiceUtil.getNotificationManager(context);
        StatusBarNotification[] activeNotifications = notifications.getActiveNotifications();

        for (StatusBarNotification notification : activeNotifications) {
          boolean validNotification = false;

          if (notification.getId() != NotificationIds.MESSAGE_SUMMARY       &&
              notification.getId() != KeyCachingService.SERVICE_RUNNING_ID  &&
              notification.getId() != IncomingMessageObserver.FOREGROUND_ID &&
              notification.getId() != NotificationIds.PENDING_MESSAGES      &&
              !CallNotificationBuilder.isWebRtcNotification(notification.getId()))
          {
            for (NotificationItem item : notificationState.getNotifications()) {
              if (notification.getId() == NotificationIds.getNotificationIdForThread(item.getThreadId())) {
                validNotification = true;
                break;
              }
            }

            if (!validNotification) {
              NotificationCancellationHelper.cancel(context, notification.getId());
            }
          }
        }
      } catch (Throwable e) {
        // XXX Android ROM Bug, see #6043
        Log.w(TAG, e);
      }
    }
  }

  @Override
  public void updateNotification(@NonNull Context context) {
    if (!TextSecurePreferences.isNotificationsEnabled(context)) {
      return;
    }

    updateNotification(context, -1, false, 0, BubbleUtil.BubbleState.HIDDEN);
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId)
  {
    if (System.currentTimeMillis() - lastDesktopActivityTimestamp < DESKTOP_ACTIVITY_PERIOD) {
      Log.i(TAG, "Scheduling delayed notification...");
      executor.execute(new DelayedNotification(context, threadId));
    } else {
      updateNotification(context, threadId, true);
    }
  }

  @Override
  public void updateNotification(@NonNull Context context, long threadId, @NonNull BubbleUtil.BubbleState defaultBubbleState) {
    updateNotification(context, threadId, false, 0, defaultBubbleState);
  }

  @Override
  public void updateNotification(@NonNull Context context,
                                 long threadId,
                                 boolean signal)
  {
    boolean   isVisible = visibleThread == threadId;
    Recipient recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId);

    if (shouldNotify(context, recipient, threadId)) {
      if (isVisible) {
        sendInThreadNotification(context, recipient);
      } else {
        updateNotification(context, threadId, signal, 0, BubbleUtil.BubbleState.HIDDEN);
      }
    }
  }

  private boolean shouldNotify(@NonNull Context context, @Nullable Recipient recipient, long threadId) {
    if (!TextSecurePreferences.isNotificationsEnabled(context)) {
      return false;
    }

    if (recipient == null || !recipient.isMuted()) {
      return true;
    }

    return recipient.isPushV2Group()                                                       &&
           recipient.getMentionSetting() == RecipientDatabase.MentionSetting.ALWAYS_NOTIFY &&
           DatabaseFactory.getMmsDatabase(context).getUnreadMentionCount(threadId) > 0;
  }

  @Override
  public void updateNotification(@NonNull Context context,
                                 long     targetThread,
                                 boolean  signal,
                                 int      reminderCount,
                                 @NonNull BubbleUtil.BubbleState defaultBubbleState)
  {
    boolean isReminder  = reminderCount > 0;
    Cursor  telcoCursor = null;
    Cursor  pushCursor  = null;

    try {
      telcoCursor = DatabaseFactory.getMmsSmsDatabase(context).getUnread();
      pushCursor  = DatabaseFactory.getPushDatabase(context).getPending();

      if ((telcoCursor == null || telcoCursor.isAfterLast()) &&
          (pushCursor == null || pushCursor.isAfterLast()))
      {
        NotificationCancellationHelper.cancelAllMessageNotifications(context);
        updateBadge(context, 0);
        clearReminder(context);
        return;
      }

      NotificationState notificationState = constructNotificationState(context, telcoCursor);

      if (signal && (System.currentTimeMillis() - lastAudibleNotification) < MIN_AUDIBLE_PERIOD_MILLIS) {
        signal = false;
      } else if (signal) {
        lastAudibleNotification = System.currentTimeMillis();
      }

      boolean shouldScheduleReminder = signal;

      if (notificationState.hasMultipleThreads()) {
        if (Build.VERSION.SDK_INT >= 23) {
          for (long threadId : notificationState.getThreads()) {
            if (targetThread < 1 || targetThread == threadId) {
              sendSingleThreadNotification(context,
                                           new NotificationState(notificationState.getNotificationsForThread(threadId)),
                                           signal && (threadId == targetThread),
                                           true,
                                           isReminder,
                                           (threadId == targetThread) ? defaultBubbleState : BubbleUtil.BubbleState.HIDDEN);
            }
          }
        }

        sendMultipleThreadNotification(context, notificationState, signal && (Build.VERSION.SDK_INT < 23));
      } else {
        long                   thread      = notificationState.getNotifications().isEmpty() ? -1 : notificationState.getNotifications().get(0).getThreadId();
        BubbleUtil.BubbleState bubbleState = thread == targetThread ? defaultBubbleState : BubbleUtil.BubbleState.HIDDEN;

        shouldScheduleReminder = sendSingleThreadNotification(context, notificationState, signal, false, isReminder, bubbleState);

        if (isDisplayingSummaryNotification(context)) {
          sendMultipleThreadNotification(context, notificationState, false);
        }
      }

      cancelOrphanedNotifications(context, notificationState);
      updateBadge(context, notificationState.getMessageCount());

      List<Long> smsIds = new LinkedList<>();
      List<Long> mmsIds = new LinkedList<>();
      for (NotificationItem item : notificationState.getNotifications()) {
        if (item.isMms()) {
          mmsIds.add(item.getId());
        } else {
          smsIds.add(item.getId());
        }
      }
      DatabaseFactory.getMmsSmsDatabase(context).setNotifiedTimestamp(System.currentTimeMillis(), smsIds, mmsIds);

      if (shouldScheduleReminder) {
        scheduleReminder(context, reminderCount);
      }
    } finally {
      if (telcoCursor != null) telcoCursor.close();
      if (pushCursor != null)  pushCursor.close();
    }
  }

  private static boolean sendSingleThreadNotification(@NonNull Context context,
                                                      @NonNull NotificationState notificationState,
                                                      boolean signal,
                                                      boolean bundled,
                                                      boolean isReminder,
                                                      @NonNull BubbleUtil.BubbleState defaultBubbleState)
  {
    Log.i(TAG, "sendSingleThreadNotification()  signal: " + signal + "  bundled: " + bundled);

    if (notificationState.getNotifications().isEmpty()) {
      if (!bundled) NotificationCancellationHelper.cancelAllMessageNotifications(context);
      Log.i(TAG, "[sendSingleThreadNotification] Empty notification state. Skipping.");
      return false;
    }

    NotificationPrivacyPreference      notificationPrivacy = TextSecurePreferences.getNotificationPrivacy(context);
    SingleRecipientNotificationBuilder builder             = new SingleRecipientNotificationBuilder(context, notificationPrivacy);
    List<NotificationItem>             notifications       = notificationState.getNotifications();
    Recipient                          recipient           = notifications.get(0).getRecipient();
    boolean                            shouldAlert         = signal && (isReminder || Stream.of(notifications).anyMatch(item -> item.getNotifiedTimestamp() == 0));
    int                                notificationId;

    if (Build.VERSION.SDK_INT >= 23) {
      notificationId = NotificationIds.getNotificationIdForThread(notifications.get(0).getThreadId());
    } else {
      notificationId = NotificationIds.MESSAGE_SUMMARY;
    }

    builder.setThread(notifications.get(0).getRecipient());
    builder.setMessageCount(notificationState.getMessageCount());
    builder.setPrimaryMessageBody(recipient, notifications.get(0).getIndividualRecipient(),
                                  notifications.get(0).getText(), notifications.get(0).getSlideDeck());
    builder.setContentIntent(notifications.get(0).getPendingIntent(context));
    builder.setDeleteIntent(notificationState.getDeleteIntent(context));
    builder.setOnlyAlertOnce(!shouldAlert);
    builder.setSortKey(String.valueOf(Long.MAX_VALUE - notifications.get(0).getTimestamp()));
    builder.setDefaultBubbleState(defaultBubbleState);

    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    boolean isSingleNotificationContactJoined = notifications.size() == 1 && notifications.get(0).isJoin();

    if (notificationPrivacy.isDisplayMessage() &&
        !KeyCachingService.isLocked(context)   &&
        RecipientUtil.isMessageRequestAccepted(context, recipient.resolve()))
    {
      ReplyMethod replyMethod = ReplyMethod.forRecipient(context, recipient);

      builder.addActions(notificationState.getMarkAsReadIntent(context, notificationId),
                         notificationState.getQuickReplyIntent(context, notifications.get(0).getRecipient()),
                         notificationState.getRemoteReplyIntent(context, notifications.get(0).getRecipient(), replyMethod),
                         replyMethod,
                         !isSingleNotificationContactJoined && notificationState.canReply());

      builder.addAndroidAutoAction(notificationState.getAndroidAutoReplyIntent(context, notifications.get(0).getRecipient()),
                                   notificationState.getAndroidAutoHeardIntent(context, notificationId), notifications.get(0).getTimestamp());
    }

    if (!KeyCachingService.isLocked(context) && isSingleNotificationContactJoined) {
      builder.addTurnOffTheseNotificationsAction(notificationState.getTurnOffTheseNotificationsIntent(context));
    }

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());

    while(iterator.hasPrevious()) {
      NotificationItem item = iterator.previous();
      builder.addMessageBody(item.getRecipient(), item.getIndividualRecipient(), item.getText(), item.getTimestamp(), item.getSlideDeck());
    }

    if (signal) {
      builder.setAlarms(notificationState.getRingtone(context), notificationState.getVibrate());
      builder.setTicker(notifications.get(0).getIndividualRecipient(),
                        notifications.get(0).getText());
    }

    if (Build.VERSION.SDK_INT >= 23) {
      builder.setGroup(NOTIFICATION_GROUP);
      builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN);
    }

    Notification notification = builder.build();
    try {
      NotificationManagerCompat.from(context).notify(notificationId, notification);
      Log.i(TAG, "Posted notification.");
    } catch (SecurityException e) {
      Uri defaultValue = TextSecurePreferences.getNotificationRingtone(context);
      if (!defaultValue.equals(notificationState.getRingtone(context))) {
        Log.e(TAG, "Security exception when posting notification with custom ringtone", e);
        clearNotificationRingtone(context, notifications.get(0).getRecipient());
      } else {
        throw e;
      }
    }

    return shouldAlert;
  }

  private static void sendMultipleThreadNotification(@NonNull Context context,
                                                     @NonNull NotificationState notificationState,
                                                     boolean signal)
  {
    Log.i(TAG, "sendMultiThreadNotification()  signal: " + signal);

    if (notificationState.getNotifications().isEmpty()) {
      Log.i(TAG, "[sendMultiThreadNotification] Empty notification state. Skipping.");
      return;
    }

    NotificationPrivacyPreference        notificationPrivacy = TextSecurePreferences.getNotificationPrivacy(context);
    MultipleRecipientNotificationBuilder builder             = new MultipleRecipientNotificationBuilder(context, notificationPrivacy);
    List<NotificationItem>               notifications       = notificationState.getNotifications();
    boolean                              shouldAlert         = signal && Stream.of(notifications).anyMatch(item -> item.getNotifiedTimestamp() == 0);

    builder.setMessageCount(notificationState.getMessageCount(), notificationState.getThreadCount());
    builder.setMostRecentSender(notifications.get(0).getIndividualRecipient());
    builder.setDeleteIntent(notificationState.getDeleteIntent(context));
    builder.setOnlyAlertOnce(!shouldAlert);

    if (Build.VERSION.SDK_INT >= 23) {
      builder.setGroup(NOTIFICATION_GROUP);
      builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN);
    }

    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    if (notificationPrivacy.isDisplayMessage()) {
      builder.addActions(notificationState.getMarkAsReadIntent(context, NotificationIds.MESSAGE_SUMMARY));
    }

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());

    while(iterator.hasPrevious()) {
      NotificationItem item = iterator.previous();
      builder.addMessageBody(item.getIndividualRecipient(), item.getText());
    }

    if (signal) {
      builder.setAlarms(notificationState.getRingtone(context), notificationState.getVibrate());
      builder.setTicker(notifications.get(0).getIndividualRecipient(),
                        notifications.get(0).getText());
    }

    Notification notification = builder.build();

    try {
      NotificationManagerCompat.from(context).notify(NotificationIds.MESSAGE_SUMMARY, builder.build());
      Log.i(TAG, "Posted notification. " + notification.toString());
    } catch (SecurityException securityException) {
      Uri defaultValue = TextSecurePreferences.getNotificationRingtone(context);
      if (!defaultValue.equals(notificationState.getRingtone(context))) {
        Log.e(TAG, "Security exception when posting notification with custom ringtone", securityException);
        clearNotificationRingtone(context, notifications.get(0).getRecipient());
      } else {
        throw securityException;
      }
    } catch (RuntimeException runtimeException) {
      Throwable cause = runtimeException.getCause();
      if (cause instanceof TransactionTooLargeException) {
        Log.e(TAG, "Transaction too large", runtimeException);
      } else {
        throw runtimeException;
      }
    }
  }

  private static void sendInThreadNotification(Context context, Recipient recipient) {
    if (!TextSecurePreferences.isInThreadNotifications(context) ||
        ServiceUtil.getAudioManager(context).getRingerMode() != AudioManager.RINGER_MODE_NORMAL)
    {
      return;
    }

    Uri uri = null;
    if (recipient != null) {
      uri = NotificationChannels.supported() ? NotificationChannels.getMessageRingtone(context, recipient) : recipient.getMessageRingtone();
    }

    if (uri == null) {
      uri = NotificationChannels.supported() ? NotificationChannels.getMessageRingtone(context) : TextSecurePreferences.getNotificationRingtone(context);
    }

    if (uri.toString().isEmpty()) {
      Log.d(TAG, "ringtone uri is empty");
      return;
    }

    Ringtone ringtone = RingtoneManager.getRingtone(context, uri);

    if (ringtone == null) {
      Log.w(TAG, "ringtone is null");
      return;
    }

    if (Build.VERSION.SDK_INT >= 21) {
      ringtone.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                                                               .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                                                               .build());
    } else {
      ringtone.setStreamType(AudioManager.STREAM_NOTIFICATION);
    }

    ringtone.play();
  }

  private static NotificationState constructNotificationState(@NonNull  Context context,
                                                              @NonNull  Cursor cursor)
  {
    NotificationState     notificationState = new NotificationState();
    MmsSmsDatabase.Reader reader            = DatabaseFactory.getMmsSmsDatabase(context).readerFor(cursor);

    MessageRecord record;

    while ((record = reader.getNext()) != null) {
      long         id                    = record.getId();
      boolean      mms                   = record.isMms() || record.isMmsNotification();
      Recipient    recipient             = record.getIndividualRecipient().resolve();
      Recipient    conversationRecipient = record.getRecipient().resolve();
      long         threadId              = record.getThreadId();
      CharSequence body                  = MentionUtil.updateBodyWithDisplayNames(context, record);
      Recipient    threadRecipients      = null;
      SlideDeck    slideDeck             = null;
      long         timestamp             = record.getTimestamp();
      long         receivedTimestamp     = record.getDateReceived();
      boolean      isUnreadMessage       = cursor.getInt(cursor.getColumnIndexOrThrow(MmsSmsColumns.READ)) == 0;
      boolean      hasUnreadReactions    = cursor.getInt(cursor.getColumnIndexOrThrow(MmsSmsColumns.REACTIONS_UNREAD)) == 1;
      long         lastReactionRead      = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.REACTIONS_LAST_SEEN));
      long         notifiedTimestamp     = record.getNotifiedTimestamp();

      if (threadId != -1) {
        threadRecipients = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId);
      }

      if (isUnreadMessage) {
        boolean canReply = false;

        if (KeyCachingService.isLocked(context)) {
          body = SpanUtil.italic(context.getString(R.string.MessageNotifier_locked_message));
        } else if (record.isMms() && !((MmsMessageRecord) record).getSharedContacts().isEmpty()) {
          Contact contact = ((MmsMessageRecord) record).getSharedContacts().get(0);
          body = ContactUtil.getStringSummary(context, contact);
        } else if (record.isMms() && ((MmsMessageRecord) record).isViewOnce()) {
          body = SpanUtil.italic(context.getString(getViewOnceDescription((MmsMessageRecord) record)));
        } else if (record.isRemoteDelete()) {
          body = SpanUtil.italic(context.getString(R.string.MessageNotifier_this_message_was_deleted));;
        } else if (record.isMms() && !record.isMmsNotification() && !((MmsMessageRecord) record).getSlideDeck().getSlides().isEmpty()) {
          body      = ThreadBodyUtil.getFormattedBodyFor(context, record);
          slideDeck = ((MmsMessageRecord) record).getSlideDeck();
          canReply  = true;
        } else if (record.isGroupCall()) {
          body     = new SpannableString(MessageRecord.getGroupCallUpdateDescription(context, record.getBody(), false).getString());
          canReply = false;
        } else {
          canReply  = true;
        }

        boolean includeMessage = true;
        if (threadRecipients != null && threadRecipients.isMuted()) {
          boolean mentionsOverrideMute = threadRecipients.getMentionSetting() == RecipientDatabase.MentionSetting.ALWAYS_NOTIFY;

          includeMessage = mentionsOverrideMute && record.hasSelfMention();
        }

        if (threadRecipients == null || includeMessage) {
          notificationState.addNotification(new NotificationItem(id, mms, recipient, conversationRecipient, threadRecipients, threadId, body, timestamp, receivedTimestamp, slideDeck, false, record.isJoined(), canReply, notifiedTimestamp));
        }
      }

      if (hasUnreadReactions) {
        CharSequence originalBody = body;
        for (ReactionRecord reaction : record.getReactions()) {
          Recipient reactionSender = Recipient.resolved(reaction.getAuthor());
          if (reactionSender.equals(Recipient.self()) || !record.isOutgoing() || reaction.getDateReceived() <= lastReactionRead) {
            continue;
          }

          if (KeyCachingService.isLocked(context)) {
            body = SpanUtil.italic(context.getString(R.string.MessageNotifier_locked_message));
          } else {
            String   text  = SpanUtil.italic(getReactionMessageBody(context, record, originalBody)).toString();
            String[] parts = text.split(EMOJI_REPLACEMENT_STRING);

            SpannableStringBuilder builder = new SpannableStringBuilder();
            for (int i = 0; i < parts.length; i++) {
              builder.append(SpanUtil.italic(parts[i]));

              if (i != parts.length -1) {
                builder.append(reaction.getEmoji());
              }
            }

            if (text.endsWith(EMOJI_REPLACEMENT_STRING)) {
              builder.append(reaction.getEmoji());
            }

            body = builder;
          }

          if (threadRecipients == null || !threadRecipients.isMuted()) {
            notificationState.addNotification(new NotificationItem(id, mms, reactionSender, conversationRecipient, threadRecipients, threadId, body, reaction.getDateReceived(), receivedTimestamp, null, true, record.isJoined(), false, 0));
          }
        }
      }
    }

    reader.close();
    return notificationState;
  }

  private static CharSequence getReactionMessageBody(@NonNull Context context, @NonNull MessageRecord record, @NonNull CharSequence body) {
    boolean bodyIsEmpty = TextUtils.isEmpty(body);

    if (MessageRecordUtil.hasSharedContact(record)) {
      Contact       contact = ((MmsMessageRecord) record).getSharedContacts().get(0);
      CharSequence  summary = ContactUtil.getStringSummary(context, contact);

      return context.getString(R.string.MessageNotifier_reacted_s_to_s, EMOJI_REPLACEMENT_STRING, summary);
    } else if (MessageRecordUtil.hasSticker(record)) {
      return context.getString(R.string.MessageNotifier_reacted_s_to_your_sticker, EMOJI_REPLACEMENT_STRING);
    } else if (record.isMms() && record.isViewOnce()){
      return context.getString(R.string.MessageNotifier_reacted_s_to_your_view_once_media, EMOJI_REPLACEMENT_STRING);
    } else if (!bodyIsEmpty) {
      return context.getString(R.string.MessageNotifier_reacted_s_to_s, EMOJI_REPLACEMENT_STRING, body);
    } else if (MessageRecordUtil.isMediaMessage(record) && MediaUtil.isVideoType(getMessageContentType((MmsMessageRecord) record))) {
      return context.getString(R.string.MessageNotifier_reacted_s_to_your_video, EMOJI_REPLACEMENT_STRING);
    } else if (MessageRecordUtil.isMediaMessage(record) && MediaUtil.isImageType(getMessageContentType((MmsMessageRecord) record))) {
      return context.getString(R.string.MessageNotifier_reacted_s_to_your_image, EMOJI_REPLACEMENT_STRING);
    } else if (MessageRecordUtil.isMediaMessage(record) && MediaUtil.isAudioType(getMessageContentType((MmsMessageRecord) record))) {
      return context.getString(R.string.MessageNotifier_reacted_s_to_your_audio, EMOJI_REPLACEMENT_STRING);
    } else if (MessageRecordUtil.isMediaMessage(record)) {
      return context.getString(R.string.MessageNotifier_reacted_s_to_your_file, EMOJI_REPLACEMENT_STRING);
    } else {
      return context.getString(R.string.MessageNotifier_reacted_s_to_s, EMOJI_REPLACEMENT_STRING, body);
    }
  }

  private static @StringRes int getViewOnceDescription(@NonNull MmsMessageRecord messageRecord) {
    final String contentType = getMessageContentType(messageRecord);

    if (MediaUtil.isImageType(contentType)) {
      return R.string.MessageNotifier_view_once_photo;
    }
    return R.string.MessageNotifier_view_once_video;
  }

  private static String getMessageContentType(@NonNull MmsMessageRecord messageRecord) {
    Slide thumbnailSlide = messageRecord.getSlideDeck().getThumbnailSlide();
    if (thumbnailSlide == null) {
      String slideContentType = messageRecord.getSlideDeck().getFirstSlideContentType();
      if (slideContentType != null) {
        return slideContentType;
      }
      Log.w(TAG, "Could not distinguish view-once content type from message record, defaulting to JPEG");
      return MediaUtil.IMAGE_JPEG;
    }
    return thumbnailSlide.getContentType();
  }

  private static void updateBadge(Context context, int count) {
    try {
      if (count == 0) ShortcutBadger.removeCount(context);
      else            ShortcutBadger.applyCount(context, count);
    } catch (Throwable t) {
      // NOTE :: I don't totally trust this thing, so I'm catching
      // everything.
      Log.w(TAG, t);
    }
  }

  private static void scheduleReminder(Context context, int count) {
    if (count >= TextSecurePreferences.getRepeatAlertsCount(context)) {
      return;
    }

    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    Intent       alarmIntent  = new Intent(context, ReminderReceiver.class);
    alarmIntent.putExtra("reminder_count", count);

    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    long          timeout       = TimeUnit.MINUTES.toMillis(2);

    alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeout, pendingIntent);
  }

  private static void clearNotificationRingtone(@NonNull Context context, @NonNull Recipient recipient) {
    SignalExecutors.BOUNDED.execute(() -> {
      DatabaseFactory.getRecipientDatabase(context).setMessageRingtone(recipient.getId(), null);
      NotificationChannels.updateMessageRingtone(context, recipient, null);
    });
  }

  @Override
  public void clearReminder(@NonNull Context context) {
    Intent        alarmIntent   = new Intent(context, ReminderReceiver.class);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    AlarmManager  alarmManager  = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

    alarmManager.cancel(pendingIntent);
  }

  private static class DelayedNotification implements Runnable {

    private static final long DELAY = TimeUnit.SECONDS.toMillis(5);

    private final AtomicBoolean canceled = new AtomicBoolean(false);

    private final Context context;
    private final long    threadId;
    private final long    delayUntil;

    private DelayedNotification(Context context, long threadId) {
      this.context    = context;
      this.threadId   = threadId;
      this.delayUntil = System.currentTimeMillis() + DELAY;
    }

    @Override
    public void run() {
      long delayMillis = delayUntil - System.currentTimeMillis();
      Log.i(TAG, "Waiting to notify: " + delayMillis);

      if (delayMillis > 0) {
        Util.sleep(delayMillis);
      }

      if (!canceled.get()) {
        Log.i(TAG, "Not canceled, notifying...");
        ApplicationDependencies.getMessageNotifier().updateNotification(context, threadId, true);
        ApplicationDependencies.getMessageNotifier().cancelDelayedNotifications();
      } else {
        Log.w(TAG, "Canceled, not notifying...");
      }
    }

    public void cancel() {
      canceled.set(true);
    }
  }

  private static class CancelableExecutor {

    private final Executor                 executor = Executors.newSingleThreadExecutor();
    private final Set<DelayedNotification> tasks    = new HashSet<>();

    public void execute(final DelayedNotification runnable) {
      synchronized (tasks) {
        tasks.add(runnable);
      }

      Runnable wrapper = () -> {
        runnable.run();

        synchronized (tasks) {
          tasks.remove(runnable);
        }
      };

      executor.execute(wrapper);
    }

    public void cancel() {
      synchronized (tasks) {
        for (DelayedNotification task : tasks) {
          task.cancel();
        }
      }
    }
  }
}
