package xyz.hanoman.messenger.util;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.keyvalue.SignalStore;

public class SignalUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

  private static final String TAG = SignalUncaughtExceptionHandler.class.getSimpleName();

  private final Thread.UncaughtExceptionHandler originalHandler;

  public SignalUncaughtExceptionHandler(@NonNull Thread.UncaughtExceptionHandler originalHandler) {
    this.originalHandler = originalHandler;
  }

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    Log.e(TAG, "", e);
    SignalStore.blockUntilAllWritesFinished();
    Log.blockUntilAllWritesFinished();
    ApplicationDependencies.getJobManager().flush();
    originalHandler.uncaughtException(t, e);
  }
}
