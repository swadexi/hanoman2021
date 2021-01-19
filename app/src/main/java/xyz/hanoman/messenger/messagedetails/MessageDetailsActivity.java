package xyz.hanoman.messenger.messagedetails;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import xyz.hanoman.messenger.PassphraseRequiredActivity;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.color.MaterialColor;
import xyz.hanoman.messenger.database.MmsSmsDatabase;
import xyz.hanoman.messenger.database.model.MessageRecord;
import xyz.hanoman.messenger.messagedetails.MessageDetailsAdapter.MessageDetailsViewState;
import xyz.hanoman.messenger.messagedetails.MessageDetailsViewModel.Factory;
import xyz.hanoman.messenger.mms.GlideApp;
import xyz.hanoman.messenger.mms.GlideRequests;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.util.DynamicDarkActionBarTheme;
import xyz.hanoman.messenger.util.DynamicTheme;
import xyz.hanoman.messenger.util.WindowUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class MessageDetailsActivity extends PassphraseRequiredActivity {

  private static final String MESSAGE_ID_EXTRA = "message_id";
  private static final String THREAD_ID_EXTRA  = "thread_id";
  private static final String TYPE_EXTRA       = "type";
  private static final String RECIPIENT_EXTRA  = "recipient_id";

  private GlideRequests           glideRequests;
  private MessageDetailsViewModel viewModel;
  private MessageDetailsAdapter   adapter;

  private DynamicTheme dynamicTheme = new DynamicDarkActionBarTheme();

  public static @NonNull Intent getIntentForMessageDetails(@NonNull Context context, @NonNull MessageRecord message, @NonNull RecipientId recipientId, long threadId) {
    Intent intent = new Intent(context, MessageDetailsActivity.class);
    intent.putExtra(MESSAGE_ID_EXTRA, message.getId());
    intent.putExtra(THREAD_ID_EXTRA, threadId);
    intent.putExtra(TYPE_EXTRA, message.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
    intent.putExtra(RECIPIENT_EXTRA, recipientId);
    return intent;
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    setContentView(R.layout.message_details_activity);

    glideRequests = GlideApp.with(this);

    initializeList();
    initializeViewModel();
    initializeActionBar();
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    adapter.resumeMessageExpirationTimer();
  }

  @Override
  protected void onPause() {
    super.onPause();
    adapter.pauseMessageExpirationTimer();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void initializeList() {
    RecyclerView list = findViewById(R.id.message_details_list);
    adapter           = new MessageDetailsAdapter(this, glideRequests);

    list.setAdapter(adapter);
    list.setItemAnimator(null);
  }

  private void initializeViewModel() {
    final RecipientId recipientId = getIntent().getParcelableExtra(RECIPIENT_EXTRA);
    final String      type        = getIntent().getStringExtra(TYPE_EXTRA);
    final Long        messageId   = getIntent().getLongExtra(MESSAGE_ID_EXTRA, -1);
    final Factory     factory     = new Factory(recipientId, type, messageId);

    viewModel = ViewModelProviders.of(this, factory).get(MessageDetailsViewModel.class);
    viewModel.getMessageDetails().observe(this, details -> {
      if (details == null) {
        finish();
      } else {
        adapter.submitList(convertToRows(details));
      }
    });
  }

  private void initializeActionBar() {
    requireSupportActionBar().setDisplayHomeAsUpEnabled(true);
    requireSupportActionBar().setTitle(R.string.AndroidManifest__message_details);

    viewModel.getRecipientColor().observe(this, this::setActionBarColor);
  }

  private void setActionBarColor(MaterialColor color) {
    assert getSupportActionBar() != null;
    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color.toActionBarColor(this)));

    WindowUtil.setStatusBarColor(getWindow(), color.toStatusBarColor(this));
  }

  private List<MessageDetailsViewState<?>> convertToRows(MessageDetails details) {
    List<MessageDetailsViewState<?>> list = new ArrayList<>();

    list.add(new MessageDetailsViewState<>(details.getConversationMessage(), MessageDetailsViewState.MESSAGE_HEADER));

    if (details.getConversationMessage().getMessageRecord().isOutgoing()) {
      addRecipients(list, RecipientHeader.NOT_SENT, details.getNotSent());
      addRecipients(list, RecipientHeader.READ, details.getRead());
      addRecipients(list, RecipientHeader.DELIVERED, details.getDelivered());
      addRecipients(list, RecipientHeader.SENT_TO, details.getSent());
      addRecipients(list, RecipientHeader.PENDING, details.getPending());
    } else {
      addRecipients(list, RecipientHeader.SENT_FROM, details.getSent());
    }

    return list;
  }

  private boolean addRecipients(List<MessageDetailsViewState<?>> list, RecipientHeader header, Collection<RecipientDeliveryStatus> recipients) {
    if (recipients.isEmpty()) {
      return false;
    }

    list.add(new MessageDetailsViewState<>(header, MessageDetailsViewState.RECIPIENT_HEADER));
    for (RecipientDeliveryStatus status : recipients) {
      list.add(new MessageDetailsViewState<>(status, MessageDetailsViewState.RECIPIENT));
    }
    return true;
  }
}
