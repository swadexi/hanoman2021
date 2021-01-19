package xyz.hanoman.messenger.storage;

import org.junit.Test;
import xyz.hanoman.messenger.groups.GroupId;
import xyz.hanoman.messenger.storage.StorageSyncHelper.KeyGenerator;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static xyz.hanoman.messenger.testutil.TestHelpers.byteArray;
import static xyz.hanoman.messenger.testutil.ZkGroupLibraryUtil.assumeZkGroupSupportedOnOS;

public final class GroupV1ConflictMergerTest {

  private static final byte[]       GENERATED_KEY = byteArray(8675309);
  private static final KeyGenerator KEY_GENERATOR = mock(KeyGenerator.class);

  static {
    when(KEY_GENERATOR.generate()).thenReturn(GENERATED_KEY);
  }

  @Test
  public void merge_alwaysPreferRemote() {
    SignalGroupV1Record remote = new SignalGroupV1Record.Builder(byteArray(1), byteArray(100, 16))
                                                        .setBlocked(false)
                                                        .setProfileSharingEnabled(false)
                                                        .setArchived(false)
                                                        .setForcedUnread(false)
                                                        .build();
    SignalGroupV1Record local  = new SignalGroupV1Record.Builder(byteArray(2), byteArray(100, 16))
                                                        .setBlocked(true)
                                                        .setProfileSharingEnabled(true)
                                                        .setArchived(true)
                                                        .setForcedUnread(true)
                                                        .build();

    SignalGroupV1Record merged = new GroupV1ConflictMerger(Collections.singletonList(local), id -> false).merge(remote, local, KEY_GENERATOR);

    assertArrayEquals(remote.getId().getRaw(), merged.getId().getRaw());
    assertArrayEquals(byteArray(100, 16), merged.getGroupId());
    assertFalse(merged.isProfileSharingEnabled());
    assertFalse(merged.isBlocked());
    assertFalse(merged.isArchived());
    assertFalse(merged.isForcedUnread());
  }

  @Test
  public void merge_returnRemoteIfEndResultMatchesRemote() {
    SignalGroupV1Record remote = new SignalGroupV1Record.Builder(byteArray(1), byteArray(100, 16))
                                                        .setBlocked(false)
                                                        .setProfileSharingEnabled(true)
                                                        .setArchived(true)
                                                        .build();
    SignalGroupV1Record local  = new SignalGroupV1Record.Builder(byteArray(2), byteArray(100, 16))
                                                        .setBlocked(true)
                                                        .setProfileSharingEnabled(false)
                                                        .setArchived(false)
                                                        .build();

    SignalGroupV1Record merged = new GroupV1ConflictMerger(Collections.singletonList(local), id -> false).merge(remote, local, mock(KeyGenerator.class));

    assertEquals(remote, merged);
  }

  @Test
  public void merge_excludeBadGroupId() {
    assumeZkGroupSupportedOnOS();

    SignalGroupV1Record badRemote  = new SignalGroupV1Record.Builder(byteArray(1), badGroupKey(99))
                                                            .setBlocked(false)
                                                            .setProfileSharingEnabled(true)
                                                            .setArchived(true)
                                                            .build();

    SignalGroupV1Record goodRemote = new SignalGroupV1Record.Builder(byteArray(1), groupKey(99))
                                                            .setBlocked(false)
                                                            .setProfileSharingEnabled(true)
                                                            .setArchived(true)
                                                            .build();

    Collection<SignalGroupV1Record> invalid = new GroupV1ConflictMerger(Collections.emptyList(), id -> false).getInvalidEntries(Arrays.asList(badRemote, goodRemote));

    assertEquals(Collections.singletonList(badRemote), invalid);
  }

  @Test
  public void merge_excludeMigratedGroupId() {
    assumeZkGroupSupportedOnOS();

    GroupId.V1 v1Id = GroupId.v1orThrow(groupKey(1));
    GroupId.V2 v2Id = v1Id.deriveV2MigrationGroupId();

    SignalGroupV1Record badRemote  = new SignalGroupV1Record.Builder(byteArray(1), v1Id.getDecodedId())
                                                            .setBlocked(false)
                                                            .setProfileSharingEnabled(true)
                                                            .setArchived(true)
                                                            .build();

    SignalGroupV1Record goodRemote = new SignalGroupV1Record.Builder(byteArray(1), groupKey(99))
                                                            .setBlocked(false)
                                                            .setProfileSharingEnabled(true)
                                                            .setArchived(true)
                                                            .build();

    Collection<SignalGroupV1Record> invalid = new GroupV1ConflictMerger(Collections.emptyList(), id -> id.equals(v2Id)).getInvalidEntries(Arrays.asList(badRemote, goodRemote));

    assertEquals(Collections.singletonList(badRemote), invalid);
  }

  private static byte[] groupKey(int value) {
    return byteArray(value, 16);
  }

  private static byte[] badGroupKey(int value) {
    return byteArray(value, 32);
  }
}
