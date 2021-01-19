package xyz.hanoman.messenger.groups.ui.creategroup;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.annimon.stream.Stream;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.ContactSelectionActivity;
import xyz.hanoman.messenger.ContactSelectionListFragment;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.contacts.ContactsCursorLoader;
import xyz.hanoman.messenger.contacts.sync.DirectoryHelper;
import xyz.hanoman.messenger.database.RecipientDatabase;
import xyz.hanoman.messenger.groups.GroupsV2CapabilityChecker;
import xyz.hanoman.messenger.groups.ui.creategroup.details.AddGroupDetailsActivity;
import xyz.hanoman.messenger.keyvalue.SignalStore;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.util.FeatureFlags;
import xyz.hanoman.messenger.util.Stopwatch;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import xyz.hanoman.messenger.util.ViewUtil;
import xyz.hanoman.messenger.util.concurrent.SimpleTask;
import xyz.hanoman.messenger.util.views.SimpleProgressDialog;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CreateGroupActivity extends ContactSelectionActivity {

  private static final String TAG = Log.tag(CreateGroupActivity.class);

  private static final short REQUEST_CODE_ADD_DETAILS = 17275;

  private ExtendedFloatingActionButton next;
  private ValueAnimator                padStart;
  private ValueAnimator                padEnd;

  public static Intent newIntent(@NonNull Context context) {
    Intent intent = new Intent(context, CreateGroupActivity.class);

    intent.putExtra(ContactSelectionListFragment.REFRESHABLE, false);
    intent.putExtra(ContactSelectionActivity.EXTRA_LAYOUT_RES_ID, R.layout.create_group_activity);

    int displayMode = TextSecurePreferences.isSmsEnabled(context) ? ContactsCursorLoader.DisplayMode.FLAG_SMS | ContactsCursorLoader.DisplayMode.FLAG_PUSH
                                                                  : ContactsCursorLoader.DisplayMode.FLAG_PUSH;

    intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, displayMode);
    intent.putExtra(ContactSelectionListFragment.SELECTION_LIMITS, FeatureFlags.groupLimits().excludingSelf());

    return intent;
  }

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    super.onCreate(bundle, ready);
    assert getSupportActionBar() != null;
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    next = findViewById(R.id.next);
    extendSkip();

    next.setOnClickListener(v -> handleNextPressed());
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == REQUEST_CODE_ADD_DETAILS && resultCode == RESULT_OK) {
      finish();
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public boolean onBeforeContactSelected(Optional<RecipientId> recipientId, String number) {
    if (contactsFragment.hasQueryFilter()) {
      getToolbar().clear();
    }

    shrinkSkip();

    return true;
  }

  @Override
  public void onContactDeselected(Optional<RecipientId> recipientId, String number) {
    if (contactsFragment.hasQueryFilter()) {
      getToolbar().clear();
    }

    if (contactsFragment.getSelectedContactsCount() == 0) {
      extendSkip();
    }
  }

  private void extendSkip() {
    next.setIconGravity(MaterialButton.ICON_GRAVITY_END);
    next.extend();
    animatePadding(24, 18);
  }

  private void shrinkSkip() {
    next.setIconGravity(MaterialButton.ICON_GRAVITY_START);
    next.shrink();
    animatePadding(16, 16);
  }

  private void animatePadding(int startDp, int endDp) {
    if (padStart != null) padStart.cancel();

    padStart = ValueAnimator.ofInt(next.getPaddingStart(), ViewUtil.dpToPx(startDp)).setDuration(200);
    padStart.addUpdateListener(animation -> {
      ViewUtil.setPaddingStart(next, (Integer) animation.getAnimatedValue());
    });
    padStart.start();

    if (padEnd != null) padEnd.cancel();

    padEnd = ValueAnimator.ofInt(next.getPaddingEnd(), ViewUtil.dpToPx(endDp)).setDuration(200);
    padEnd.addUpdateListener(animation -> {
      ViewUtil.setPaddingEnd(next, (Integer) animation.getAnimatedValue());
    });
    padEnd.start();
  }

  private void handleNextPressed() {
    Stopwatch                              stopwatch         = new Stopwatch("Recipient Refresh");
    SimpleProgressDialog.DismissibleDialog dismissibleDialog = SimpleProgressDialog.showDelayed(this);

    SimpleTask.run(getLifecycle(), () -> {
      List<RecipientId> ids = Stream.of(contactsFragment.getSelectedContacts())
                                    .map(selectedContact -> selectedContact.getOrCreateRecipientId(this))
                                    .toList();

      List<Recipient> resolved = Recipient.resolvedList(ids);

      stopwatch.split("resolve");

      List<Recipient> registeredChecks = Stream.of(resolved)
                                               .filter(r -> r.getRegistered() == RecipientDatabase.RegisteredState.UNKNOWN)
                                               .toList();

      Log.i(TAG, "Need to do " + registeredChecks.size() + " registration checks.");

      for (Recipient recipient : registeredChecks) {
        try {
          DirectoryHelper.refreshDirectoryFor(this, recipient, false);
        } catch (IOException e) {
          Log.w(TAG, "Failed to refresh registered status for " + recipient.getId(), e);
        }
      }

      stopwatch.split("registered");

      List<Recipient> recipientsAndSelf = new ArrayList<>(resolved);
      recipientsAndSelf.add(Recipient.self().resolve());

      if (!SignalStore.internalValues().gv2DoNotCreateGv2Groups()) {
        try {
          GroupsV2CapabilityChecker.refreshCapabilitiesIfNecessary(recipientsAndSelf);
        } catch (IOException e) {
          Log.w(TAG, "Failed to refresh all recipient capabilities.", e);
        }
      }

      stopwatch.split("capabilities");

      resolved = Recipient.resolvedList(ids);

      Pair<Boolean, List<RecipientId>> result;

      boolean gv2 = Stream.of(recipientsAndSelf).allMatch(r -> r.getGroupsV2Capability() == Recipient.Capability.SUPPORTED);
      if (!gv2 && Stream.of(resolved).anyMatch(r -> !r.hasE164()))
      {
        Log.w(TAG, "Invalid GV1 group...");
        ids = Collections.emptyList();
        result = Pair.create(false, ids);
      } else {
        result = Pair.create(true, ids);
      }

      stopwatch.split("gv1-check");

      return result;
    }, result -> {
      dismissibleDialog.dismiss();

      stopwatch.stop(TAG);

      if (result.first) {
        startActivityForResult(AddGroupDetailsActivity.newIntent(this, result.second), REQUEST_CODE_ADD_DETAILS);
      } else {
        new AlertDialog.Builder(this)
                       .setMessage(R.string.CreateGroupActivity_some_contacts_cannot_be_in_legacy_groups)
                       .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                       .show();
      }
    });
  }
}
