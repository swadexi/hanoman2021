package xyz.hanoman.messenger.dependencies;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.BuildConfig;
import xyz.hanoman.messenger.components.TypingStatusRepository;
import xyz.hanoman.messenger.components.TypingStatusSender;
import xyz.hanoman.messenger.crypto.storage.SignalProtocolStoreImpl;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.DatabaseObserver;
import xyz.hanoman.messenger.database.JobDatabase;
import xyz.hanoman.messenger.events.ReminderUpdateEvent;
import xyz.hanoman.messenger.jobmanager.Constraint;
import xyz.hanoman.messenger.jobmanager.ConstraintObserver;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.jobmanager.JobManager;
import xyz.hanoman.messenger.jobmanager.JobMigrator;
import xyz.hanoman.messenger.jobmanager.impl.FactoryJobPredicate;
import xyz.hanoman.messenger.jobmanager.impl.JsonDataSerializer;
import xyz.hanoman.messenger.jobmanager.persistence.JobStorage;
import xyz.hanoman.messenger.jobs.FastJobStorage;
import xyz.hanoman.messenger.jobs.GroupCallUpdateSendJob;
import xyz.hanoman.messenger.jobs.JobManagerFactories;
import xyz.hanoman.messenger.jobs.MarkerJob;
import xyz.hanoman.messenger.jobs.PushDecryptMessageJob;
import xyz.hanoman.messenger.jobs.PushGroupSendJob;
import xyz.hanoman.messenger.jobs.PushMediaSendJob;
import xyz.hanoman.messenger.jobs.PushProcessMessageJob;
import xyz.hanoman.messenger.jobs.PushTextSendJob;
import xyz.hanoman.messenger.jobs.ReactionSendJob;
import xyz.hanoman.messenger.jobs.TypingSendJob;
import xyz.hanoman.messenger.megaphone.MegaphoneRepository;
import xyz.hanoman.messenger.messages.BackgroundMessageRetriever;
import xyz.hanoman.messenger.messages.IncomingMessageObserver;
import xyz.hanoman.messenger.messages.IncomingMessageProcessor;
import xyz.hanoman.messenger.notifications.DefaultMessageNotifier;
import xyz.hanoman.messenger.notifications.MessageNotifier;
import xyz.hanoman.messenger.notifications.OptimizedMessageNotifier;
import xyz.hanoman.messenger.push.SecurityEventListener;
import xyz.hanoman.messenger.push.SignalServiceNetworkAccess;
import xyz.hanoman.messenger.recipients.LiveRecipientCache;
import xyz.hanoman.messenger.service.TrimThreadsByDateManager;
import xyz.hanoman.messenger.shakereport.ShakeToReport;
import xyz.hanoman.messenger.util.AlarmSleepTimer;
import xyz.hanoman.messenger.util.ByteUnit;
import xyz.hanoman.messenger.util.EarlyMessageCache;
import xyz.hanoman.messenger.util.FeatureFlags;
import xyz.hanoman.messenger.util.FrameRateTracker;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;

import java.util.UUID;

/**
 * Implementation of {@link ApplicationDependencies.Provider} that provides real app dependencies.
 */
public class ApplicationDependencyProvider implements ApplicationDependencies.Provider {

  private static final String TAG = Log.tag(ApplicationDependencyProvider.class);

  private final Application                context;
  private final SignalServiceNetworkAccess networkAccess;

  public ApplicationDependencyProvider(@NonNull Application context, @NonNull SignalServiceNetworkAccess networkAccess) {
    this.context       = context;
    this.networkAccess = networkAccess;
  }

  private @NonNull ClientZkOperations provideClientZkOperations() {
    return ClientZkOperations.create(networkAccess.getConfiguration(context));
  }

  @Override
  public @NonNull GroupsV2Operations provideGroupsV2Operations() {
    return new GroupsV2Operations(provideClientZkOperations());
  }

  @Override
  public @NonNull SignalServiceAccountManager provideSignalServiceAccountManager() {
    return new SignalServiceAccountManager(networkAccess.getConfiguration(context),
                                           new DynamicCredentialsProvider(context),
                                           BuildConfig.SIGNAL_AGENT,
                                           provideGroupsV2Operations(),
                                           FeatureFlags.okHttpAutomaticRetry());
  }

  @Override
  public @NonNull SignalServiceMessageSender provideSignalServiceMessageSender() {
      return new SignalServiceMessageSender(networkAccess.getConfiguration(context),
                                            new DynamicCredentialsProvider(context),
                                            new SignalProtocolStoreImpl(context),
                                            BuildConfig.SIGNAL_AGENT,
                                            TextSecurePreferences.isMultiDevice(context),
                                            Optional.fromNullable(IncomingMessageObserver.getPipe()),
                                            Optional.fromNullable(IncomingMessageObserver.getUnidentifiedPipe()),
                                            Optional.of(new SecurityEventListener(context)),
                                            provideClientZkOperations().getProfileOperations(),
                                            SignalExecutors.newCachedBoundedExecutor("signal-messages", 1, 16),
                                            ByteUnit.KILOBYTES.toBytes(512),
                                            FeatureFlags.okHttpAutomaticRetry());
  }

  @Override
  public @NonNull SignalServiceMessageReceiver provideSignalServiceMessageReceiver() {
    SleepTimer sleepTimer = TextSecurePreferences.isFcmDisabled(context) ? new AlarmSleepTimer(context)
                                                                         : new UptimeSleepTimer();
    return new SignalServiceMessageReceiver(networkAccess.getConfiguration(context),
                                            new DynamicCredentialsProvider(context),
                                            BuildConfig.SIGNAL_AGENT,
                                            new PipeConnectivityListener(),
                                            sleepTimer,
                                            provideClientZkOperations().getProfileOperations(),
                                            FeatureFlags.okHttpAutomaticRetry());
  }

  @Override
  public @NonNull SignalServiceNetworkAccess provideSignalServiceNetworkAccess() {
    return networkAccess;
  }

  @Override
  public @NonNull IncomingMessageProcessor provideIncomingMessageProcessor() {
    return new IncomingMessageProcessor(context);
  }

  @Override
  public @NonNull BackgroundMessageRetriever provideBackgroundMessageRetriever() {
    return new BackgroundMessageRetriever();
  }

  @Override
  public @NonNull LiveRecipientCache provideRecipientCache() {
    return new LiveRecipientCache(context);
  }

  @Override
  public @NonNull JobManager provideJobManager() {
    JobManager.Configuration config = new JobManager.Configuration.Builder()
                                                                  .setDataSerializer(new JsonDataSerializer())
                                                                  .setJobFactories(JobManagerFactories.getJobFactories(context))
                                                                  .setConstraintFactories(JobManagerFactories.getConstraintFactories(context))
                                                                  .setConstraintObservers(JobManagerFactories.getConstraintObservers(context))
                                                                  .setJobStorage(new FastJobStorage(JobDatabase.getInstance(context)))
                                                                  .setJobMigrator(new JobMigrator(TextSecurePreferences.getJobManagerVersion(context), JobManager.CURRENT_VERSION, JobManagerFactories.getJobMigrations(context)))
                                                                  .addReservedJobRunner(new FactoryJobPredicate(PushDecryptMessageJob.KEY, PushProcessMessageJob.KEY, MarkerJob.KEY))
                                                                  .addReservedJobRunner(new FactoryJobPredicate(PushTextSendJob.KEY, PushMediaSendJob.KEY, PushGroupSendJob.KEY, ReactionSendJob.KEY, TypingSendJob.KEY, GroupCallUpdateSendJob.KEY))
                                                                  .build();
    return new JobManager(context, config);
  }

  @Override
  public @NonNull FrameRateTracker provideFrameRateTracker() {
    return new FrameRateTracker(context);
  }

  public @NonNull MegaphoneRepository provideMegaphoneRepository() {
    return new MegaphoneRepository(context);
  }

  @Override
  public @NonNull EarlyMessageCache provideEarlyMessageCache() {
    return new EarlyMessageCache();
  }

  @Override
  public @NonNull MessageNotifier provideMessageNotifier() {
    return new OptimizedMessageNotifier(new DefaultMessageNotifier());
  }

  @Override
  public @NonNull IncomingMessageObserver provideIncomingMessageObserver() {
    return new IncomingMessageObserver(context);
  }

  @Override
  public @NonNull TrimThreadsByDateManager provideTrimThreadsByDateManager() {
    return new TrimThreadsByDateManager(context);
  }

  @Override
  public @NonNull TypingStatusRepository provideTypingStatusRepository() {
    return new TypingStatusRepository();
  }

  @Override
  public @NonNull TypingStatusSender provideTypingStatusSender() {
    return new TypingStatusSender();
  }

  @Override
  public @NonNull DatabaseObserver provideDatabaseObserver() {
    return new DatabaseObserver(context);
  }

  @Override
  public @NonNull ShakeToReport provideShakeToReport() {
    return new ShakeToReport(context);
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public UUID getUuid() {
      return TextSecurePreferences.getLocalUuid(context);
    }

    @Override
    public String getE164() {
      return TextSecurePreferences.getLocalNumber(context);
    }

    @Override
    public String getPassword() {
      return TextSecurePreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return TextSecurePreferences.getSignalingKey(context);
    }
  }

  private class PipeConnectivityListener implements ConnectivityListener {

    @Override
    public void onConnected() {
      Log.i(TAG, "onConnected()");
      TextSecurePreferences.setUnauthorizedReceived(context, false);
    }

    @Override
    public void onConnecting() {
      Log.i(TAG, "onConnecting()");
    }

    @Override
    public void onDisconnected() {
      Log.w(TAG, "onDisconnected()");
    }

    @Override
    public void onAuthenticationFailure() {
      Log.w(TAG, "onAuthenticationFailure()");
      TextSecurePreferences.setUnauthorizedReceived(context, true);
      EventBus.getDefault().post(new ReminderUpdateEvent());
    }
  }
}
