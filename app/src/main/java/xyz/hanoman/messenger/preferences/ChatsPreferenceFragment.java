package xyz.hanoman.messenger.preferences;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import org.greenrobot.eventbus.EventBus;
import xyz.hanoman.messenger.ApplicationPreferencesActivity;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.keyvalue.SignalStore;
import xyz.hanoman.messenger.permissions.Permissions;
import xyz.hanoman.messenger.storage.StorageSyncHelper;
import xyz.hanoman.messenger.util.ConversationUtil;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import xyz.hanoman.messenger.util.ThrottledDebouncer;

public class ChatsPreferenceFragment extends ListSummaryPreferenceFragment {
  private static final String PREFER_SYSTEM_CONTACT_PHOTOS = "pref_system_contact_photos";

  private final ThrottledDebouncer refreshDebouncer = new ThrottledDebouncer(500);

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    findPreference(TextSecurePreferences.MESSAGE_BODY_TEXT_SIZE_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());

    findPreference(TextSecurePreferences.BACKUP).setOnPreferenceClickListener(unused -> {
      goToBackupsPreferenceFragment();
      return true;
    });

    findPreference(PREFER_SYSTEM_CONTACT_PHOTOS)
        .setOnPreferenceChangeListener((preference, newValue) -> {
          SignalStore.settings().setPreferSystemContactPhotos(newValue == Boolean.TRUE);
          refreshDebouncer.publish(ConversationUtil::refreshRecipientShortcuts);
          StorageSyncHelper.scheduleSyncForDataChange();
          return true;
        });

    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.MESSAGE_BODY_TEXT_SIZE_PREF));
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_chats);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity)getActivity()).getSupportActionBar().setTitle(R.string.preferences_chats__chats);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  private void goToBackupsPreferenceFragment() {
    ((ApplicationPreferencesActivity) requireActivity()).pushFragment(new BackupsPreferenceFragment());
  }

  public static CharSequence getSummary(Context context) {
    return null;
  }
}
