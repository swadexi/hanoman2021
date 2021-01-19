package xyz.hanoman.messenger.mms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.hanoman.messenger.attachments.Attachment;
import xyz.hanoman.messenger.contactshare.Contact;
import xyz.hanoman.messenger.database.model.Mention;
import xyz.hanoman.messenger.linkpreview.LinkPreview;
import xyz.hanoman.messenger.recipients.Recipient;

import java.util.Collections;
import java.util.List;

public class OutgoingSecureMediaMessage extends OutgoingMediaMessage {

  public OutgoingSecureMediaMessage(Recipient recipient, String body,
                                    List<Attachment> attachments,
                                    long sentTimeMillis,
                                    int distributionType,
                                    long expiresIn,
                                    boolean viewOnce,
                                    @Nullable QuoteModel quote,
                                    @NonNull List<Contact> contacts,
                                    @NonNull List<LinkPreview> previews,
                                    @NonNull List<Mention> mentions)
  {
    super(recipient, body, attachments, sentTimeMillis, -1, expiresIn, viewOnce, distributionType, quote, contacts, previews, mentions, Collections.emptyList(), Collections.emptyList());
  }

  public OutgoingSecureMediaMessage(OutgoingMediaMessage base) {
    super(base);
  }

  @Override
  public boolean isSecure() {
    return true;
  }
}
