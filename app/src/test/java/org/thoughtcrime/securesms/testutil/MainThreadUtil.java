package xyz.hanoman.messenger.testutil;

import xyz.hanoman.messenger.util.Util;

import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doCallRealMethod;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

public final class MainThreadUtil {

  private MainThreadUtil() {
  }

  /**
   * Makes {@link Util}'s Main thread assertions pass or fail during tests.
   * <p>
   * Use with {@link org.powermock.modules.junit4.PowerMockRunner} or robolectric with powermock
   * rule and {@code @PrepareForTest(Util.class)}
   */
  public static void setMainThread(boolean isMainThread) {
    mockStatic(Util.class);
    when(Util.isMainThread()).thenReturn(isMainThread);
    try {
      doCallRealMethod().when(Util.class, "assertMainThread");
      doCallRealMethod().when(Util.class, "assertNotMainThread");
    } catch (Exception e) {
      throw new AssertionError();
    }
  }
}
