package xyz.hanoman.messenger.groups.ui.invitesandrequests.invited;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.groups.GroupId;
import xyz.hanoman.messenger.groups.ui.AdminActionsListener;
import xyz.hanoman.messenger.groups.ui.GroupMemberEntry;
import xyz.hanoman.messenger.groups.ui.GroupMemberListView;
import xyz.hanoman.messenger.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;
import xyz.hanoman.messenger.util.BottomSheetUtil;

import java.util.Objects;

public class PendingMemberInvitesFragment extends Fragment {

  private static final String GROUP_ID = "GROUP_ID";

  private PendingMemberInvitesViewModel viewModel;
  private GroupMemberListView           youInvited;
  private GroupMemberListView           othersInvited;
  private View                          youInvitedEmptyState;
  private View                          othersInvitedEmptyState;

  public static PendingMemberInvitesFragment newInstance(@NonNull GroupId.V2 groupId) {
    PendingMemberInvitesFragment fragment = new PendingMemberInvitesFragment();
    Bundle                       args     = new Bundle();

    args.putString(GROUP_ID, groupId.toString());
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.group_pending_member_invites_fragment, container, false);

    youInvited              = view.findViewById(R.id.members_you_invited);
    othersInvited           = view.findViewById(R.id.members_others_invited);
    youInvitedEmptyState    = view.findViewById(R.id.no_pending_from_you);
    othersInvitedEmptyState = view.findViewById(R.id.no_pending_from_others);

    youInvited.setRecipientClickListener(recipient ->
      RecipientBottomSheetDialogFragment.create(recipient.getId(), null)
                                        .show(requireActivity().getSupportFragmentManager(), BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG));

    youInvited.setAdminActionsListener(new AdminActionsListener() {

      @Override
      public void onRevokeInvite(@NonNull GroupMemberEntry.PendingMember pendingMember) {
        viewModel.revokeInviteFor(pendingMember);
      }

      @Override
      public void onRevokeAllInvites(@NonNull GroupMemberEntry.UnknownPendingMemberCount pendingMembers) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void onApproveRequest(@NonNull GroupMemberEntry.RequestingMember requestingMember) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void onDenyRequest(@NonNull GroupMemberEntry.RequestingMember requestingMember) {
        throw new UnsupportedOperationException();
      }
    });

    othersInvited.setAdminActionsListener(new AdminActionsListener() {

      @Override
      public void onRevokeInvite(@NonNull GroupMemberEntry.PendingMember pendingMember) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void onRevokeAllInvites(@NonNull GroupMemberEntry.UnknownPendingMemberCount pendingMembers) {
        viewModel.revokeInvitesFor(pendingMembers);
      }

      @Override
      public void onApproveRequest(@NonNull GroupMemberEntry.RequestingMember requestingMember) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void onDenyRequest(@NonNull GroupMemberEntry.RequestingMember requestingMember) {
        throw new UnsupportedOperationException();
      }
    });

    return view;
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    GroupId.V2 groupId = GroupId.parseOrThrow(Objects.requireNonNull(requireArguments().getString(GROUP_ID))).requireV2();

    PendingMemberInvitesViewModel.Factory factory = new PendingMemberInvitesViewModel.Factory(requireContext(), groupId);

    viewModel = ViewModelProviders.of(requireActivity(), factory).get(PendingMemberInvitesViewModel.class);

    viewModel.getWhoYouInvited().observe(getViewLifecycleOwner(), invitees -> {
      youInvited.setMembers(invitees);
      youInvitedEmptyState.setVisibility(invitees.isEmpty() ? View.VISIBLE : View.GONE);
    });

    viewModel.getWhoOthersInvited().observe(getViewLifecycleOwner(), invitees -> {
      othersInvited.setMembers(invitees);
      othersInvitedEmptyState.setVisibility(invitees.isEmpty() ? View.VISIBLE : View.GONE);
    });
  }
}
