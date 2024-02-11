package io.github.oliviercailloux.samples.chess;

import com.google.common.collect.ImmutableList;

public class MyChessBoard implements ChessBoard {
  public static final ImmutableList<String> COLUMNS =
      ImmutableList.of("a", "b", "c", "d", "e", "f", "g", "h");

  /**
   * <p>
   * This method, with the given declaration, <b>must</b> be present.
   * </p>
   * <p>
   * Initially (for simplicity), a board has just two pieces: a White King on square e1 and a Black
   * King on square e8.
   * </p>
   *
   */
  public static MyChessBoard newInstance() {
    // TODO
  }
}
