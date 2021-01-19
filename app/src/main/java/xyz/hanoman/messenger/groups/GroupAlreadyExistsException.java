package xyz.hanoman.messenger.groups;

public final class GroupAlreadyExistsException extends GroupChangeException {

  public GroupAlreadyExistsException(Throwable throwable) {
    super(throwable);
  }
}
