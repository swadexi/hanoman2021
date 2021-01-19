package xyz.hanoman.messenger.giph.ui;


import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.loader.content.Loader;

import xyz.hanoman.messenger.giph.model.GiphyImage;
import xyz.hanoman.messenger.giph.net.GiphyStickerLoader;

import java.util.List;

public class GiphyStickerFragment extends GiphyFragment {
  @Override
  public @NonNull Loader<List<GiphyImage>> onCreateLoader(int id, Bundle args) {
    return new GiphyStickerLoader(getActivity(), searchString);
  }
}
