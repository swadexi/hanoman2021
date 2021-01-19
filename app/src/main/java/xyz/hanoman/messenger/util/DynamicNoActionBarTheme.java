package xyz.hanoman.messenger.util;

import androidx.annotation.StyleRes;

import xyz.hanoman.messenger.R;

public class DynamicNoActionBarTheme extends DynamicTheme {

  protected @StyleRes int getTheme() {
    return R.style.Signal_DayNight_NoActionBar;
  }
}
