package xyz.hanoman.messenger.storage;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.groups.GroupId;

/**
 * Allows a caller to determine if a group exists in the local data store already. Needed primarily
 * to check if a local GV2 group already exists for a remote GV1 group.
 */
public interface GroupV2ExistenceChecker {
  boolean exists(@NonNull GroupId.V2 groupId);
}
