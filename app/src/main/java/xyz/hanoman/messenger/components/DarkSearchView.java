package xyz.hanoman.messenger.components;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import xyz.hanoman.messenger.R;

/**
 * Custom styled search view that we can insert into ActionBar menus
 */
public class DarkSearchView extends androidx.appcompat.widget.SearchView {
  public DarkSearchView(@NonNull Context context) {
    this(context, null);
  }

  public DarkSearchView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.search_view_style_dark);
  }

  public DarkSearchView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    EditText searchText = findViewById(androidx.appcompat.R.id.search_src_text);
    searchText.setTextColor(ContextCompat.getColor(context, R.color.signal_text_toolbar_subtitle));
  }
}
