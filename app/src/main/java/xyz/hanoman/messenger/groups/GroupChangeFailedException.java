package xyz.hanoman.messenger.groups;

import androidx.annotation.NonNull;

public final class GroupChangeFailedException extends GroupChangeException {

  GroupChangeFailedException() {
  }

  GroupChangeFailedException(@NonNull Throwable throwable) {
    super(throwable);
  }

  GroupChangeFailedException(@NonNull String message) {
    super(message);
  }
}
