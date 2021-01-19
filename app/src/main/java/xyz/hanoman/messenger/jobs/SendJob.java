package xyz.hanoman.messenger.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.BuildConfig;
import xyz.hanoman.messenger.TextSecureExpiredException;
import xyz.hanoman.messenger.attachments.Attachment;
import xyz.hanoman.messenger.database.AttachmentDatabase;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.keyvalue.SignalStore;

import java.util.List;

public abstract class SendJob extends BaseJob {

  @SuppressWarnings("unused")
  private final static String TAG = SendJob.class.getSimpleName();

  public SendJob(Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public final void onRun() throws Exception {
    if (SignalStore.misc().isClientDeprecated()) {
      throw new TextSecureExpiredException(String.format("TextSecure expired (build %d, now %d)",
                                                         BuildConfig.BUILD_TIMESTAMP,
                                                         System.currentTimeMillis()));
    }

    Log.i(TAG, "Starting message send attempt");
    onSend();
    Log.i(TAG, "Message send completed");
  }

  protected abstract void onSend() throws Exception;

  protected void markAttachmentsUploaded(long messageId, @NonNull List<Attachment> attachments) {
    AttachmentDatabase database = DatabaseFactory.getAttachmentDatabase(context);

    for (Attachment attachment : attachments) {
      database.markAttachmentUploaded(messageId, attachment);
    }
  }
}
