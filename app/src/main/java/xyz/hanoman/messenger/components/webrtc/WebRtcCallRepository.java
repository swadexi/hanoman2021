package xyz.hanoman.messenger.components.webrtc;

import android.content.Context;
import android.media.AudioManager;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.SignalExecutors;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.GroupDatabase;
import xyz.hanoman.messenger.database.IdentityDatabase;
import xyz.hanoman.messenger.database.identity.IdentityRecordList;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.util.ServiceUtil;

import java.util.Collections;
import java.util.List;

class WebRtcCallRepository {

  private final Context      context;
  private final AudioManager audioManager;

  WebRtcCallRepository(@NonNull Context context) {
    this.context      = context;
    this.audioManager = ServiceUtil.getAudioManager(ApplicationDependencies.getApplication());
  }

  @NonNull WebRtcAudioOutput getAudioOutput() {
    if (audioManager.isBluetoothScoOn()) {
      return WebRtcAudioOutput.HEADSET;
    } else if (audioManager.isSpeakerphoneOn()) {
      return WebRtcAudioOutput.SPEAKER;
    } else {
      return WebRtcAudioOutput.HANDSET;
    }
  }

  @WorkerThread
  void getIdentityRecords(@NonNull Recipient recipient, @NonNull Consumer<IdentityRecordList> consumer) {
    SignalExecutors.BOUNDED.execute(() -> {
      IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(context);
      List<Recipient>  recipients;

      if (recipient.isGroup()) {
        recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(recipient.requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
      } else {
        recipients = Collections.singletonList(recipient);
      }

      consumer.accept(identityDatabase.getIdentities(recipients));
    });
  }
}
