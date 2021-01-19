package xyz.hanoman.messenger.jobs;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.mms.pdu_alt.CharacterSets;
import com.google.android.mms.pdu_alt.EncodedStringValue;
import com.google.android.mms.pdu_alt.PduBody;
import com.google.android.mms.pdu_alt.PduPart;
import com.google.android.mms.pdu_alt.RetrieveConf;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.attachments.Attachment;
import xyz.hanoman.messenger.attachments.UriAttachment;
import xyz.hanoman.messenger.contactshare.Contact;
import xyz.hanoman.messenger.contactshare.VCardUtil;
import xyz.hanoman.messenger.database.AttachmentDatabase;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.MessageDatabase;
import xyz.hanoman.messenger.database.MessageDatabase.InsertResult;
import xyz.hanoman.messenger.database.MmsDatabase;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.groups.GroupId;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.mms.ApnUnavailableException;
import xyz.hanoman.messenger.mms.CompatMmsConnection;
import xyz.hanoman.messenger.mms.IncomingMediaMessage;
import xyz.hanoman.messenger.mms.MmsException;
import xyz.hanoman.messenger.mms.MmsRadioException;
import xyz.hanoman.messenger.mms.PartParser;
import xyz.hanoman.messenger.providers.BlobProvider;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.service.KeyCachingService;
import xyz.hanoman.messenger.util.MediaUtil;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import xyz.hanoman.messenger.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class MmsDownloadJob extends BaseJob {

  public static final String KEY = "MmsDownloadJob";

  private static final String TAG = MmsDownloadJob.class.getSimpleName();

  private static final String KEY_MESSAGE_ID = "message_id";
  private static final String KEY_THREAD_ID  = "thread_id";
  private static final String KEY_AUTOMATIC  = "automatic";

  private long    messageId;
  private long    threadId;
  private boolean automatic;

  public MmsDownloadJob(long messageId, long threadId, boolean automatic) {
    this(new Job.Parameters.Builder()
                           .setQueue("mms-operation")
                           .setMaxAttempts(25)
                           .build(),
         messageId,
         threadId,
         automatic);

  }

  private MmsDownloadJob(@NonNull Job.Parameters parameters, long messageId, long threadId, boolean automatic) {
    super(parameters);

    this.messageId = messageId;
    this.threadId  = threadId;
    this.automatic = automatic;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId)
                             .putLong(KEY_THREAD_ID, threadId)
                             .putBoolean(KEY_AUTOMATIC, automatic)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    if (automatic && KeyCachingService.isLocked(context)) {
      DatabaseFactory.getMmsDatabase(context).markIncomingNotificationReceived(threadId);
      ApplicationDependencies.getMessageNotifier().updateNotification(context);
    }
  }

  @Override
  public void onRun() {
    if (TextSecurePreferences.getLocalUuid(context) == null && TextSecurePreferences.getLocalNumber(context) == null) {
      throw new NotReadyException();
    }

    MessageDatabase                           database     = DatabaseFactory.getMmsDatabase(context);
    Optional<MmsDatabase.MmsNotificationInfo> notification = database.getNotification(messageId);

    if (!notification.isPresent()) {
      Log.w(TAG, "No notification for ID: " + messageId);
      return;
    }

    try {
      if (notification.get().getContentLocation() == null) {
        throw new MmsException("Notification content location was null.");
      }

      if (!TextSecurePreferences.isPushRegistered(context)) {
        throw new MmsException("Not registered");
      }

      database.markDownloadState(messageId, MmsDatabase.Status.DOWNLOAD_CONNECTING);

      String contentLocation = notification.get().getContentLocation();
      byte[] transactionId   = new byte[0];

      try {
        if (notification.get().getTransactionId() != null) {
          transactionId = notification.get().getTransactionId().getBytes(CharacterSets.MIMENAME_ISO_8859_1);
        } else {
          Log.w(TAG, "No transaction ID!");
        }
      } catch (UnsupportedEncodingException e) {
        Log.w(TAG, e);
      }

      Log.i(TAG, "Downloading mms at " + Uri.parse(contentLocation).getHost() + ", subscription ID: " + notification.get().getSubscriptionId());

      RetrieveConf retrieveConf = new CompatMmsConnection(context).retrieve(contentLocation, transactionId, notification.get().getSubscriptionId());

      if (retrieveConf == null) {
        throw new MmsException("RetrieveConf was null");
      }

      storeRetrievedMms(contentLocation, messageId, threadId, retrieveConf, notification.get().getSubscriptionId(), notification.get().getFrom());
    } catch (ApnUnavailableException e) {
      Log.w(TAG, e);
      handleDownloadError(messageId, threadId, MmsDatabase.Status.DOWNLOAD_APN_UNAVAILABLE,
                          automatic);
    } catch (MmsException e) {
      Log.w(TAG, e);
      handleDownloadError(messageId, threadId,
                          MmsDatabase.Status.DOWNLOAD_HARD_FAILURE,
                          automatic);
    } catch (MmsRadioException | IOException e) {
      Log.w(TAG, e);
      handleDownloadError(messageId, threadId,
                          MmsDatabase.Status.DOWNLOAD_SOFT_FAILURE,
                          automatic);
    }
  }

  @Override
  public void onFailure() {
    MessageDatabase database = DatabaseFactory.getMmsDatabase(context);
    database.markDownloadState(messageId, MmsDatabase.Status.DOWNLOAD_SOFT_FAILURE);

    if (automatic) {
      database.markIncomingNotificationReceived(threadId);
      ApplicationDependencies.getMessageNotifier().updateNotification(context, threadId);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return false;
  }

  private void storeRetrievedMms(String contentLocation,
                                 long messageId, long threadId, RetrieveConf retrieved,
                                 int subscriptionId, @Nullable RecipientId notificationFrom)
      throws MmsException
  {
    MessageDatabase   database    = DatabaseFactory.getMmsDatabase(context);
    Optional<GroupId> group       = Optional.absent();
    Set<RecipientId>  members     = new HashSet<>();
    String            body        = null;
    List<Attachment>  attachments = new LinkedList<>();
    List<Contact>     sharedContacts = new LinkedList<>();

    RecipientId from = null;

    if (retrieved.getFrom() != null) {
      from = Recipient.external(context, Util.toIsoString(retrieved.getFrom().getTextString())).getId();
    } else if (notificationFrom != null) {
      from = notificationFrom;
    }

    if (retrieved.getTo() != null) {
      for (EncodedStringValue toValue : retrieved.getTo()) {
        members.add(Recipient.external(context, Util.toIsoString(toValue.getTextString())).getId());
      }
    }

    if (retrieved.getCc() != null) {
      for (EncodedStringValue ccValue : retrieved.getCc()) {
        members.add(Recipient.external(context, Util.toIsoString(ccValue.getTextString())).getId());
      }
    }

    if (from != null) {
      members.add(from);
    }
    members.add(Recipient.self().getId());

    if (retrieved.getBody() != null) {
      body = PartParser.getMessageText(retrieved.getBody());
      PduBody media = PartParser.getSupportedMediaParts(retrieved.getBody());

      for (int i=0;i<media.getPartsNum();i++) {
        PduPart part = media.getPart(i);

        if (part.getData() != null) {
          if (Util.toIsoString(part.getContentType()).toLowerCase().equals(MediaUtil.VCARD)){
            sharedContacts.addAll(VCardUtil.parseContacts(new String(part.getData())));
          } else {
            Uri    uri  = BlobProvider.getInstance().forData(part.getData()).createForSingleUseInMemory();
            String name = null;

            if (part.getName() != null) name = Util.toIsoString(part.getName());

            attachments.add(new UriAttachment(uri, Util.toIsoString(part.getContentType()),
                            AttachmentDatabase.TRANSFER_PROGRESS_DONE,
                            part.getData().length, name, false, false, false, null, null, null, null, null));
          }
        }
      }
    }

    if (members.size() > 2) {
      List<RecipientId> recipients = new ArrayList<>(members);
      group = Optional.of(DatabaseFactory.getGroupDatabase(context).getOrCreateMmsGroupForMembers(recipients));
    }

    IncomingMediaMessage   message      = new IncomingMediaMessage(from, group, body, retrieved.getDate() * 1000L, -1, attachments, subscriptionId, 0, false, false, false, Optional.of(sharedContacts));
    Optional<InsertResult> insertResult = database.insertMessageInbox(message, contentLocation, threadId);

    if (insertResult.isPresent()) {
      database.deleteMessage(messageId);
      ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
    }
  }

  private void handleDownloadError(long messageId, long threadId, int downloadStatus, boolean automatic)
  {
    MessageDatabase db = DatabaseFactory.getMmsDatabase(context);

    db.markDownloadState(messageId, downloadStatus);

    if (automatic) {
      db.markIncomingNotificationReceived(threadId);
      ApplicationDependencies.getMessageNotifier().updateNotification(context, threadId);
    }
  }

  public static final class Factory implements Job.Factory<MmsDownloadJob> {
    @Override
    public @NonNull MmsDownloadJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new MmsDownloadJob(parameters,
                                data.getLong(KEY_MESSAGE_ID),
                                data.getLong(KEY_THREAD_ID),
                                data.getBoolean(KEY_AUTOMATIC));
    }
  }

  private static class NotReadyException extends RuntimeException {
  }
}
