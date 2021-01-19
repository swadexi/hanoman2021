package xyz.hanoman.messenger.util;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.crypto.storage.TextSecureIdentityKeyStore;
import xyz.hanoman.messenger.crypto.storage.TextSecureSessionStore;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.GroupDatabase;
import xyz.hanoman.messenger.database.IdentityDatabase;
import xyz.hanoman.messenger.database.IdentityDatabase.IdentityRecord;
import xyz.hanoman.messenger.database.MessageDatabase;
import xyz.hanoman.messenger.database.MessageDatabase.InsertResult;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.sms.IncomingIdentityDefaultMessage;
import xyz.hanoman.messenger.sms.IncomingIdentityUpdateMessage;
import xyz.hanoman.messenger.sms.IncomingIdentityVerifiedMessage;
import xyz.hanoman.messenger.sms.IncomingTextMessage;
import xyz.hanoman.messenger.sms.OutgoingIdentityDefaultMessage;
import xyz.hanoman.messenger.sms.OutgoingIdentityVerifiedMessage;
import xyz.hanoman.messenger.sms.OutgoingTextMessage;
import xyz.hanoman.messenger.util.concurrent.ListenableFuture;
import xyz.hanoman.messenger.util.concurrent.SettableFuture;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;

import java.util.List;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

public class IdentityUtil {

  private static final String TAG = IdentityUtil.class.getSimpleName();

  public static ListenableFuture<Optional<IdentityRecord>> getRemoteIdentityKey(final Context context, final Recipient recipient) {
    final SettableFuture<Optional<IdentityRecord>> future = new SettableFuture<>();

    new AsyncTask<Recipient, Void, Optional<IdentityRecord>>() {
      @Override
      protected Optional<IdentityRecord> doInBackground(Recipient... recipient) {
        return DatabaseFactory.getIdentityDatabase(context)
                              .getIdentity(recipient[0].getId());
      }

      @Override
      protected void onPostExecute(Optional<IdentityRecord> result) {
        future.set(result);
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipient);

    return future;
  }

  public static void markIdentityVerified(Context context, Recipient recipient, boolean verified, boolean remote)
  {
    long            time          = System.currentTimeMillis();
    MessageDatabase smsDatabase   = DatabaseFactory.getSmsDatabase(context);
    GroupDatabase   groupDatabase = DatabaseFactory.getGroupDatabase(context);

    try (GroupDatabase.Reader reader = groupDatabase.getGroups()) {

      GroupDatabase.GroupRecord groupRecord;

      while ((groupRecord = reader.getNext()) != null) {
        if (groupRecord.getMembers().contains(recipient.getId()) && groupRecord.isActive() && !groupRecord.isMms()) {

          if (remote) {
            IncomingTextMessage incoming = new IncomingTextMessage(recipient.getId(), 1, time, -1, null, Optional.of(groupRecord.getId()), 0, false);

            if (verified) incoming = new IncomingIdentityVerifiedMessage(incoming);
            else          incoming = new IncomingIdentityDefaultMessage(incoming);

            smsDatabase.insertMessageInbox(incoming);
          } else {
            RecipientId         recipientId    = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupRecord.getId());
            Recipient           groupRecipient = Recipient.resolved(recipientId);
            long                threadId       = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
            OutgoingTextMessage outgoing ;

            if (verified) outgoing = new OutgoingIdentityVerifiedMessage(recipient);
            else          outgoing = new OutgoingIdentityDefaultMessage(recipient);

            DatabaseFactory.getSmsDatabase(context).insertMessageOutbox(threadId, outgoing, false, time, null);
          }
        }
      }
    }

    if (remote) {
      IncomingTextMessage incoming = new IncomingTextMessage(recipient.getId(), 1, time, -1, null, Optional.absent(), 0, false);

      if (verified) incoming = new IncomingIdentityVerifiedMessage(incoming);
      else          incoming = new IncomingIdentityDefaultMessage(incoming);

      smsDatabase.insertMessageInbox(incoming);
    } else {
      OutgoingTextMessage outgoing;

      if (verified) outgoing = new OutgoingIdentityVerifiedMessage(recipient);
      else          outgoing = new OutgoingIdentityDefaultMessage(recipient);

      long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);

      Log.i(TAG, "Inserting verified outbox...");
      DatabaseFactory.getSmsDatabase(context).insertMessageOutbox(threadId, outgoing, false, time, null);
    }
  }

  public static void markIdentityUpdate(@NonNull Context context, @NonNull RecipientId recipientId) {
    long            time          = System.currentTimeMillis();
    MessageDatabase smsDatabase   = DatabaseFactory.getSmsDatabase(context);
    GroupDatabase   groupDatabase = DatabaseFactory.getGroupDatabase(context);

    try (GroupDatabase.Reader reader = groupDatabase.getGroups()) {
      GroupDatabase.GroupRecord groupRecord;

      while ((groupRecord = reader.getNext()) != null) {
        if (groupRecord.getMembers().contains(recipientId) && groupRecord.isActive()) {
          IncomingTextMessage           incoming    = new IncomingTextMessage(recipientId, 1, time, time, null, Optional.of(groupRecord.getId()), 0, false);
          IncomingIdentityUpdateMessage groupUpdate = new IncomingIdentityUpdateMessage(incoming);

          smsDatabase.insertMessageInbox(groupUpdate);
        }
      }
    }

    IncomingTextMessage           incoming         = new IncomingTextMessage(recipientId, 1, time, -1, null, Optional.absent(), 0, false);
    IncomingIdentityUpdateMessage individualUpdate = new IncomingIdentityUpdateMessage(incoming);
    Optional<InsertResult>        insertResult     = smsDatabase.insertMessageInbox(individualUpdate);

    if (insertResult.isPresent()) {
      ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
    }
  }

  public static void saveIdentity(Context context, String user, IdentityKey identityKey) {
    synchronized (SESSION_LOCK) {
      IdentityKeyStore      identityKeyStore = new TextSecureIdentityKeyStore(context);
      SessionStore          sessionStore     = new TextSecureSessionStore(context);
      SignalProtocolAddress address          = new SignalProtocolAddress(user, 1);

      if (identityKeyStore.saveIdentity(address, identityKey)) {
        if (sessionStore.containsSession(address)) {
          SessionRecord sessionRecord = sessionStore.loadSession(address);
          sessionRecord.archiveCurrentState();

          sessionStore.storeSession(address, sessionRecord);
        }
      }
    }
  }

  public static void processVerifiedMessage(Context context, VerifiedMessage verifiedMessage) {
    synchronized (SESSION_LOCK) {
      IdentityDatabase         identityDatabase = DatabaseFactory.getIdentityDatabase(context);
      Recipient                recipient        = Recipient.externalPush(context, verifiedMessage.getDestination());
      Optional<IdentityRecord> identityRecord   = identityDatabase.getIdentity(recipient.getId());

      if (!identityRecord.isPresent() && verifiedMessage.getVerified() == VerifiedMessage.VerifiedState.DEFAULT) {
        Log.w(TAG, "No existing record for default status");
        return;
      }

      if (verifiedMessage.getVerified() == VerifiedMessage.VerifiedState.DEFAULT              &&
          identityRecord.isPresent()                                                          &&
          identityRecord.get().getIdentityKey().equals(verifiedMessage.getIdentityKey())      &&
          identityRecord.get().getVerifiedStatus() != IdentityDatabase.VerifiedStatus.DEFAULT)
      {
        identityDatabase.setVerified(recipient.getId(), identityRecord.get().getIdentityKey(), IdentityDatabase.VerifiedStatus.DEFAULT);
        markIdentityVerified(context, recipient, false, true);
      }

      if (verifiedMessage.getVerified() == VerifiedMessage.VerifiedState.VERIFIED &&
          (!identityRecord.isPresent() ||
              (identityRecord.isPresent() && !identityRecord.get().getIdentityKey().equals(verifiedMessage.getIdentityKey())) ||
              (identityRecord.isPresent() && identityRecord.get().getVerifiedStatus() != IdentityDatabase.VerifiedStatus.VERIFIED)))
      {
        saveIdentity(context, verifiedMessage.getDestination().getIdentifier(), verifiedMessage.getIdentityKey());
        identityDatabase.setVerified(recipient.getId(), verifiedMessage.getIdentityKey(), IdentityDatabase.VerifiedStatus.VERIFIED);
        markIdentityVerified(context, recipient, true, true);
      }
    }
  }


  public static @Nullable String getUnverifiedBannerDescription(@NonNull Context context,
                                                                @NonNull List<Recipient> unverified)
  {
    return getPluralizedIdentityDescription(context, unverified,
                                            R.string.IdentityUtil_unverified_banner_one,
                                            R.string.IdentityUtil_unverified_banner_two,
                                            R.string.IdentityUtil_unverified_banner_many);
  }

  public static @Nullable String getUnverifiedSendDialogDescription(@NonNull Context context,
                                                                    @NonNull List<Recipient> unverified)
  {
    return getPluralizedIdentityDescription(context, unverified,
                                            R.string.IdentityUtil_unverified_dialog_one,
                                            R.string.IdentityUtil_unverified_dialog_two,
                                            R.string.IdentityUtil_unverified_dialog_many);
  }

  public static @Nullable String getUntrustedSendDialogDescription(@NonNull Context context,
                                                                   @NonNull List<Recipient> untrusted)
  {
    return getPluralizedIdentityDescription(context, untrusted,
                                            R.string.IdentityUtil_untrusted_dialog_one,
                                            R.string.IdentityUtil_untrusted_dialog_two,
                                            R.string.IdentityUtil_untrusted_dialog_many);
  }

  private static @Nullable String getPluralizedIdentityDescription(@NonNull Context context,
                                                                   @NonNull List<Recipient> recipients,
                                                                   @StringRes int resourceOne,
                                                                   @StringRes int resourceTwo,
                                                                   @StringRes int resourceMany)
  {
    if (recipients.isEmpty()) return null;

    if (recipients.size() == 1) {
      String name = recipients.get(0).getDisplayName(context);
      return context.getString(resourceOne, name);
    } else {
      String firstName  = recipients.get(0).getDisplayName(context);
      String secondName = recipients.get(1).getDisplayName(context);

      if (recipients.size() == 2) {
        return context.getString(resourceTwo, firstName, secondName);
      } else {
        int    othersCount = recipients.size() - 2;
        String nMore       = context.getResources().getQuantityString(R.plurals.identity_others, othersCount, othersCount);

        return context.getString(resourceMany, firstName, secondName, nMore);
      }
    }
  }
}
