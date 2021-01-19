package xyz.hanoman.messenger.stickers;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.mms.GlideRequests;
import xyz.hanoman.messenger.util.ViewUtil;
import org.whispersystems.libsignal.util.Pair;

import java.lang.ref.WeakReference;

public class StickerRolloverTouchListener implements RecyclerView.OnItemTouchListener {
  private final StickerPreviewPopup      popup;
  private final RolloverEventListener    eventListener;
  private final RolloverStickerRetriever stickerRetriever;

  private WeakReference<View> currentView;
  private boolean             hoverMode;

  StickerRolloverTouchListener(@NonNull Context context,
                               @NonNull GlideRequests glideRequests,
                               @NonNull RolloverEventListener eventListener,
                               @NonNull RolloverStickerRetriever stickerRetriever)
  {
    this.eventListener    = eventListener;
    this.stickerRetriever = stickerRetriever;
    this.popup            = new StickerPreviewPopup(context, glideRequests);
    this.currentView      = new WeakReference<>(null);

    popup.setAnimationStyle(R.style.StickerPopupAnimation);
  }

  @Override
  public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent motionEvent) {
    return hoverMode;
  }

  @Override
  public void onTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent motionEvent) {
    switch (motionEvent.getAction()) {
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        hoverMode = false;
        popup.dismiss();
        eventListener.onStickerPopupEnded();
        currentView.clear();
        break;
      default:
        for (int i = 0, len = recyclerView.getChildCount(); i < len; i++) {
          View child = recyclerView.getChildAt(i);

          if (ViewUtil.isPointInsideView(recyclerView, motionEvent.getRawX(), motionEvent.getRawY()) &&
              ViewUtil.isPointInsideView(child, motionEvent.getRawX(), motionEvent.getRawY())        &&
              child != currentView.get())
          {
            showStickerForView(recyclerView, child);
            currentView = new WeakReference<>(child);
            break;
          }
        }
    }
  }

  @Override
  public void onRequestDisallowInterceptTouchEvent(boolean b) {
  }

  void enterHoverMode(@NonNull RecyclerView recyclerView, View targetView) {
    this.hoverMode = true;
    showStickerForView(recyclerView, targetView);
  }

  private void showStickerForView(@NonNull RecyclerView recyclerView, @NonNull View view) {
    Pair<Object, String> stickerData = stickerRetriever.getStickerDataFromView(view);

    if (stickerData != null) {
      if (!popup.isShowing()) {
        popup.showAtLocation(recyclerView, Gravity.NO_GRAVITY, 0, 0);
        eventListener.onStickerPopupStarted();
      }
      popup.presentSticker(stickerData.first(), stickerData.second());
    }
  }

  public interface RolloverEventListener {
    void onStickerPopupStarted();
    void onStickerPopupEnded();
  }

  public interface RolloverStickerRetriever {
    @Nullable Pair<Object, String> getStickerDataFromView(@NonNull View view);
  }
}
