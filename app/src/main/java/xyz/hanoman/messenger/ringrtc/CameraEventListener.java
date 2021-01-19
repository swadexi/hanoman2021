package xyz.hanoman.messenger.ringrtc;

import androidx.annotation.NonNull;

public interface CameraEventListener {
  void onCameraSwitchCompleted(@NonNull CameraState newCameraState);
}
