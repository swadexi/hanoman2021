package xyz.hanoman.messenger.util.viewholders;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.components.AvatarImageView;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.util.MappingAdapter;
import xyz.hanoman.messenger.util.MappingViewHolder;

public class RecipientViewHolder<T extends RecipientMappingModel<T>> extends MappingViewHolder<T> {

  protected final @Nullable AvatarImageView  avatar;
  protected final @Nullable TextView         name;
  protected final @Nullable EventListener<T> eventListener;

  public RecipientViewHolder(@NonNull View itemView, @Nullable EventListener<T> eventListener) {
    super(itemView);
    this.eventListener = eventListener;

    avatar = findViewById(R.id.recipient_view_avatar);
    name   = findViewById(R.id.recipient_view_name);
  }

  @Override
  public void bind(@NonNull T model) {
    if (avatar != null) {
      avatar.setRecipient(model.getRecipient());
    }

    if (name != null) {
      name.setText(model.getName(context));
    }

    if (eventListener != null) {
      itemView.setOnClickListener(v -> eventListener.onModelClick(model));
    } else {
      itemView.setOnClickListener(null);
    }
  }

  public static @NonNull <T extends RecipientMappingModel<T>> MappingAdapter.Factory<T> createFactory(@LayoutRes int layout, @Nullable EventListener<T> listener) {
    return new MappingAdapter.LayoutFactory<>(view -> new RecipientViewHolder<>(view, listener), layout);
  }

  public interface EventListener<T extends RecipientMappingModel<T>> {
    default void onModelClick(@NonNull T model) {
      onClick(model.getRecipient());
    }

    void onClick(@NonNull Recipient recipient);
  }
}
