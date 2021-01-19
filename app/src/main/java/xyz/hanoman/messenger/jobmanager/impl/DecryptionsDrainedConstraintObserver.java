package xyz.hanoman.messenger.jobmanager.impl;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobmanager.ConstraintObserver;

/**
 * An observer for {@link DecryptionsDrainedConstraint}. Will fire when the websocket is drained and
 * the relevant decryptions have finished.
 */
public class DecryptionsDrainedConstraintObserver implements ConstraintObserver {

  private static final String REASON = DecryptionsDrainedConstraintObserver.class.getSimpleName();

  @Override
  public void register(@NonNull Notifier notifier) {
    ApplicationDependencies.getIncomingMessageObserver().addDecryptionDrainedListener(() -> {
      notifier.onConstraintMet(REASON);
    });
  }
}
