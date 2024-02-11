package io.github.oliviercailloux.samples.chess;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class MyChessBoard implements ChessBoard {
  public static final ImmutableList<String> COLUMNS = ImmutableList.of("a", "b", "c", "d", "e", "f", "g", "h");

  private Map<String, Piece> board;

  /**
   * <p>
   * This method, with the given declaration, <b>must</b> be present.
   * </p>
   * <p>
   * Initially (for simplicity), a board has just two pieces: a White King on
   * square e1 and a Black King on square e8.
   * </p>
   *
   */
  public static MyChessBoard newInstance() {
    return new MyChessBoard();
  }

  private MyChessBoard() {
    board = new LinkedHashMap<>();
    board.put("e1", Piece.king("W"));
    board.put("e8", Piece.king("B"));
  }

  @Override
  public boolean setBoardByString(List<String> inputBoard) {
    return true;
  }

  @Override
  public boolean setBoardByPieces(List<Optional<Piece>> inputBoard) {
    return true;
  }

  @Override
  public ImmutableMap<String, String> getStringPiecesByPosition() {
    return ImmutableMap.of();
  }

  @Override
  public ImmutableMap<String, Piece> getPiecesByPosition() {
    return ImmutableMap.copyOf(board);
  }

  @Override
  public Optional<Piece> getPieceByPosition(String position) {
    return Optional.of(board.get(position));
  }

  @Override
  public ImmutableSet<Piece> getPieces(String color) {
    return ImmutableSet.of();
  }

  @Override
  public ImmutableList<Piece> getOrderedPieces(String color) {
    return ImmutableList.of();
  }

  @Override
  public void movePiece(String oldPosition, String newPosition) {
  }

}
