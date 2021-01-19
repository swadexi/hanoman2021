package xyz.hanoman.messenger;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;

import xyz.hanoman.messenger.groups.LiveGroup;
import xyz.hanoman.messenger.groups.ui.GroupMemberEntry;
import xyz.hanoman.messenger.groups.ui.GroupMemberListView;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;

import java.util.List;

public final class GroupMembersDialog {

  private final FragmentActivity fragmentActivity;
  private final Recipient        groupRecipient;

  public GroupMembersDialog(@NonNull FragmentActivity activity,
                            @NonNull Recipient groupRecipient)
  {
    this.fragmentActivity = activity;
    this.groupRecipient   = groupRecipient;
  }

  public void display() {
    AlertDialog dialog = new AlertDialog.Builder(fragmentActivity)
                                        .setTitle(R.string.ConversationActivity_group_members)
                                        .setIcon(R.drawable.ic_group_24)
                                        .setCancelable(true)
                                        .setView(R.layout.dialog_group_members)
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show();

    GroupMemberListView memberListView = dialog.findViewById(R.id.list_members);

    LiveGroup                                   liveGroup   = new LiveGroup(groupRecipient.requireGroupId());
    LiveData<List<GroupMemberEntry.FullMember>> fullMembers = liveGroup.getFullMembers();

    //noinspection ConstantConditions
    fullMembers.observe(fragmentActivity, memberListView::setMembers);

    dialog.setOnDismissListener(d -> fullMembers.removeObservers(fragmentActivity));

    memberListView.setRecipientClickListener(recipient -> {
      dialog.dismiss();
      contactClick(recipient);
    });
  }

  private void contactClick(@NonNull Recipient recipient) {
    RecipientBottomSheetDialogFragment.create(recipient.getId(), groupRecipient.requireGroupId())
                                      .show(fragmentActivity.getSupportFragmentManager(), "BOTTOM");
  }
}
