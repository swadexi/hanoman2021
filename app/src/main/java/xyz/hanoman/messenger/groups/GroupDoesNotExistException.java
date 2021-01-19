package xyz.hanoman.messenger.groups;

public final class GroupDoesNotExistException extends GroupChangeException {

  public GroupDoesNotExistException(Throwable throwable) {
    super(throwable);
  }
}
