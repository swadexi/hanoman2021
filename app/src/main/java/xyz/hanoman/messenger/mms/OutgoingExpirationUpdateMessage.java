package xyz.hanoman.messenger.mms;

import xyz.hanoman.messenger.attachments.Attachment;
import xyz.hanoman.messenger.database.ThreadDatabase;
import xyz.hanoman.messenger.recipients.Recipient;

import java.util.Collections;
import java.util.LinkedList;

public class OutgoingExpirationUpdateMessage extends OutgoingSecureMediaMessage {

  public OutgoingExpirationUpdateMessage(Recipient recipient, long sentTimeMillis, long expiresIn) {
    super(recipient, "", new LinkedList<Attachment>(), sentTimeMillis,
          ThreadDatabase.DistributionTypes.CONVERSATION, expiresIn, false, null, Collections.emptyList(),
          Collections.emptyList(), Collections.emptyList());
  }

  @Override
  public boolean isExpirationUpdate() {
    return true;
  }

}
