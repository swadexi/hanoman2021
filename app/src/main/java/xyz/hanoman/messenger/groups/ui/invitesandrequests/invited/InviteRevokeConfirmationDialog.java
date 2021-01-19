package xyz.hanoman.messenger.groups.ui.invitesandrequests.invited;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.recipients.Recipient;

final class InviteRevokeConfirmationDialog {

  private InviteRevokeConfirmationDialog() {
  }

  /**
   * Confirms that you want to revoke an invite that you sent.
   */
  static AlertDialog showOwnInviteRevokeConfirmationDialog(@NonNull Context context,
                                                           @NonNull Recipient invitee,
                                                           @NonNull Runnable onRevoke)
  {
    return new AlertDialog.Builder(context)
                          .setMessage(context.getString(R.string.InviteRevokeConfirmationDialog_revoke_own_single_invite,
                                                        invitee.getDisplayName(context)))
                          .setPositiveButton(R.string.yes, (dialog, which) -> onRevoke.run())
                          .setNegativeButton(R.string.no, null)
                          .show();
  }

  /**
   * Confirms that you want to revoke a number of invites that another member sent.
   */
  static AlertDialog showOthersInviteRevokeConfirmationDialog(@NonNull Context context,
                                                              @NonNull Recipient inviter,
                                                              int numberOfInvitations,
                                                              @NonNull Runnable onRevoke)
  {
    return new AlertDialog.Builder(context)
                          .setMessage(context.getResources().getQuantityString(R.plurals.InviteRevokeConfirmationDialog_revoke_others_invites,
                                                                               numberOfInvitations,
                                                                               inviter.getDisplayName(context),
                                                                               numberOfInvitations))
                          .setPositiveButton(R.string.yes, (dialog, which) -> onRevoke.run())
                          .setNegativeButton(R.string.no, null)
                          .show();
  }
}
