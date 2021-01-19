package xyz.hanoman.messenger.recipients.ui.bottomsheet;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.components.AvatarImageView;
import xyz.hanoman.messenger.contacts.avatars.FallbackContactPhoto;
import xyz.hanoman.messenger.contacts.avatars.FallbackPhoto80dp;
import xyz.hanoman.messenger.groups.GroupId;
import xyz.hanoman.messenger.phonenumbers.PhoneNumberFormatter;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientExporter;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.recipients.RecipientUtil;
import xyz.hanoman.messenger.util.BottomSheetUtil;
import xyz.hanoman.messenger.util.ServiceUtil;
import xyz.hanoman.messenger.util.ThemeUtil;
import xyz.hanoman.messenger.util.Util;
import xyz.hanoman.messenger.util.ViewUtil;

import java.util.Objects;

/**
 * A bottom sheet that shows some simple recipient details, as well as some actions (like calling,
 * adding to contacts, etc).
 */
public final class RecipientBottomSheetDialogFragment extends BottomSheetDialogFragment {

  public static final int REQUEST_CODE_ADD_CONTACT = 1111;

  private static final String ARGS_RECIPIENT_ID = "RECIPIENT_ID";
  private static final String ARGS_GROUP_ID     = "GROUP_ID";

  private RecipientDialogViewModel viewModel;
  private AvatarImageView          avatar;
  private TextView                 fullName;
  private TextView                 usernameNumber;
  private Button                   messageButton;
  private Button                   secureCallButton;
  private Button                   insecureCallButton;
  private Button                   secureVideoCallButton;
  private Button                   blockButton;
  private Button                   unblockButton;
  private Button                   addContactButton;
  private Button                   addToGroupButton;
  private Button                   viewSafetyNumberButton;
  private Button                   makeGroupAdminButton;
  private Button                   removeAdminButton;
  private Button                   removeFromGroupButton;
  private ProgressBar              adminActionBusy;
  private View                     noteToSelfDescription;

  public static BottomSheetDialogFragment create(@NonNull RecipientId recipientId,
                                                 @Nullable GroupId groupId)
  {
    Bundle                             args     = new Bundle();
    RecipientBottomSheetDialogFragment fragment = new RecipientBottomSheetDialogFragment();

    args.putString(ARGS_RECIPIENT_ID, recipientId.serialize());
    if (groupId != null) {
      args.putString(ARGS_GROUP_ID, groupId.toString());
    }

    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    setStyle(DialogFragment.STYLE_NORMAL,
             ThemeUtil.isDarkTheme(requireContext()) ? R.style.Theme_Signal_RoundedBottomSheet
                                                     : R.style.Theme_Signal_RoundedBottomSheet_Light);

    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.recipient_bottom_sheet, container, false);

    avatar                 = view.findViewById(R.id.rbs_recipient_avatar);
    fullName               = view.findViewById(R.id.rbs_full_name);
    usernameNumber         = view.findViewById(R.id.rbs_username_number);
    messageButton          = view.findViewById(R.id.rbs_message_button);
    secureCallButton       = view.findViewById(R.id.rbs_secure_call_button);
    insecureCallButton     = view.findViewById(R.id.rbs_insecure_call_button);
    secureVideoCallButton  = view.findViewById(R.id.rbs_video_call_button);
    blockButton            = view.findViewById(R.id.rbs_block_button);
    unblockButton          = view.findViewById(R.id.rbs_unblock_button);
    addContactButton       = view.findViewById(R.id.rbs_add_contact_button);
    addToGroupButton       = view.findViewById(R.id.rbs_add_to_group_button);
    viewSafetyNumberButton = view.findViewById(R.id.rbs_view_safety_number_button);
    makeGroupAdminButton   = view.findViewById(R.id.rbs_make_group_admin_button);
    removeAdminButton      = view.findViewById(R.id.rbs_remove_group_admin_button);
    removeFromGroupButton  = view.findViewById(R.id.rbs_remove_from_group_button);
    adminActionBusy        = view.findViewById(R.id.rbs_admin_action_busy);
    noteToSelfDescription  = view.findViewById(R.id.rbs_note_to_self_description);

    return view;
  }

  @Override
  public void onViewCreated(@NonNull View fragmentView, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(fragmentView, savedInstanceState);

    Bundle      arguments   = requireArguments();
    RecipientId recipientId = RecipientId.from(Objects.requireNonNull(arguments.getString(ARGS_RECIPIENT_ID)));
    GroupId     groupId     = GroupId.parseNullableOrThrow(arguments.getString(ARGS_GROUP_ID));

    RecipientDialogViewModel.Factory factory = new RecipientDialogViewModel.Factory(requireContext().getApplicationContext(), recipientId, groupId);

    viewModel = ViewModelProviders.of(this, factory).get(RecipientDialogViewModel.class);

    viewModel.getRecipient().observe(getViewLifecycleOwner(), recipient -> {
      avatar.setFallbackPhotoProvider(new Recipient.FallbackPhotoProvider() {
        @Override
        public @NonNull FallbackContactPhoto getPhotoForLocalNumber() {
          return new FallbackPhoto80dp(R.drawable.ic_note_80, recipient.getColor().toAvatarColor(requireContext()));
        }
      });
      avatar.setAvatar(recipient);
      if (recipient.isSelf()) {
        avatar.setOnClickListener(v -> {
          dismiss();
          viewModel.onMessageClicked(requireActivity());
        });
      }

      String name = recipient.isSelf() ? requireContext().getString(R.string.note_to_self)
                                              : recipient.getDisplayName(requireContext());
      fullName.setText(name);
      fullName.setVisibility(TextUtils.isEmpty(name) ? View.GONE : View.VISIBLE);
      if (recipient.isSystemContact() && !recipient.isSelf()) {
        fullName.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_profile_circle_outline_16, 0);
        fullName.setCompoundDrawablePadding(ViewUtil.dpToPx(4));
        TextViewCompat.setCompoundDrawableTintList(fullName, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.signal_text_primary)));
      }

      String usernameNumberString = recipient.hasAUserSetDisplayName(requireContext()) && !recipient.isSelf()
                                    ? recipient.getSmsAddress().transform(PhoneNumberFormatter::prettyPrint).or("").trim()
                                    : "";
      usernameNumber.setText(usernameNumberString);
      usernameNumber.setVisibility(TextUtils.isEmpty(usernameNumberString) ? View.GONE : View.VISIBLE);
      usernameNumber.setOnLongClickListener(v -> {
        Util.copyToClipboard(v.getContext(), usernameNumber.getText().toString());
        ServiceUtil.getVibrator(v.getContext()).vibrate(250);
        Toast.makeText(v.getContext(), R.string.RecipientBottomSheet_copied_to_clipboard, Toast.LENGTH_SHORT).show();
        return true;
      });

      noteToSelfDescription.setVisibility(recipient.isSelf() ? View.VISIBLE : View.GONE);

      if (RecipientUtil.isBlockable(recipient)) {
        boolean blocked = recipient.isBlocked();

        blockButton  .setVisibility(recipient.isSelf() ||  blocked ? View.GONE : View.VISIBLE);
        unblockButton.setVisibility(recipient.isSelf() || !blocked ? View.GONE : View.VISIBLE);
      } else {
        blockButton  .setVisibility(View.GONE);
        unblockButton.setVisibility(View.GONE);
      }

      messageButton.setVisibility(!recipient.isSelf() ? View.VISIBLE : View.GONE);
      secureCallButton.setVisibility(recipient.isRegistered() && !recipient.isSelf() ? View.VISIBLE : View.GONE);
      insecureCallButton.setVisibility(!recipient.isRegistered() && !recipient.isSelf() ? View.VISIBLE : View.GONE);
      secureVideoCallButton.setVisibility(recipient.isRegistered() && !recipient.isSelf() ? View.VISIBLE : View.GONE);

      if (recipient.isSystemContact() || recipient.isGroup() || recipient.isSelf()) {
        addContactButton.setVisibility(View.GONE);
      } else {
        addContactButton.setVisibility(View.VISIBLE);
        addContactButton.setOnClickListener(v -> {
          startActivityForResult(RecipientExporter.export(recipient).asAddContactIntent(), REQUEST_CODE_ADD_CONTACT);
        });
      }
    });

    viewModel.getCanAddToAGroup().observe(getViewLifecycleOwner(), canAdd -> {
      addToGroupButton.setText(groupId == null ? R.string.RecipientBottomSheet_add_to_a_group : R.string.RecipientBottomSheet_add_to_another_group);
      addToGroupButton.setVisibility(canAdd ? View.VISIBLE : View.GONE);
    });

    viewModel.getAdminActionStatus().observe(getViewLifecycleOwner(), adminStatus -> {
      makeGroupAdminButton.setVisibility(adminStatus.isCanMakeAdmin() ? View.VISIBLE : View.GONE);
      removeAdminButton.setVisibility(adminStatus.isCanMakeNonAdmin() ? View.VISIBLE : View.GONE);
      removeFromGroupButton.setVisibility(adminStatus.isCanRemove() ? View.VISIBLE : View.GONE);
    });

    viewModel.getIdentity().observe(getViewLifecycleOwner(), identityRecord -> {
      viewSafetyNumberButton.setVisibility(identityRecord != null ? View.VISIBLE : View.GONE);

      if (identityRecord != null) {
        viewSafetyNumberButton.setOnClickListener(view -> {
          dismiss();
          viewModel.onViewSafetyNumberClicked(requireActivity(), identityRecord);
        });
      }
    });

    avatar.setOnClickListener(view -> {
      dismiss();
      viewModel.onAvatarClicked(requireActivity());
    });

    messageButton.setOnClickListener(view -> {
      dismiss();
      viewModel.onMessageClicked(requireActivity());
    });

    secureCallButton.setOnClickListener(view -> viewModel.onSecureCallClicked(requireActivity()));
    insecureCallButton.setOnClickListener(view -> viewModel.onInsecureCallClicked(requireActivity()));
    secureVideoCallButton.setOnClickListener(view -> viewModel.onSecureVideoCallClicked(requireActivity()));

    blockButton.setOnClickListener(view -> viewModel.onBlockClicked(requireActivity()));
    unblockButton.setOnClickListener(view -> viewModel.onUnblockClicked(requireActivity()));

    makeGroupAdminButton.setOnClickListener(view -> viewModel.onMakeGroupAdminClicked(requireActivity()));
    removeAdminButton.setOnClickListener(view -> viewModel.onRemoveGroupAdminClicked(requireActivity()));

    removeFromGroupButton.setOnClickListener(view -> viewModel.onRemoveFromGroupClicked(requireActivity(), this::dismiss));

    addToGroupButton.setOnClickListener(view -> {
      dismiss();
      viewModel.onAddToGroupButton(requireActivity());
    });

    viewModel.getAdminActionBusy().observe(getViewLifecycleOwner(), busy -> {
      adminActionBusy.setVisibility(busy ? View.VISIBLE : View.GONE);

      makeGroupAdminButton.setEnabled(!busy);
      removeAdminButton.setEnabled(!busy);
      removeFromGroupButton.setEnabled(!busy);
    });
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE_ADD_CONTACT) {
      viewModel.onAddedToContacts();
    }
  }

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    BottomSheetUtil.show(manager, tag, this);
  }
}
