package xyz.hanoman.messenger.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.util.ViewUtil;

import java.util.Objects;

public final class FallbackPhoto80dp implements FallbackContactPhoto {

  @DrawableRes private final int drawable80dp;
               private final int backgroundColor;

  public FallbackPhoto80dp(@DrawableRes int drawable80dp, int backgroundColor) {
    this.drawable80dp    = drawable80dp;
    this.backgroundColor = backgroundColor;
  }

  @Override
  public Drawable asDrawable(Context context, int color) {
    return buildDrawable(context);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    return buildDrawable(context);
  }

  @Override
  public Drawable asSmallDrawable(Context context, int color, boolean inverted) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Drawable asCallCard(Context context) {
    throw new UnsupportedOperationException();
  }

  private @NonNull Drawable buildDrawable(@NonNull Context context) {
    Drawable      background      = DrawableCompat.wrap(Objects.requireNonNull(AppCompatResources.getDrawable(context, R.drawable.circle_tintable))).mutate();
    Drawable      foreground      = AppCompatResources.getDrawable(context, drawable80dp);
    Drawable      gradient        = AppCompatResources.getDrawable(context, R.drawable.avatar_gradient);
    LayerDrawable drawable        = new LayerDrawable(new Drawable[]{background, foreground, gradient});
    int           foregroundInset = ViewUtil.dpToPx(24);

    DrawableCompat.setTint(background, backgroundColor);

    drawable.setLayerInset(1, foregroundInset, foregroundInset, foregroundInset, foregroundInset);

    return drawable;
  }
}
