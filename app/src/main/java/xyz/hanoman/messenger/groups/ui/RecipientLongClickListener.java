package xyz.hanoman.messenger.groups.ui;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.recipients.Recipient;

public interface RecipientLongClickListener {
  boolean onLongClick(@NonNull Recipient recipient);
}
