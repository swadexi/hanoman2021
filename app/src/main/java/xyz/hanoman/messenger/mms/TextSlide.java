package xyz.hanoman.messenger.mms;


import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.hanoman.messenger.attachments.Attachment;
import xyz.hanoman.messenger.util.MediaUtil;

public class TextSlide extends Slide {

  public TextSlide(@NonNull Context context, @NonNull Attachment attachment) {
    super(context, attachment);
  }

  public TextSlide(@NonNull Context context, @NonNull Uri uri, @Nullable String filename, long size) {
    super(context, constructAttachmentFromUri(context, uri, MediaUtil.LONG_TEXT, size, 0, 0, true, filename, null, null, null, null, false, false, false));
  }
}
