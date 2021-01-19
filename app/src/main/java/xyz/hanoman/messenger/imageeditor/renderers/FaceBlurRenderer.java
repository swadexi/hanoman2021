package xyz.hanoman.messenger.imageeditor.renderers;

import android.os.Parcel;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.imageeditor.Bounds;
import xyz.hanoman.messenger.imageeditor.Renderer;
import xyz.hanoman.messenger.imageeditor.RendererContext;

/**
 * A rectangle that will be rendered on the blur mask layer. Intended for blurring faces.
 */
public final class FaceBlurRenderer implements Renderer {

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    rendererContext.canvas.drawRect(Bounds.FULL_BOUNDS, rendererContext.getMaskPaint());
  }

  @Override
  public boolean hitTest(float x, float y) {
    return Bounds.FULL_BOUNDS.contains(x, y);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
  }

  public static final Creator<FaceBlurRenderer> CREATOR = new Creator<FaceBlurRenderer>() {
    @Override
    public FaceBlurRenderer createFromParcel(Parcel in) {
      return new FaceBlurRenderer();
    }

    @Override
    public FaceBlurRenderer[] newArray(int size) {
      return new FaceBlurRenderer[size];
    }
  };
}
