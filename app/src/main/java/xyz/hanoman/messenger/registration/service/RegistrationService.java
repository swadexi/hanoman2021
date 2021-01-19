package xyz.hanoman.messenger.registration.service;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.hanoman.messenger.pin.PinRestoreRepository;

public final class RegistrationService {

  private final Credentials credentials;

  private RegistrationService(@NonNull Credentials credentials) {
    this.credentials = credentials;
  }

  public static RegistrationService getInstance(@NonNull String e164number, @NonNull String password) {
    return new RegistrationService(new Credentials(e164number, password));
  }

  /**
   * See {@link RegistrationCodeRequest}.
   */
  public void requestVerificationCode(@NonNull Activity activity,
                                      @NonNull RegistrationCodeRequest.Mode mode,
                                      @Nullable String captchaToken,
                                      @NonNull RegistrationCodeRequest.SmsVerificationCodeCallback callback)
  {
    RegistrationCodeRequest.requestSmsVerificationCode(activity, credentials, captchaToken, mode, callback);
  }

  /**
   * See {@link CodeVerificationRequest}.
   */
  public void verifyAccount(@NonNull Activity activity,
                            @Nullable String fcmToken,
                            @NonNull String code,
                            @Nullable String pin,
                            @Nullable PinRestoreRepository.TokenData tokenData,
                            @NonNull CodeVerificationRequest.VerifyCallback callback)
  {
    CodeVerificationRequest.verifyAccount(activity, credentials, fcmToken, code, pin, tokenData, callback);
  }
}
