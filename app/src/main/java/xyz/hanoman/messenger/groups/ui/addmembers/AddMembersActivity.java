package xyz.hanoman.messenger.groups.ui.addmembers;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProviders;

import xyz.hanoman.messenger.ContactSelectionActivity;
import xyz.hanoman.messenger.PushContactSelectionActivity;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.groups.GroupId;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

public class AddMembersActivity extends PushContactSelectionActivity {

  public static final String GROUP_ID = "group_id";

  private View                done;
  private AddMembersViewModel viewModel;

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    getIntent().putExtra(ContactSelectionActivity.EXTRA_LAYOUT_RES_ID, R.layout.add_members_activity);
    super.onCreate(icicle, ready);

    AddMembersViewModel.Factory factory = new AddMembersViewModel.Factory(getGroupId());

    done      = findViewById(R.id.done);
    viewModel = ViewModelProviders.of(this, factory)
                                  .get(AddMembersViewModel.class);

    done.setOnClickListener(v ->
      viewModel.getDialogStateForSelectedContacts(contactsFragment.getSelectedContacts(), this::displayAlertMessage)
    );

    disableDone();
  }

  @Override
  protected void initializeToolbar() {
    getToolbar().setNavigationIcon(R.drawable.ic_arrow_left_24);
    getToolbar().setNavigationOnClickListener(v -> {
      setResult(RESULT_CANCELED);
      finish();
    });
  }

  @Override
  public boolean onBeforeContactSelected(Optional<RecipientId> recipientId, String number) {
    if (getGroupId().isV1() && recipientId.isPresent() && !Recipient.resolved(recipientId.get()).hasE164()) {
      Toast.makeText(this, R.string.AddMembersActivity__this_person_cant_be_added_to_legacy_groups, Toast.LENGTH_SHORT).show();
      return false;
    }

    if (contactsFragment.hasQueryFilter()) {
      getToolbar().clear();
    }

    enableDone();

    return true;
  }

  @Override
  public void onContactDeselected(Optional<RecipientId> recipientId, String number) {
    if (contactsFragment.hasQueryFilter()) {
      getToolbar().clear();
    }

    if (contactsFragment.getSelectedContactsCount() < 1) {
      disableDone();
    }
  }

  private void enableDone() {
    done.setEnabled(true);
    done.animate().alpha(1f);
  }

  private void disableDone() {
    done.setEnabled(false);
    done.animate().alpha(0.5f);
  }

  private GroupId getGroupId() {
    return GroupId.parseOrThrow(getIntent().getStringExtra(GROUP_ID));
  }

  private void displayAlertMessage(@NonNull AddMembersViewModel.AddMemberDialogMessageState state) {
    Recipient recipient = Util.firstNonNull(state.getRecipient(), Recipient.UNKNOWN);

    String message = getResources().getQuantityString(R.plurals.AddMembersActivity__add_d_members_to_s, state.getSelectionCount(),
                                                      recipient.getDisplayName(this), state.getGroupTitle(), state.getSelectionCount());

    new AlertDialog.Builder(this)
                   .setMessage(message)
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                   .setPositiveButton(R.string.AddMembersActivity__add, (dialog, which) -> {
                     dialog.dismiss();
                     onFinishedSelection();
                   })
                   .setCancelable(true)
                   .show();
  }
}
