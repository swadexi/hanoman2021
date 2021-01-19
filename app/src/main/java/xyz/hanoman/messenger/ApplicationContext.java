/*
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package xyz.hanoman.messenger;

import android.content.Context;
import android.hardware.SensorManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.multidex.MultiDexApplication;

import com.google.android.gms.security.ProviderInstaller;

import org.conscrypt.Conscrypt;
import org.signal.aesgcmprovider.AesGcmProvider;
import org.signal.core.util.ShakeDetector;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.AndroidLogger;
import org.signal.core.util.logging.Log;
import org.signal.core.util.logging.PersistentLogger;
import org.signal.core.util.tracing.Tracer;
import org.signal.glide.SignalGlideCodecs;
import org.signal.ringrtc.CallManager;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.helpers.SQLCipherOpenHelper;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.dependencies.ApplicationDependencyProvider;
import xyz.hanoman.messenger.gcm.FcmJobService;
import xyz.hanoman.messenger.jobmanager.JobManager;
import xyz.hanoman.messenger.jobs.CreateSignedPreKeyJob;
import xyz.hanoman.messenger.jobs.FcmRefreshJob;
import xyz.hanoman.messenger.jobs.GroupV1MigrationJob;
import xyz.hanoman.messenger.jobs.MultiDeviceContactUpdateJob;
import xyz.hanoman.messenger.jobs.PushNotificationReceiveJob;
import xyz.hanoman.messenger.jobs.RefreshPreKeysJob;
import xyz.hanoman.messenger.jobs.RetrieveProfileJob;
import xyz.hanoman.messenger.keyvalue.SignalStore;
import xyz.hanoman.messenger.logging.CustomSignalProtocolLogger;
import xyz.hanoman.messenger.logging.LogSecretProvider;
import xyz.hanoman.messenger.migrations.ApplicationMigrations;
import xyz.hanoman.messenger.notifications.NotificationChannels;
import xyz.hanoman.messenger.providers.BlobProvider;
import xyz.hanoman.messenger.push.SignalServiceNetworkAccess;
import xyz.hanoman.messenger.registration.RegistrationUtil;
import xyz.hanoman.messenger.revealable.ViewOnceMessageManager;
import xyz.hanoman.messenger.ringrtc.RingRtcLogger;
import xyz.hanoman.messenger.service.DirectoryRefreshListener;
import xyz.hanoman.messenger.service.ExpiringMessageManager;
import xyz.hanoman.messenger.service.KeyCachingService;
import xyz.hanoman.messenger.service.LocalBackupListener;
import xyz.hanoman.messenger.service.RotateSenderCertificateListener;
import xyz.hanoman.messenger.service.RotateSignedPreKeyListener;
import xyz.hanoman.messenger.service.UpdateApkRefreshListener;
import xyz.hanoman.messenger.shakereport.ShakeToReport;
import xyz.hanoman.messenger.storage.StorageSyncHelper;
import xyz.hanoman.messenger.util.AppStartup;
import xyz.hanoman.messenger.util.DynamicTheme;
import xyz.hanoman.messenger.util.FeatureFlags;
import xyz.hanoman.messenger.util.SignalUncaughtExceptionHandler;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import xyz.hanoman.messenger.util.Util;
import xyz.hanoman.messenger.util.VersionTracker;
import xyz.hanoman.messenger.util.dynamiclanguage.DynamicLanguageContextWrapper;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;

import java.security.Security;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends MultiDexApplication implements DefaultLifecycleObserver {

  private static final String TAG = ApplicationContext.class.getSimpleName();

  private ExpiringMessageManager expiringMessageManager;
  private ViewOnceMessageManager viewOnceMessageManager;
  private PersistentLogger       persistentLogger;

  private volatile boolean isAppVisible;

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    Tracer.getInstance().start("Application#onCreate()");
    AppStartup.getInstance().onApplicationCreate();

    long startTime = System.currentTimeMillis();

    if (FeatureFlags.internalUser()) {
      Tracer.getInstance().setMaxBufferSize(35_000);
    }

    super.onCreate();

    AppStartup.getInstance().addBlocking("security-provider", this::initializeSecurityProvider)
                            .addBlocking("logging", () -> {
                                initializeLogging();
                                Log.i(TAG, "onCreate()");
                            })
                            .addBlocking("crash-handling", this::initializeCrashHandling)
                            .addBlocking("eat-db", () -> DatabaseFactory.getInstance(this))
                            .addBlocking("app-dependencies", this::initializeAppDependencies)
                            .addBlocking("first-launch", this::initializeFirstEverAppLaunch)
                            .addBlocking("app-migrations", this::initializeApplicationMigrations)
                            .addBlocking("ring-rtc", this::initializeRingRtc)
                            .addBlocking("mark-registration", () -> RegistrationUtil.maybeMarkRegistrationComplete(this))
                            .addBlocking("lifecycle-observer", () -> ProcessLifecycleOwner.get().getLifecycle().addObserver(this))
                            .addBlocking("message-retriever", this::initializeMessageRetrieval)
                            .addBlocking("dynamic-theme", () -> DynamicTheme.setDefaultDayNightMode(this))
                            .addBlocking("vector-compat", () -> {
                              if (Build.VERSION.SDK_INT < 21) {
                                AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
                              }
                            })
                            .addNonBlocking(this::initializeRevealableMessageManager)
                            .addNonBlocking(this::initializeGcmCheck)
                            .addNonBlocking(this::initializeSignedPreKeyCheck)
                            .addNonBlocking(this::initializePeriodicTasks)
                            .addNonBlocking(this::initializeCircumvention)
                            .addNonBlocking(this::initializePendingMessages)
                            .addNonBlocking(this::initializeCleanup)
                            .addNonBlocking(this::initializeGlideCodecs)
                            .addNonBlocking(FeatureFlags::init)
                            .addNonBlocking(RefreshPreKeysJob::scheduleIfNecessary)
                            .addNonBlocking(StorageSyncHelper::scheduleRoutineSync)
                            .addNonBlocking(() -> ApplicationDependencies.getJobManager().beginJobLoop())
                            .addPostRender(this::initializeExpiringMessageManager)
                            .addPostRender(this::initializeBlobProvider)
                            .addPostRender(() -> NotificationChannels.create(this))
                            .execute();

    ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

    Log.d(TAG, "onCreate() took " + (System.currentTimeMillis() - startTime) + " ms");
    Tracer.getInstance().end("Application#onCreate()");
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {
    long startTime = System.currentTimeMillis();
    isAppVisible = true;
    Log.i(TAG, "App is now visible.");

    ApplicationDependencies.getFrameRateTracker().begin();
    ApplicationDependencies.getMegaphoneRepository().onAppForegrounded();

    SignalExecutors.BOUNDED.execute(() -> {
      FeatureFlags.refreshIfNecessary();
      ApplicationDependencies.getRecipientCache().warmUp();
      RetrieveProfileJob.enqueueRoutineFetchIfNecessary(this);
      GroupV1MigrationJob.enqueueRoutineMigrationsIfNecessary(this);
      executePendingContactSync();
      KeyCachingService.onAppForegrounded(this);
      ApplicationDependencies.getShakeToReport().enable();
      checkBuildExpiration();
    });

    Log.d(TAG, "onStart() took " + (System.currentTimeMillis() - startTime) + " ms");
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    isAppVisible = false;
    Log.i(TAG, "App is no longer visible.");
    KeyCachingService.onAppBackgrounded(this);
    ApplicationDependencies.getMessageNotifier().clearVisibleThread();
    ApplicationDependencies.getFrameRateTracker().end();
    ApplicationDependencies.getShakeToReport().disable();
  }

  public ExpiringMessageManager getExpiringMessageManager() {
    if (expiringMessageManager == null) {
      initializeExpiringMessageManager();
    }
    return expiringMessageManager;
  }

  public ViewOnceMessageManager getViewOnceMessageManager() {
    return viewOnceMessageManager;
  }

  public boolean isAppVisible() {
    return isAppVisible;
  }

  public PersistentLogger getPersistentLogger() {
    return persistentLogger;
  }

  public void checkBuildExpiration() {
    if (Util.getTimeUntilBuildExpiry() <= 0 && !SignalStore.misc().isClientDeprecated()) {
      Log.w(TAG, "Build expired!");
      SignalStore.misc().markClientDeprecated();
    }
  }

  private void initializeSecurityProvider() {
    try {
      Class.forName("org.signal.aesgcmprovider.AesGcmCipher");
    } catch (ClassNotFoundException e) {
      Log.e(TAG, "Failed to find AesGcmCipher class");
      throw new ProviderInitializationException();
    }

    int aesPosition = Security.insertProviderAt(new AesGcmProvider(), 1);
    Log.i(TAG, "Installed AesGcmProvider: " + aesPosition);

    if (aesPosition < 0) {
      Log.e(TAG, "Failed to install AesGcmProvider()");
      throw new ProviderInitializationException();
    }

    int conscryptPosition = Security.insertProviderAt(Conscrypt.newProvider(), 2);
    Log.i(TAG, "Installed Conscrypt provider: " + conscryptPosition);

    if (conscryptPosition < 0) {
      Log.w(TAG, "Did not install Conscrypt provider. May already be present.");
    }
  }

  private void initializeLogging() {
    persistentLogger = new PersistentLogger(this, LogSecretProvider.getOrCreateAttachmentSecret(this), BuildConfig.VERSION_NAME);
    org.signal.core.util.logging.Log.initialize(new AndroidLogger(), persistentLogger);

    SignalProtocolLoggerProvider.setProvider(new CustomSignalProtocolLogger());
  }

  private void initializeCrashHandling() {
    final Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new SignalUncaughtExceptionHandler(originalHandler));
  }

  private void initializeApplicationMigrations() {
    ApplicationMigrations.onApplicationCreate(this, ApplicationDependencies.getJobManager());
  }

  public void initializeMessageRetrieval() {
    ApplicationDependencies.getIncomingMessageObserver();
  }

  private void initializeAppDependencies() {
    ApplicationDependencies.init(this, new ApplicationDependencyProvider(this, new SignalServiceNetworkAccess(this)));
  }

  private void initializeFirstEverAppLaunch() {
    if (TextSecurePreferences.getFirstInstallVersion(this) == -1) {
      if (!SQLCipherOpenHelper.databaseFileExists(this) || VersionTracker.getDaysSinceFirstInstalled(this) < 365) {
        Log.i(TAG, "First ever app launch!");
        AppInitialization.onFirstEverAppLaunch(this);
      }

      Log.i(TAG, "Setting first install version to " + BuildConfig.CANONICAL_VERSION_CODE);
      TextSecurePreferences.setFirstInstallVersion(this, BuildConfig.CANONICAL_VERSION_CODE);
    } else if (!TextSecurePreferences.isPasswordDisabled(this) && VersionTracker.getDaysSinceFirstInstalled(this) < 90) {
      Log.i(TAG, "Detected a new install that doesn't have passphrases disabled -- assuming bad initialization.");
      AppInitialization.onRepairFirstEverAppLaunch(this);
    }
  }

  private void initializeGcmCheck() {
    if (TextSecurePreferences.isPushRegistered(this)) {
      long nextSetTime = TextSecurePreferences.getFcmTokenLastSetTime(this) + TimeUnit.HOURS.toMillis(6);

      if (TextSecurePreferences.getFcmToken(this) == null || nextSetTime <= System.currentTimeMillis()) {
        ApplicationDependencies.getJobManager().add(new FcmRefreshJob());
      }
    }
  }

  private void initializeSignedPreKeyCheck() {
    if (!TextSecurePreferences.isSignedPreKeyRegistered(this)) {
      ApplicationDependencies.getJobManager().add(new CreateSignedPreKeyJob(this));
    }
  }

  private void initializeExpiringMessageManager() {
    this.expiringMessageManager = new ExpiringMessageManager(this);
  }

  private void initializeRevealableMessageManager() {
    this.viewOnceMessageManager = new ViewOnceMessageManager(this);
  }

  private void initializePeriodicTasks() {
    RotateSignedPreKeyListener.schedule(this);
    DirectoryRefreshListener.schedule(this);
    LocalBackupListener.schedule(this);
    RotateSenderCertificateListener.schedule(this);

    if (BuildConfig.PLAY_STORE_DISABLED) {
      UpdateApkRefreshListener.schedule(this);
    }
  }

  private void initializeRingRtc() {
    try {
      Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
        add("Moto G5");
        add("Moto G (5S) Plus");
        add("Moto G4");
        add("TA-1053");
        add("Mi A1");
        add("Mi A2");
        add("E5823"); // Sony z5 compact
        add("Redmi Note 5");
        add("FP2"); // Fairphone FP2
        add("MI 5");
      }};

      Set<String> OPEN_SL_ES_WHITELIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
      }};

      if (HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
      }

      if (!OPEN_SL_ES_WHITELIST.contains(Build.MODEL)) {
        WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
      }

      CallManager.initialize(this, new RingRtcLogger());
    } catch (UnsatisfiedLinkError e) {
      throw new AssertionError("Unable to load ringrtc library", e);
    }
  }

  @WorkerThread
  private void initializeCircumvention() {
    if (new SignalServiceNetworkAccess(ApplicationContext.this).isCensored(ApplicationContext.this)) {
      try {
        ProviderInstaller.installIfNeeded(ApplicationContext.this);
      } catch (Throwable t) {
        Log.w(TAG, t);
      }
    }
  }

  private void executePendingContactSync() {
    if (TextSecurePreferences.needsFullContactSync(this)) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob(true));
    }
  }

  private void initializePendingMessages() {
    if (TextSecurePreferences.getNeedsMessagePull(this)) {
      Log.i(TAG, "Scheduling a message fetch.");
      if (Build.VERSION.SDK_INT >= 26) {
        FcmJobService.schedule(this);
      } else {
        ApplicationDependencies.getJobManager().add(new PushNotificationReceiveJob(this));
      }
      TextSecurePreferences.setNeedsMessagePull(this, false);
    }
  }

  @WorkerThread
  private void initializeBlobProvider() {
    BlobProvider.getInstance().onSessionStart(this);
  }

  @WorkerThread
  private void initializeCleanup() {
    int deleted = DatabaseFactory.getAttachmentDatabase(this).deleteAbandonedPreuploadedAttachments();
    Log.i(TAG, "Deleted " + deleted + " abandoned attachments.");
  }

  private void initializeGlideCodecs() {
    SignalGlideCodecs.setLogProvider(new org.signal.glide.Log.Provider() {
      @Override
      public void v(@NonNull String tag, @NonNull String message) {
        Log.v(tag, message);
      }

      @Override
      public void d(@NonNull String tag, @NonNull String message) {
        Log.d(tag, message);
      }

      @Override
      public void i(@NonNull String tag, @NonNull String message) {
        Log.i(tag, message);
      }

      @Override
      public void w(@NonNull String tag, @NonNull String message) {
        Log.w(tag, message);
      }

      @Override
      public void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        Log.e(tag, message, throwable);
      }
    });
  }

  @Override
  protected void attachBaseContext(Context base) {
    DynamicLanguageContextWrapper.updateContext(base);
    super.attachBaseContext(base);
  }

  private static class ProviderInitializationException extends RuntimeException {
  }
}
