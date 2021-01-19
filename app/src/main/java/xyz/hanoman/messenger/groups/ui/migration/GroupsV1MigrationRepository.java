package xyz.hanoman.messenger.groups.ui.migration;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.database.RecipientDatabase;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.groups.GroupChangeBusyException;
import xyz.hanoman.messenger.groups.GroupsV1MigrationUtil;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.jobmanager.impl.NetworkConstraint;
import xyz.hanoman.messenger.jobs.RetrieveProfileJob;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.recipients.RecipientUtil;
import xyz.hanoman.messenger.transport.RetryLaterException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class GroupsV1MigrationRepository {

  private static final String TAG = Log.tag(GroupsV1MigrationRepository.class);

  void getMigrationState(@NonNull RecipientId groupRecipientId, @NonNull Consumer<MigrationState> callback) {
    SignalExecutors.BOUNDED.execute(() -> callback.accept(getMigrationState(groupRecipientId)));
  }

  void upgradeGroup(@NonNull RecipientId recipientId, @NonNull Consumer<MigrationResult> callback) {
    SignalExecutors.UNBOUNDED.execute(() -> {
      if (!NetworkConstraint.isMet(ApplicationDependencies.getApplication())) {
        Log.w(TAG, "No network!");
        callback.accept(MigrationResult.FAILURE_NETWORK);
        return;
      }

      if (!Recipient.resolved(recipientId).isPushV1Group()) {
        Log.w(TAG, "Not a V1 group!");
        callback.accept(MigrationResult.FAILURE_GENERAL);
        return;
      }

      try {
        GroupsV1MigrationUtil.migrate(ApplicationDependencies.getApplication(), recipientId, true);
        callback.accept(MigrationResult.SUCCESS);
      } catch (IOException | RetryLaterException | GroupChangeBusyException e) {
        callback.accept(MigrationResult.FAILURE_NETWORK);
      } catch (GroupsV1MigrationUtil.InvalidMigrationStateException e) {
        callback.accept(MigrationResult.FAILURE_GENERAL);
      }
    });
  }

  @WorkerThread
  private MigrationState getMigrationState(@NonNull RecipientId groupRecipientId) {
    Recipient group = Recipient.resolved(groupRecipientId);

    if (!group.isPushV1Group()) {
      return new MigrationState(Collections.emptyList(), Collections.emptyList());
    }

    Set<RecipientId> needsRefresh = Stream.of(group.getParticipants())
                                          .filter(r -> r.getGroupsV2Capability() != Recipient.Capability.SUPPORTED ||
                                                       r.getGroupsV1MigrationCapability() != Recipient.Capability.SUPPORTED)
                                          .map(Recipient::getId)
                                          .collect(Collectors.toSet());

    List<Job> jobs = RetrieveProfileJob.forRecipients(needsRefresh);

    for (Job job : jobs) {
      if (!ApplicationDependencies.getJobManager().runSynchronously(job, TimeUnit.SECONDS.toMillis(3)).isPresent()) {
        Log.w(TAG, "Failed to refresh capabilities in time!");
      }
    }

    try {
      RecipientUtil.ensureUuidsAreAvailable(ApplicationDependencies.getApplication(), group.getParticipants());
    } catch (IOException e) {
      Log.w(TAG, "Failed to refresh UUIDs!", e);
    }

    group = group.fresh();

    List<Recipient> ineligible = Stream.of(group.getParticipants())
                                       .filter(r -> !r.hasUuid()                                                         ||
                                                    r.getGroupsV2Capability() != Recipient.Capability.SUPPORTED          ||
                                                    r.getGroupsV1MigrationCapability() != Recipient.Capability.SUPPORTED ||
                                                    r.getRegistered() != RecipientDatabase.RegisteredState.REGISTERED)
                                       .toList();

    List<Recipient> invites = Stream.of(group.getParticipants())
                                    .filterNot(ineligible::contains)
                                    .filterNot(Recipient::isSelf)
                                    .filter(r -> r.getProfileKey() == null)
                                    .toList();

    return new MigrationState(invites, ineligible);
  }
}
