package xyz.hanoman.messenger.lock.v2;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import xyz.hanoman.messenger.BaseActivity;
import xyz.hanoman.messenger.PassphrasePromptActivity;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.service.KeyCachingService;
import xyz.hanoman.messenger.util.DynamicRegistrationTheme;
import xyz.hanoman.messenger.util.DynamicTheme;

public class KbsMigrationActivity extends BaseActivity {

  public static final int REQUEST_NEW_PIN = CreateKbsPinActivity.REQUEST_NEW_PIN;

  private final DynamicTheme dynamicTheme = new DynamicRegistrationTheme();

  public static Intent createIntent() {
    return new Intent(ApplicationDependencies.getApplication(), KbsMigrationActivity.class);
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    if (KeyCachingService.isLocked(this)) {
      startActivity(getPromptPassphraseIntent());
      finish();
      return;
    }

    dynamicTheme.onCreate(this);

    setContentView(R.layout.kbs_migration_activity);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  private Intent getPromptPassphraseIntent() {
    return getRoutedIntent(PassphrasePromptActivity.class, getIntent());
  }

  private Intent getRoutedIntent(Class<?> destination, @Nullable Intent nextIntent) {
    final Intent intent = new Intent(this, destination);
    if (nextIntent != null)   intent.putExtra("next_intent", nextIntent);
    return intent;
  }
}
