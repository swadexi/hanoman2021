package xyz.hanoman.messenger.jobmanager.impl;

import android.app.job.JobInfo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobmanager.Constraint;

/**
 * A constraint that is met once we have pulled down and decrypted all messages from the websocket
 * during initial load. See {@link xyz.hanoman.messenger.messages.IncomingMessageObserver}.
 */
public final class DecryptionsDrainedConstraint implements Constraint {

  public static final String KEY = "WebsocketDrainedConstraint";

  private DecryptionsDrainedConstraint() {
  }

  @Override
  public boolean isMet() {
    return ApplicationDependencies.getIncomingMessageObserver().isDecryptionDrained();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @RequiresApi(26)
  @Override
  public void applyToJobInfo(@NonNull JobInfo.Builder jobInfoBuilder) {
  }

  public static final class Factory implements Constraint.Factory<DecryptionsDrainedConstraint> {

    @Override
    public DecryptionsDrainedConstraint create() {
      return new DecryptionsDrainedConstraint();
    }
  }
}
