package xyz.hanoman.messenger.imageeditor;

public interface UndoRedoStackListener {

  void onAvailabilityChanged(boolean undoAvailable, boolean redoAvailable);
}
