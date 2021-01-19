package xyz.hanoman.messenger.conversation.ui.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import xyz.hanoman.messenger.conversation.ui.error.SafetyNumberChangeRepository.SafetyNumberChangeState;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.util.livedata.LiveDataUtil;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class SafetyNumberChangeViewModel extends ViewModel {

  private final SafetyNumberChangeRepository             safetyNumberChangeRepository;
  private final MutableLiveData<Collection<RecipientId>> recipientIds;
  private final LiveData<SafetyNumberChangeState>        safetyNumberChangeState;

  private SafetyNumberChangeViewModel(@NonNull List<RecipientId> recipientIds,
                                      @Nullable Long messageId,
                                      @Nullable String messageType,
                                      @NonNull SafetyNumberChangeRepository safetyNumberChangeRepository)
  {
    this.safetyNumberChangeRepository = safetyNumberChangeRepository;
    this.recipientIds                 = new MutableLiveData<>(recipientIds);
    this.safetyNumberChangeState      = LiveDataUtil.mapAsync(this.recipientIds, ids -> this.safetyNumberChangeRepository.getSafetyNumberChangeState(ids, messageId, messageType));
  }

  @NonNull LiveData<List<ChangedRecipient>> getChangedRecipients() {
    return Transformations.map(safetyNumberChangeState, SafetyNumberChangeState::getChangedRecipients);
  }

  @NonNull LiveData<TrustAndVerifyResult> trustOrVerifyChangedRecipients() {
    SafetyNumberChangeState state = Objects.requireNonNull(safetyNumberChangeState.getValue());
    if (state.getMessageRecord() != null) {
      return safetyNumberChangeRepository.trustOrVerifyChangedRecipientsAndResend(state.getChangedRecipients(), state.getMessageRecord());
    } else {
      return safetyNumberChangeRepository.trustOrVerifyChangedRecipients(state.getChangedRecipients());
    }
  }

  void updateRecipients(Collection<RecipientId> recipientIds) {
    this.recipientIds.setValue(recipientIds);
  }

  public static final class Factory implements ViewModelProvider.Factory {
    private final List<RecipientId> recipientIds;
    private final Long              messageId;
    private final String            messageType;

    public Factory(@NonNull List<RecipientId> recipientIds, @Nullable Long messageId, @Nullable String messageType) {
      this.recipientIds = recipientIds;
      this.messageId    = messageId;
      this.messageType  = messageType;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      SafetyNumberChangeRepository repo = new SafetyNumberChangeRepository(ApplicationDependencies.getApplication());
      return Objects.requireNonNull(modelClass.cast(new SafetyNumberChangeViewModel(recipientIds, messageId, messageType, repo)));
    }
  }
}
