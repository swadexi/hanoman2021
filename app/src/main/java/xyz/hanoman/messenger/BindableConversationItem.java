package xyz.hanoman.messenger;

import android.net.Uri;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import xyz.hanoman.messenger.components.voice.VoiceNotePlaybackState;
import xyz.hanoman.messenger.contactshare.Contact;
import xyz.hanoman.messenger.conversation.ConversationMessage;
import xyz.hanoman.messenger.database.model.MessageRecord;
import xyz.hanoman.messenger.database.model.MmsMessageRecord;
import xyz.hanoman.messenger.groups.GroupId;
import xyz.hanoman.messenger.groups.GroupMigrationMembershipChange;
import xyz.hanoman.messenger.linkpreview.LinkPreview;
import xyz.hanoman.messenger.mms.GlideRequests;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.stickers.StickerLocator;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public interface BindableConversationItem extends Unbindable {
  void bind(@NonNull LifecycleOwner lifecycleOwner,
            @NonNull ConversationMessage messageRecord,
            @NonNull Optional<MessageRecord> previousMessageRecord,
            @NonNull Optional<MessageRecord> nextMessageRecord,
            @NonNull GlideRequests glideRequests,
            @NonNull Locale locale,
            @NonNull Set<ConversationMessage> batchSelected,
            @NonNull Recipient recipients,
            @Nullable String searchQuery,
            boolean pulseMention);

  ConversationMessage getConversationMessage();

  void setEventListener(@Nullable EventListener listener);

  interface EventListener {
    void onQuoteClicked(MmsMessageRecord messageRecord);
    void onLinkPreviewClicked(@NonNull LinkPreview linkPreview);
    void onMoreTextClicked(@NonNull RecipientId conversationRecipientId, long messageId, boolean isMms);
    void onStickerClicked(@NonNull StickerLocator stickerLocator);
    void onViewOnceMessageClicked(@NonNull MmsMessageRecord messageRecord);
    void onSharedContactDetailsClicked(@NonNull Contact contact, @NonNull View avatarTransitionView);
    void onAddToContactsClicked(@NonNull Contact contact);
    void onMessageSharedContactClicked(@NonNull List<Recipient> choices);
    void onInviteSharedContactClicked(@NonNull List<Recipient> choices);
    void onReactionClicked(@NonNull View reactionTarget, long messageId, boolean isMms);
    void onGroupMemberClicked(@NonNull RecipientId recipientId, @NonNull GroupId groupId);
    void onMessageWithErrorClicked(@NonNull MessageRecord messageRecord);
    void onRegisterVoiceNoteCallbacks(@NonNull Observer<VoiceNotePlaybackState> onPlaybackStartObserver);
    void onUnregisterVoiceNoteCallbacks(@NonNull Observer<VoiceNotePlaybackState> onPlaybackStartObserver);
    void onVoiceNotePause(@NonNull Uri uri);
    void onVoiceNotePlay(@NonNull Uri uri, long messageId, double position);
    void onVoiceNoteSeekTo(@NonNull Uri uri, double position);
    void onGroupMigrationLearnMoreClicked(@NonNull GroupMigrationMembershipChange membershipChange);
    void onDecryptionFailedLearnMoreClicked();
    void onSafetyNumberLearnMoreClicked(@NonNull Recipient recipient);
    void onJoinGroupCallClicked();
    void onInviteFriendsToGroupClicked(@NonNull GroupId.V2 groupId);

    /** @return true if handled, false if you want to let the normal url handling continue */
    boolean onUrlClicked(@NonNull String url);
  }
}
