package xyz.hanoman.messenger.groups.ui;

import androidx.annotation.NonNull;

public interface GroupChangeErrorCallback {
  void onError(@NonNull GroupChangeFailureReason failureReason);
}
