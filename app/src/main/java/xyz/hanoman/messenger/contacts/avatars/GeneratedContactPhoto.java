package xyz.hanoman.messenger.contacts.avatars;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.amulyakhare.textdrawable.TextDrawable;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.util.ContextUtil;
import xyz.hanoman.messenger.util.ViewUtil;

import java.util.regex.Pattern;

public class GeneratedContactPhoto implements FallbackContactPhoto {

  private static final Pattern  PATTERN  = Pattern.compile("[^\\p{L}\\p{Nd}\\p{S}]+");
  private static final Typeface TYPEFACE = Typeface.create("sans-serif-medium", Typeface.NORMAL);

  private final String name;
  private final int    fallbackResId;
  private final int    targetSize;
  private final int    fontSize;

  public GeneratedContactPhoto(@NonNull String name, @DrawableRes int fallbackResId) {
    this(name, fallbackResId, -1, ViewUtil.dpToPx(24));
  }

  public GeneratedContactPhoto(@NonNull String name, @DrawableRes int fallbackResId, int targetSize, int fontSize) {
    this.name          = name;
    this.fallbackResId = fallbackResId;
    this.targetSize    = targetSize;
    this.fontSize      = fontSize;
  }

  @Override
  public Drawable asDrawable(Context context, int color) {
    return asDrawable(context, color,false);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    int targetSize = this.targetSize != -1
                     ? this.targetSize
                     : context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size);

    String character = getAbbreviation(name);

    if (!TextUtils.isEmpty(character)) {
      Drawable base = TextDrawable.builder()
                                  .beginConfig()
                                  .width(targetSize)
                                  .height(targetSize)
                                  .useFont(TYPEFACE)
                                  .fontSize(fontSize)
                                  .textColor(inverted ? color : Color.WHITE)
                                  .endConfig()
                                  .buildRound(character, inverted ? Color.WHITE : color);

      Drawable gradient = ContextUtil.requireDrawable(context, R.drawable.avatar_gradient);
      return new LayerDrawable(new Drawable[] { base, gradient });
    }

    return newFallbackDrawable(context, color, inverted);
  }

  @Override
  public Drawable asSmallDrawable(Context context, int color, boolean inverted) {
    return asDrawable(context, color, inverted);
  }

  protected @DrawableRes int getFallbackResId() {
    return fallbackResId;
  }

  protected Drawable newFallbackDrawable(@NonNull Context context, int color, boolean inverted) {
    return new ResourceContactPhoto(fallbackResId).asDrawable(context, color, inverted);
  }

  private @Nullable String getAbbreviation(String name) {
    String[]      parts   = name.split(" ");
    StringBuilder builder = new StringBuilder();
    int           count   = 0;

    for (int i = 0; i < parts.length && count < 2; i++) {
      String cleaned = PATTERN.matcher(parts[i]).replaceFirst("");
      if (!TextUtils.isEmpty(cleaned)) {
        builder.appendCodePoint(cleaned.codePointAt(0));
        count++;
      }
    }

    if (builder.length() == 0) {
      return null;
    } else {
      return builder.toString();
    }
  }

  @Override
  public Drawable asCallCard(Context context) {
    return AppCompatResources.getDrawable(context, R.drawable.ic_person_large);

  }
}
