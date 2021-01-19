package xyz.hanoman.messenger.conversation.ui.mentions;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.util.viewholders.RecipientMappingModel;

public final class MentionViewState extends RecipientMappingModel<MentionViewState> {

  private final Recipient recipient;

  public MentionViewState(@NonNull Recipient recipient) {
    this.recipient = recipient;
  }

  @Override
  public @NonNull Recipient getRecipient() {
    return recipient;
  }
}
