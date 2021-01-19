package xyz.hanoman.messenger.database.model;

import android.content.Context;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.database.model.databaseprotos.GroupCallUpdateDetails;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.util.DateUtils;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Create a group call update message based on time and joined members.
 */
public class GroupCallUpdateMessageFactory implements UpdateDescription.StringFactory {
  private final Context                context;
  private final List<UUID>             joinedMembers;
  private final boolean                withTime;
  private final GroupCallUpdateDetails groupCallUpdateDetails;
  private final UUID                   selfUuid;

  public GroupCallUpdateMessageFactory(@NonNull Context context,
                                       @NonNull List<UUID> joinedMembers,
                                       boolean withTime,
                                       @NonNull GroupCallUpdateDetails groupCallUpdateDetails)
  {
    this.context                = context;
    this.joinedMembers          = new ArrayList<>(joinedMembers);
    this.withTime               = withTime;
    this.groupCallUpdateDetails = groupCallUpdateDetails;
    this.selfUuid               = TextSecurePreferences.getLocalUuid(context);

    boolean removed = this.joinedMembers.remove(selfUuid);
    if (removed) {
      this.joinedMembers.add(selfUuid);
    }
  }

  @Override
  public @NonNull String create() {
    String time = DateUtils.getTimeString(context, Locale.getDefault(), groupCallUpdateDetails.getStartedCallTimestamp());

    switch (joinedMembers.size()) {
      case 0:
        return withTime ? context.getString(R.string.MessageRecord_group_call_s, time)
                        : context.getString(R.string.MessageRecord_group_call);
      case 1:
        if (joinedMembers.get(0).toString().equals(groupCallUpdateDetails.getStartedCallUuid())) {
          return withTime ? context.getString(R.string.MessageRecord_s_started_a_group_call_s, describe(joinedMembers.get(0)), time)
                          : context.getString(R.string.MessageRecord_s_started_a_group_call, describe(joinedMembers.get(0)));
        } else if (Objects.equals(joinedMembers.get(0), selfUuid)) {
          return withTime ? context.getString(R.string.MessageRecord_you_are_in_the_group_call_s1, time)
                          : context.getString(R.string.MessageRecord_you_are_in_the_group_call);
        } else {
          return withTime ? context.getString(R.string.MessageRecord_s_is_in_the_group_call_s, describe(joinedMembers.get(0)), time)
                          : context.getString(R.string.MessageRecord_s_is_in_the_group_call, describe(joinedMembers.get(0)));
        }
      case 2:
        return withTime ? context.getString(R.string.MessageRecord_s_and_s_are_in_the_group_call_s1,
                                            describe(joinedMembers.get(0)),
                                            describe(joinedMembers.get(1)),
                                            time)
                        : context.getString(R.string.MessageRecord_s_and_s_are_in_the_group_call,
                                            describe(joinedMembers.get(0)),
                                            describe(joinedMembers.get(1)));
      default:
        int others = joinedMembers.size() - 2;
        return withTime ? context.getResources().getQuantityString(R.plurals.MessageRecord_s_s_and_d_others_are_in_the_group_call_s,
                                                                   others,
                                                                   describe(joinedMembers.get(0)),
                                                                   describe(joinedMembers.get(1)),
                                                                   others,
                                                                   time)
                        : context.getResources().getQuantityString(R.plurals.MessageRecord_s_s_and_d_others_are_in_the_group_call,
                                                                   others,
                                                                   describe(joinedMembers.get(0)),
                                                                   describe(joinedMembers.get(1)),
                                                                   others);
    }
  }

  private @NonNull String describe(@NonNull UUID uuid) {
    if (UuidUtil.UNKNOWN_UUID.equals(uuid)) {
      return context.getString(R.string.MessageRecord_unknown);
    }

    Recipient recipient = Recipient.resolved(RecipientId.from(uuid, null));

    if (recipient.isSelf()) {
      return context.getString(R.string.MessageRecord_you);
    } else {
      return recipient.getShortDisplayName(context);
    }
  }
}
