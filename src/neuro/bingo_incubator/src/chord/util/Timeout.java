package chord.util;

public interface Timeout {
  void check() throws ChordTimeoutException;
}
