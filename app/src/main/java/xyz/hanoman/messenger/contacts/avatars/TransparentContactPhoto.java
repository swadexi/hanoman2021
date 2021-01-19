package xyz.hanoman.messenger.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.makeramen.roundedimageview.RoundedDrawable;

import xyz.hanoman.messenger.R;

public class TransparentContactPhoto implements FallbackContactPhoto {

  public TransparentContactPhoto() {}

  @Override
  public Drawable asDrawable(Context context, int color) {
    return asDrawable(context, color, false);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    return RoundedDrawable.fromDrawable(context.getResources().getDrawable(android.R.color.transparent));
  }

  @Override
  public Drawable asSmallDrawable(Context context, int color, boolean inverted) {
    return asDrawable(context, color, inverted);
  }

  @Override
  public Drawable asCallCard(Context context) {
    return ContextCompat.getDrawable(context, R.drawable.ic_contact_picture_large);
  }

}
