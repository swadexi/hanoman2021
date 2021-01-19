package xyz.hanoman.messenger.groups.ui.creategroup.dialogs;

import android.app.Dialog;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.groups.ui.GroupMemberEntry;
import xyz.hanoman.messenger.groups.ui.GroupMemberListView;
import xyz.hanoman.messenger.recipients.Recipient;

import java.util.ArrayList;
import java.util.List;

public final class NonGv2MemberDialog {

  private NonGv2MemberDialog() {
  }

  public static @Nullable Dialog showNonGv2Members(@NonNull Context context, @NonNull List<Recipient> recipients, boolean forcedMigration) {
    int size = recipients.size();
    if (size == 0) {
      return null;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(context)
                                                 // TODO: GV2 Need a URL for learn more
                                                 //  .setNegativeButton(R.string.NonGv2MemberDialog_learn_more, (dialog, which) -> {
                                                 //  })
                                                 .setPositiveButton(android.R.string.ok, null);
    if (size == 1) {
      int stringRes = forcedMigration ? R.string.NonGv2MemberDialog_single_users_are_non_gv2_capable_forced_migration
                                      : R.string.NonGv2MemberDialog_single_users_are_non_gv2_capable;
      builder.setMessage(context.getString(stringRes, recipients.get(0).getDisplayName(context)));
    } else {
      int pluralRes = forcedMigration ? R.plurals.NonGv2MemberDialog_d_users_are_non_gv2_capable_forced_migration
                                      : R.plurals.NonGv2MemberDialog_d_users_are_non_gv2_capable;
      builder.setMessage(context.getResources().getQuantityString(pluralRes, size, size))
             .setView(R.layout.dialog_multiple_members_non_gv2_capable);
    }

    Dialog dialog = builder.show();
    if (size > 1) {
      GroupMemberListView nonGv2CapableMembers = dialog.findViewById(R.id.list_non_gv2_members);

      List<GroupMemberEntry.NewGroupCandidate> pendingMembers = new ArrayList<>(recipients.size());
      for (Recipient r : recipients) {
        pendingMembers.add(new GroupMemberEntry.NewGroupCandidate(r));
      }

      //noinspection ConstantConditions
      nonGv2CapableMembers.setMembers(pendingMembers);
    }

    return dialog;
  }
}
