package xyz.hanoman.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.hanoman.messenger.util.AppStartup;
import xyz.hanoman.messenger.util.CachedInflater;
import xyz.hanoman.messenger.util.CommunicationActions;
import xyz.hanoman.messenger.util.DynamicNoActionBarTheme;
import xyz.hanoman.messenger.util.DynamicTheme;

public class MainActivity extends PassphraseRequiredActivity {

  public static final int RESULT_CONFIG_CHANGED = Activity.RESULT_FIRST_USER + 901;

  private final DynamicTheme  dynamicTheme = new DynamicNoActionBarTheme();
  private final MainNavigator navigator    = new MainNavigator(this);

  public static @NonNull Intent clearTop(@NonNull Context context) {
    Intent intent = new Intent(context, MainActivity.class);

    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_NEW_TASK  |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);

    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    AppStartup.getInstance().onCriticalRenderEventStart();
    super.onCreate(savedInstanceState, ready);
    setContentView(R.layout.main_activity);

    navigator.onCreate(savedInstanceState);

    handleGroupLinkInIntent(getIntent());

    CachedInflater.from(this).clear();
  }

  @Override
  public Intent getIntent() {
    return super.getIntent().setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                      Intent.FLAG_ACTIVITY_NEW_TASK  |
                                      Intent.FLAG_ACTIVITY_SINGLE_TOP);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    handleGroupLinkInIntent(intent);
  }

  @Override
  protected void onPreCreate() {
    super.onPreCreate();
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public void onBackPressed() {
    if (!navigator.onBackPressed()) {
      super.onBackPressed();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == MainNavigator.REQUEST_CONFIG_CHANGES && resultCode == RESULT_CONFIG_CHANGED) {
      recreate();
    }
  }

  public @NonNull MainNavigator getNavigator() {
    return navigator;
  }

  private void handleGroupLinkInIntent(Intent intent) {
    Uri data = intent.getData();
    if (data != null) {
      CommunicationActions.handlePotentialGroupLinkUrl(this, data.toString());
    }
  }
}
