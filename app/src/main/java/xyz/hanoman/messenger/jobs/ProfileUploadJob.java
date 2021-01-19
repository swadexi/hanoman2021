package xyz.hanoman.messenger.jobs;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.zkgroup.profiles.ProfileKey;
import xyz.hanoman.messenger.crypto.ProfileKeyUtil;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.jobmanager.impl.NetworkConstraint;
import xyz.hanoman.messenger.profiles.AvatarHelper;
import xyz.hanoman.messenger.profiles.ProfileName;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.util.concurrent.TimeUnit;

public final class ProfileUploadJob extends BaseJob {

  private static final String TAG = Log.tag(ProfileUploadJob.class);

  public static final String KEY = "ProfileUploadJob";

  public static final String QUEUE = "ProfileAlteration";

  private final Context                     context;
  private final SignalServiceAccountManager accountManager;

  public ProfileUploadJob() {
    this(new Job.Parameters.Builder()
                            .addConstraint(NetworkConstraint.KEY)
                            .setQueue(QUEUE)
                            .setLifespan(TimeUnit.DAYS.toMillis(30))
                            .setMaxAttempts(Parameters.UNLIMITED)
                            .setMaxInstancesForFactory(2)
                            .build());
  }

  private ProfileUploadJob(@NonNull Parameters parameters) {
    super(parameters);

    this.context        = ApplicationDependencies.getApplication();
    this.accountManager = ApplicationDependencies.getSignalServiceAccountManager();
  }

  @Override
  protected void onRun() throws Exception {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      Log.w(TAG, "Not registered. Skipping.");
      return;
    }

    ProfileKey  profileKey  = ProfileKeyUtil.getSelfProfileKey();
    ProfileName profileName = Recipient.self().getProfileName();
    String      avatarPath;

    try (StreamDetails avatar = AvatarHelper.getSelfProfileAvatarStream(context)) {
      avatarPath = accountManager.setVersionedProfile(Recipient.self().getUuid().get(), profileKey, profileName.serialize(), avatar).orNull();
    }

    DatabaseFactory.getRecipientDatabase(context).setProfileAvatar(Recipient.self().getId(), avatarPath);
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return true;
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
  }

  public static class Factory implements Job.Factory<ProfileUploadJob> {

    @Override
    public @NonNull ProfileUploadJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new ProfileUploadJob(parameters);
    }
  }
}
