package xyz.hanoman.messenger.util;

import android.content.Context;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.push.SignalServiceNetworkAccess;

public final class CensorshipUtil {

  private CensorshipUtil() {}

  public static boolean isCensored(@NonNull Context context) {
    return new SignalServiceNetworkAccess(context).isCensored(context);
  }
}
