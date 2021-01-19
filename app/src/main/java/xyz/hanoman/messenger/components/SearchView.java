package xyz.hanoman.messenger.components;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.hanoman.messenger.R;

/**
 * Custom styled search view that we can insert into ActionBar menus
 */
public class SearchView extends androidx.appcompat.widget.SearchView {
  public SearchView(@NonNull Context context) {
    this(context, null);
  }

  public SearchView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.search_view_style);
  }

  public SearchView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }
}
