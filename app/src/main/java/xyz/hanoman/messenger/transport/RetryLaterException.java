package xyz.hanoman.messenger.transport;

public class RetryLaterException extends Exception {
  public RetryLaterException() {}

  public RetryLaterException(Exception e) {
    super(e);
  }
}
