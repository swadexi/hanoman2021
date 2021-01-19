package xyz.hanoman.messenger.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.stickers.StickerKeyboardProvider;
import xyz.hanoman.messenger.util.TextSecurePreferences;

public class EmojiToggle extends AppCompatImageButton implements MediaKeyboard.MediaKeyboardListener {

  private Drawable emojiToggle;
  private Drawable stickerToggle;

  private Drawable mediaToggle;
  private Drawable imeToggle;


  public EmojiToggle(Context context) {
    super(context);
    initialize();
  }

  public EmojiToggle(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public EmojiToggle(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public void setToMedia() {
    setImageDrawable(mediaToggle);
  }

  public void setToIme() {
    setImageDrawable(imeToggle);
  }

  private void initialize() {
    this.emojiToggle   = ContextCompat.getDrawable(getContext(), R.drawable.ic_emoji_smiley_24);
    this.stickerToggle = ContextCompat.getDrawable(getContext(), R.drawable.ic_sticker_24);
    this.imeToggle     = ContextCompat.getDrawable(getContext(), R.drawable.ic_keyboard_24);
    this.mediaToggle   = emojiToggle;

    setToMedia();
  }

  public void attach(MediaKeyboard drawer) {
    drawer.setKeyboardListener(this);
  }

  public void setStickerMode(boolean stickerMode) {
    this.mediaToggle = stickerMode ? stickerToggle : emojiToggle;

    if (getDrawable() != imeToggle) {
      setToMedia();
    }
  }

  public boolean isStickerMode() {
    return this.mediaToggle == stickerToggle;
  }

  @Override public void onShown() {
    setToIme();
  }

  @Override public void onHidden() {
    setToMedia();
  }

  @Override
  public void onKeyboardProviderChanged(@NonNull MediaKeyboardProvider provider) {
    setStickerMode(provider instanceof StickerKeyboardProvider);
    TextSecurePreferences.setMediaKeyboardMode(getContext(), (provider instanceof StickerKeyboardProvider) ? TextSecurePreferences.MediaKeyboardMode.STICKER
                                                                                                           : TextSecurePreferences.MediaKeyboardMode.EMOJI);
  }
}
