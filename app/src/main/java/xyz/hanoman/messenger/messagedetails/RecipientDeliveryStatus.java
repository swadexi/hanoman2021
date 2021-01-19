package xyz.hanoman.messenger.messagedetails;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.hanoman.messenger.database.documents.IdentityKeyMismatch;
import xyz.hanoman.messenger.database.documents.NetworkFailure;
import xyz.hanoman.messenger.database.model.MessageRecord;
import xyz.hanoman.messenger.recipients.Recipient;

final class RecipientDeliveryStatus {

  enum Status {
    UNKNOWN, PENDING, SENT, DELIVERED, READ
  }

  private final MessageRecord       messageRecord;
  private final Recipient           recipient;
  private final Status              deliveryStatus;
  private final boolean             isUnidentified;
  private final long                timestamp;
  private final NetworkFailure      networkFailure;
  private final IdentityKeyMismatch keyMismatchFailure;

  RecipientDeliveryStatus(@NonNull MessageRecord messageRecord, @NonNull Recipient recipient, @NonNull Status deliveryStatus, boolean isUnidentified, long timestamp, @Nullable NetworkFailure networkFailure, @Nullable IdentityKeyMismatch keyMismatchFailure) {
    this.messageRecord      = messageRecord;
    this.recipient          = recipient;
    this.deliveryStatus     = deliveryStatus;
    this.isUnidentified     = isUnidentified;
    this.timestamp          = timestamp;
    this.networkFailure     = networkFailure;
    this.keyMismatchFailure = keyMismatchFailure;
  }

  @NonNull MessageRecord getMessageRecord() {
    return messageRecord;
  }

  @NonNull Status getDeliveryStatus() {
    return deliveryStatus;
  }

  boolean isUnidentified() {
    return isUnidentified;
  }

  long getTimestamp() {
    return timestamp;
  }

  @NonNull Recipient getRecipient() {
    return recipient;
  }

  @Nullable NetworkFailure getNetworkFailure() {
    return networkFailure;
  }

  @Nullable IdentityKeyMismatch getKeyMismatchFailure() {
    return keyMismatchFailure;
  }
}
