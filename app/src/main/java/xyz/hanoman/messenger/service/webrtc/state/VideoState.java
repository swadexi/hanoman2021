package xyz.hanoman.messenger.service.webrtc.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.hanoman.messenger.components.webrtc.BroadcastVideoSink;
import xyz.hanoman.messenger.ringrtc.Camera;
import org.webrtc.EglBase;

import java.util.Objects;

/**
 * Local device video state and infrastructure.
 */
public final class VideoState {
  EglBase            eglBase;
  BroadcastVideoSink localSink;
  Camera             camera;

  VideoState() {
    this(null, null, null);
  }

  VideoState(@NonNull VideoState toCopy) {
    this(toCopy.eglBase, toCopy.localSink, toCopy.camera);
  }

  VideoState(@Nullable EglBase eglBase, @Nullable BroadcastVideoSink localSink, @Nullable Camera camera) {
    this.eglBase   = eglBase;
    this.localSink = localSink;
    this.camera    = camera;
  }

  public @Nullable EglBase getEglBase() {
    return eglBase;
  }

  public @NonNull EglBase requireEglBase() {
    return Objects.requireNonNull(eglBase);
  }

  public @Nullable BroadcastVideoSink getLocalSink() {
    return localSink;
  }

  public @NonNull BroadcastVideoSink requireLocalSink() {
    return Objects.requireNonNull(localSink);
  }

  public @Nullable Camera getCamera() {
    return camera;
  }

  public @NonNull Camera requireCamera() {
    return Objects.requireNonNull(camera);
  }
}
