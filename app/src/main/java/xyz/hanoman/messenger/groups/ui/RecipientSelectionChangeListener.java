package xyz.hanoman.messenger.groups.ui;

import androidx.annotation.NonNull;

import java.util.Set;

public interface RecipientSelectionChangeListener {
  void onSelectionChanged(@NonNull Set<GroupMemberEntry> selection);
}
