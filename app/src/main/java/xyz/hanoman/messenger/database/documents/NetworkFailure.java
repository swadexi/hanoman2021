package xyz.hanoman.messenger.database.documents;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;

import java.util.Objects;

public class NetworkFailure {

  /** DEPRECATED */
  @JsonProperty(value = "a")
  private String address;

  @JsonProperty(value = "r")
  private String recipientId;

  public NetworkFailure(@NonNull RecipientId recipientId) {
    this.recipientId = recipientId.serialize();
    this.address = "";
  }

  public NetworkFailure() {}

  @JsonIgnore
  public RecipientId getRecipientId(@NonNull Context context) {
    if (!TextUtils.isEmpty(recipientId)) {
      return RecipientId.from(recipientId);
    } else {
      return Recipient.external(context, address).getId();
    }
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NetworkFailure that = (NetworkFailure) o;
    return Objects.equals(address, that.address) &&
        Objects.equals(recipientId, that.recipientId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(address, recipientId);
  }
}
