package xyz.hanoman.messenger.pin;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.BuildConfig;
import xyz.hanoman.messenger.KbsEnclave;
import xyz.hanoman.messenger.util.Util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class KbsEnclaves {

  public static @NonNull KbsEnclave current() {
    return BuildConfig.KBS_ENCLAVE;
  }

  public static @NonNull List<KbsEnclave> all() {
    return Util.join(Collections.singletonList(BuildConfig.KBS_ENCLAVE), fallbacks());
  }

  public static @NonNull List<KbsEnclave> fallbacks() {
    return Arrays.asList(BuildConfig.KBS_FALLBACKS);
  }
}
