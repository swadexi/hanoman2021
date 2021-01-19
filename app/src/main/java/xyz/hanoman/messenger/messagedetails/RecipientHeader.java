package xyz.hanoman.messenger.messagedetails;

import androidx.annotation.StringRes;

import xyz.hanoman.messenger.R;

enum RecipientHeader {
  PENDING(R.string.message_details_recipient_header__pending_send),
  SENT_TO(R.string.message_details_recipient_header__sent_to),
  SENT_FROM(R.string.message_details_recipient_header__sent_from),
  DELIVERED(R.string.message_details_recipient_header__delivered_to),
  READ(R.string.message_details_recipient_header__read_by),
  NOT_SENT(R.string.message_details_recipient_header__not_sent);

  private final int headerText;

  RecipientHeader(@StringRes int headerText) {
    this.headerText = headerText;
  }

  @StringRes int getHeaderText() {
    return headerText;
  }
}
