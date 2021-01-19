package xyz.hanoman.messenger.sms;

import xyz.hanoman.messenger.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;

public class IncomingJoinedMessage extends IncomingTextMessage {

  public IncomingJoinedMessage(RecipientId sender) {
    super(sender, 1, System.currentTimeMillis(), -1, null, Optional.absent(), 0, false);
  }

  @Override
  public boolean isJoined() {
    return true;
  }

  @Override
  public boolean isSecureMessage() {
    return true;
  }

}
