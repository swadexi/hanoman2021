package xyz.hanoman.messenger.groups;

public final class GroupNotAMemberException extends GroupChangeException {

  public GroupNotAMemberException(Throwable throwable) {
    super(throwable);
  }

  GroupNotAMemberException() {
  }
}
