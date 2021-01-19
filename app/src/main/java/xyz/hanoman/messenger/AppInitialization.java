package xyz.hanoman.messenger;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.insights.InsightsOptOut;
import xyz.hanoman.messenger.jobmanager.JobManager;
import xyz.hanoman.messenger.jobs.StickerPackDownloadJob;
import xyz.hanoman.messenger.keyvalue.SignalStore;
import xyz.hanoman.messenger.migrations.ApplicationMigrations;
import xyz.hanoman.messenger.stickers.BlessedPacks;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import xyz.hanoman.messenger.util.Util;

/**
 * Rule of thumb: if there's something you want to do on the first app launch that involves
 * persisting state to the database, you'll almost certainly *also* want to do it post backup
 * restore, since a backup restore will wipe the current state of the database.
 */
public final class AppInitialization {

  private static final String TAG = Log.tag(AppInitialization.class);

  private AppInitialization() {}

  public static void onFirstEverAppLaunch(@NonNull Context context) {
    Log.i(TAG, "onFirstEverAppLaunch()");

    InsightsOptOut.userRequestedOptOut(context);
    TextSecurePreferences.setAppMigrationVersion(context, ApplicationMigrations.CURRENT_VERSION);
    TextSecurePreferences.setJobManagerVersion(context, JobManager.CURRENT_VERSION);
    TextSecurePreferences.setLastExperienceVersionCode(context, Util.getCanonicalVersionCode());
    TextSecurePreferences.setHasSeenStickerIntroTooltip(context, true);
    TextSecurePreferences.setPasswordDisabled(context, true);
    TextSecurePreferences.setLastExperienceVersionCode(context, Util.getCanonicalVersionCode());
    TextSecurePreferences.setReadReceiptsEnabled(context, true);
    TextSecurePreferences.setTypingIndicatorsEnabled(context, true);
    TextSecurePreferences.setHasSeenWelcomeScreen(context, false);
    ApplicationDependencies.getMegaphoneRepository().onFirstEverAppLaunch();
    SignalStore.onFirstEverAppLaunch();
    ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forInstall(BlessedPacks.ZOZO.getPackId(), BlessedPacks.ZOZO.getPackKey(), false));
    ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forInstall(BlessedPacks.BANDIT.getPackId(), BlessedPacks.BANDIT.getPackKey(), false));
    ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forReference(BlessedPacks.SWOON_HANDS.getPackId(), BlessedPacks.SWOON_HANDS.getPackKey()));
    ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forReference(BlessedPacks.SWOON_FACES.getPackId(), BlessedPacks.SWOON_FACES.getPackKey()));
  }

  public static void onPostBackupRestore(@NonNull Context context) {
    Log.i(TAG, "onPostBackupRestore()");

    ApplicationDependencies.getMegaphoneRepository().onFirstEverAppLaunch();
    SignalStore.onFirstEverAppLaunch();
    SignalStore.onboarding().clearAll();
    ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forInstall(BlessedPacks.ZOZO.getPackId(), BlessedPacks.ZOZO.getPackKey(), false));
    ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forInstall(BlessedPacks.BANDIT.getPackId(), BlessedPacks.BANDIT.getPackKey(), false));
    ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forReference(BlessedPacks.SWOON_HANDS.getPackId(), BlessedPacks.SWOON_HANDS.getPackKey()));
    ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forReference(BlessedPacks.SWOON_FACES.getPackId(), BlessedPacks.SWOON_FACES.getPackKey()));
  }

  /**
   * Temporary migration method that does the safest bits of {@link #onFirstEverAppLaunch(Context)}
   */
  public static void onRepairFirstEverAppLaunch(@NonNull Context context) {
    Log.w(TAG, "onRepairFirstEverAppLaunch()");

    InsightsOptOut.userRequestedOptOut(context);
    TextSecurePreferences.setAppMigrationVersion(context, ApplicationMigrations.CURRENT_VERSION);
    TextSecurePreferences.setJobManagerVersion(context, JobManager.CURRENT_VERSION);
    TextSecurePreferences.setLastExperienceVersionCode(context, Util.getCanonicalVersionCode());
    TextSecurePreferences.setHasSeenStickerIntroTooltip(context, true);
    TextSecurePreferences.setPasswordDisabled(context, true);
    TextSecurePreferences.setLastExperienceVersionCode(context, Util.getCanonicalVersionCode());
    ApplicationDependencies.getMegaphoneRepository().onFirstEverAppLaunch();
    SignalStore.onFirstEverAppLaunch();
    ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forInstall(BlessedPacks.ZOZO.getPackId(), BlessedPacks.ZOZO.getPackKey(), false));
    ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forInstall(BlessedPacks.BANDIT.getPackId(), BlessedPacks.BANDIT.getPackKey(), false));
    ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forReference(BlessedPacks.SWOON_HANDS.getPackId(), BlessedPacks.SWOON_HANDS.getPackKey()));
    ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forReference(BlessedPacks.SWOON_FACES.getPackId(), BlessedPacks.SWOON_FACES.getPackKey()));
  }
}
