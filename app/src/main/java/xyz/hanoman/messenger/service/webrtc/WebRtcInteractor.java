package xyz.hanoman.messenger.service.webrtc;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.ringrtc.CallManager;
import org.signal.ringrtc.GroupCall;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.ringrtc.CameraEventListener;
import xyz.hanoman.messenger.ringrtc.RemotePeer;
import xyz.hanoman.messenger.service.WebRtcCallService;
import xyz.hanoman.messenger.service.webrtc.state.WebRtcServiceState;
import xyz.hanoman.messenger.webrtc.audio.BluetoothStateManager;
import xyz.hanoman.messenger.webrtc.audio.OutgoingRinger;
import xyz.hanoman.messenger.webrtc.audio.SignalAudioManager;
import xyz.hanoman.messenger.webrtc.locks.LockManager;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

import java.util.Collection;
import java.util.UUID;

/**
 * Serves as the bridge between the action processing framework as the WebRTC service. Attempts
 * to minimize direct access to various managers by providing a simple proxy to them. Due to the
 * heavy use of {@link CallManager} throughout, it was exempted from the rule.
 */
public class WebRtcInteractor {

  @NonNull private final WebRtcCallService     webRtcCallService;
  @NonNull private final CallManager           callManager;
  @NonNull private final LockManager           lockManager;
  @NonNull private final SignalAudioManager    audioManager;
  @NonNull private final BluetoothStateManager bluetoothStateManager;
  @NonNull private final CameraEventListener   cameraEventListener;
  @NonNull private final GroupCall.Observer    groupCallObserver;

  public WebRtcInteractor(@NonNull WebRtcCallService webRtcCallService,
                          @NonNull CallManager callManager,
                          @NonNull LockManager lockManager,
                          @NonNull SignalAudioManager audioManager,
                          @NonNull BluetoothStateManager bluetoothStateManager,
                          @NonNull CameraEventListener cameraEventListener,
                          @NonNull GroupCall.Observer groupCallObserver)
  {
    this.webRtcCallService     = webRtcCallService;
    this.callManager           = callManager;
    this.lockManager           = lockManager;
    this.audioManager          = audioManager;
    this.bluetoothStateManager = bluetoothStateManager;
    this.cameraEventListener   = cameraEventListener;
    this.groupCallObserver     = groupCallObserver;
  }

  @NonNull CameraEventListener getCameraEventListener() {
    return cameraEventListener;
  }

  @NonNull CallManager getCallManager() {
    return callManager;
  }

  @NonNull WebRtcCallService getWebRtcCallService() {
    return webRtcCallService;
  }

  @NonNull GroupCall.Observer getGroupCallObserver() {
    return groupCallObserver;
  }

  void setWantsBluetoothConnection(boolean enabled) {
    bluetoothStateManager.setWantsConnection(enabled);
  }

  void updatePhoneState(@NonNull LockManager.PhoneState phoneState) {
    lockManager.updatePhoneState(phoneState);
  }

  void sendMessage(@NonNull WebRtcServiceState state) {
    webRtcCallService.sendMessage(state);
  }

  void sendCallMessage(@NonNull RemotePeer remotePeer, @NonNull SignalServiceCallMessage callMessage) {
    webRtcCallService.sendCallMessage(remotePeer, callMessage);
  }

  void sendOpaqueCallMessage(@NonNull UUID uuid, @NonNull SignalServiceCallMessage callMessage) {
    webRtcCallService.sendOpaqueCallMessage(uuid, callMessage);
  }

  void sendGroupCallMessage(@NonNull Recipient recipient, @Nullable String groupCallEraId) {
    webRtcCallService.sendGroupCallMessage(recipient, groupCallEraId);
  }

  void updateGroupCallUpdateMessage(@NonNull RecipientId groupId, @Nullable String groupCallEraId, @NonNull Collection<UUID> joinedMembers, boolean isCallFull) {
    webRtcCallService.updateGroupCallUpdateMessage(groupId, groupCallEraId, joinedMembers, isCallFull);
  }

  void setCallInProgressNotification(int type, @NonNull RemotePeer remotePeer) {
    webRtcCallService.setCallInProgressNotification(type, remotePeer.getRecipient());
  }

  void setCallInProgressNotification(int type, @NonNull Recipient recipient) {
    webRtcCallService.setCallInProgressNotification(type, recipient);
  }

  void retrieveTurnServers(@NonNull RemotePeer remotePeer) {
    webRtcCallService.retrieveTurnServers(remotePeer);
  }

  void stopForegroundService() {
    webRtcCallService.stopForeground(true);
  }

  void insertMissedCall(@NonNull RemotePeer remotePeer, boolean signal, long timestamp, boolean isVideoOffer) {
    webRtcCallService.insertMissedCall(remotePeer, signal, timestamp, isVideoOffer);
  }

  void startWebRtcCallActivityIfPossible() {
    webRtcCallService.startCallCardActivityIfPossible();
  }

  void registerPowerButtonReceiver() {
    webRtcCallService.registerPowerButtonReceiver();
  }

  void unregisterPowerButtonReceiver() {
    webRtcCallService.unregisterPowerButtonReceiver();
  }

  void silenceIncomingRinger() {
    audioManager.silenceIncomingRinger();
  }

  void initializeAudioForCall() {
    audioManager.initializeAudioForCall();
  }

  void startIncomingRinger(@Nullable Uri ringtoneUri, boolean vibrate) {
    audioManager.startIncomingRinger(ringtoneUri, vibrate);
  }

  void startOutgoingRinger(@NonNull OutgoingRinger.Type type) {
    audioManager.startOutgoingRinger(type);
  }

  void stopAudio(boolean playDisconnect) {
    audioManager.stop(playDisconnect);
  }

  void startAudioCommunication(boolean preserveSpeakerphone) {
    audioManager.startCommunication(preserveSpeakerphone);
  }

  void peekGroupCall(@NonNull RecipientId recipientId) {
    webRtcCallService.peekGroupCall(recipientId);
  }
}
