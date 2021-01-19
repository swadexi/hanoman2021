package xyz.hanoman.messenger.groups.ui.addmembers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import xyz.hanoman.messenger.contacts.SelectedContact;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.groups.GroupId;
import xyz.hanoman.messenger.recipients.RecipientId;

final class AddMembersRepository {

  private final Context context;
  private final GroupId groupId;

  AddMembersRepository(@NonNull GroupId groupId) {
    this.groupId = groupId;
    this.context = ApplicationDependencies.getApplication();
  }

  @WorkerThread
  RecipientId getOrCreateRecipientId(@NonNull SelectedContact selectedContact) {
    return selectedContact.getOrCreateRecipientId(context);
  }

  @WorkerThread
  String getGroupTitle() {
    return DatabaseFactory.getGroupDatabase(context).requireGroup(groupId).getTitle();
  }
}
