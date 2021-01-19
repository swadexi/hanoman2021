package xyz.hanoman.messenger.mms;


import android.content.Context;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.attachments.Attachment;
import xyz.hanoman.messenger.util.MediaUtil;

/**
 * Slide used for attachments with contentType {@link MediaUtil#VIEW_ONCE}.
 * Attachments will only get this type *after* they've been viewed, or if they were synced from a
 * linked device. Incoming unviewed messages will have the appropriate image/video contentType.
 */
public class ViewOnceSlide extends Slide {

  public ViewOnceSlide(@NonNull Context context, @NonNull Attachment attachment) {
    super(context, attachment);
  }

  @Override
  public boolean hasViewOnce() {
    return true;
  }
}
