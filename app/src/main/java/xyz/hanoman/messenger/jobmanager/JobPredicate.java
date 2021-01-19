package xyz.hanoman.messenger.jobmanager;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.jobmanager.persistence.JobSpec;

public interface JobPredicate {
  JobPredicate NONE = jobSpec -> true;

  boolean shouldRun(@NonNull JobSpec jobSpec);
}
