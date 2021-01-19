package xyz.hanoman.messenger.pin;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import xyz.hanoman.messenger.MainActivity;
import xyz.hanoman.messenger.PassphraseRequiredActivity;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.lock.v2.CreateKbsPinActivity;

public final class PinRestoreActivity extends AppCompatActivity {

  @Override
  protected void attachBaseContext(@NonNull Context newBase) {
    getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    super.attachBaseContext(newBase);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.pin_restore_activity);
  }

  void navigateToPinCreation() {
    final Intent main      = MainActivity.clearTop(this);
    final Intent createPin = CreateKbsPinActivity.getIntentForPinCreate(this);
    final Intent chained   = PassphraseRequiredActivity.chainIntent(createPin, main);

    startActivity(chained);
  }
}
