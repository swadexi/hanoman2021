package xyz.hanoman.messenger.messagedetails;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import xyz.hanoman.messenger.color.MaterialColor;
import xyz.hanoman.messenger.database.model.MessageRecord;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;

import java.util.Objects;

final class MessageDetailsViewModel extends ViewModel {

  private final LiveData<Recipient>      recipient;
  private final LiveData<MessageDetails> messageDetails;

  private MessageDetailsViewModel(RecipientId recipientId, String type, Long messageId) {
    recipient = Recipient.live(recipientId).getLiveData();

    MessageDetailsRepository repository    = new MessageDetailsRepository();
    LiveData<MessageRecord>  messageRecord = repository.getMessageRecord(type, messageId);

    messageDetails = Transformations.switchMap(messageRecord, repository::getMessageDetails);
  }

  @NonNull LiveData<MaterialColor> getRecipientColor() {
    return Transformations.distinctUntilChanged(Transformations.map(recipient, Recipient::getColor));
  }

  @NonNull LiveData<MessageDetails> getMessageDetails() {
    return messageDetails;
  }

  static final class Factory implements ViewModelProvider.Factory {

    private final RecipientId recipientId;
    private final String      type;
    private final Long        messageId;

    Factory(RecipientId recipientId, String type, Long messageId) {
      this.recipientId = recipientId;
      this.type        = type;
      this.messageId   = messageId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new MessageDetailsViewModel(recipientId, type, messageId)));
    }
  }
}
