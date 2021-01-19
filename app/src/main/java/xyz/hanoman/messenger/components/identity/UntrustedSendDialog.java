package xyz.hanoman.messenger.components.identity;


import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.IdentityDatabase;
import xyz.hanoman.messenger.database.IdentityDatabase.IdentityRecord;
import xyz.hanoman.messenger.util.concurrent.SimpleTask;

import java.util.List;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

public class UntrustedSendDialog extends AlertDialog.Builder implements DialogInterface.OnClickListener {

  private final List<IdentityRecord> untrustedRecords;
  private final ResendListener       resendListener;

  public UntrustedSendDialog(@NonNull Context context,
                             @NonNull String message,
                             @NonNull List<IdentityRecord> untrustedRecords,
                             @NonNull ResendListener resendListener)
  {
    super(context);
    this.untrustedRecords = untrustedRecords;
    this.resendListener   = resendListener;

    setTitle(R.string.UntrustedSendDialog_send_message);
    setIcon(R.drawable.ic_warning);
    setMessage(message);
    setPositiveButton(R.string.UntrustedSendDialog_send, this);
    setNegativeButton(android.R.string.cancel, null);
  }

  @Override
  public void onClick(DialogInterface dialog, int which) {
    final IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(getContext());

    SimpleTask.run(() -> {
      synchronized (SESSION_LOCK) {
        for (IdentityRecord identityRecord : untrustedRecords) {
          identityDatabase.setApproval(identityRecord.getRecipientId(), true);
        }
      }

      return null;
    }, unused -> resendListener.onResendMessage());
  }

  public interface ResendListener {
    public void onResendMessage();
  }
}
