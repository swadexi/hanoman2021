package xyz.hanoman.messenger.registration.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.ActivityNavigator;

import xyz.hanoman.messenger.MainActivity;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.keyvalue.SignalStore;
import xyz.hanoman.messenger.lock.v2.CreateKbsPinActivity;
import xyz.hanoman.messenger.pin.PinRestoreActivity;
import xyz.hanoman.messenger.profiles.edit.EditProfileActivity;

public final class RegistrationCompleteFragment extends BaseRegistrationFragment {

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_blank, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    FragmentActivity activity = requireActivity();

    if (SignalStore.storageServiceValues().needsAccountRestore()) {
      activity.startActivity(new Intent(activity, PinRestoreActivity.class));
    } else if (!isReregister()) {
      final Intent main    = MainActivity.clearTop(activity);
      final Intent profile = EditProfileActivity.getIntentForUserProfile(activity);

      Intent kbs = CreateKbsPinActivity.getIntentForPinCreate(requireContext());
      activity.startActivity(chainIntents(chainIntents(profile, kbs), main));
    }

    activity.finish();
    ActivityNavigator.applyPopAnimationsToPendingTransition(activity);
  }

  private static Intent chainIntents(@NonNull Intent sourceIntent, @Nullable Intent nextIntent) {
    if (nextIntent != null) sourceIntent.putExtra("next_intent", nextIntent);
    return sourceIntent;
  }
}
