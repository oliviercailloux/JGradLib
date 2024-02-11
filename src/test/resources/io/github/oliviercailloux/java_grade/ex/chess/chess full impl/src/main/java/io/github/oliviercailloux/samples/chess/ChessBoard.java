package io.github.oliviercailloux.samples.chess;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * <p>
 * A chess board that represents the 64 squares of a chess board, each one containing a chess
 * {@link Piece} or nothing.
 * </p>
 * <p>
 * This class rejects all {@code null} arguments (and all arguments that are not legal according to
 * the respective contracts).
 * </p>
 * <p>
 * This class uses the <a href="https://en.wikipedia.org/wiki/Algebraic_notation_(chess)">Algebraic
 * notation</a> to denote positions: the bottom left square is referred to as "a1", the bottom right
 * square is "h1", the top left square is "a8"…
 * </p>
 * <p>
 * <em>Hint for implementors</em>: it seems wise to store a {@code Map&lt;String, Piece>}
 * internally.
 * </p>
 */
public interface ChessBoard {
  /**
   * <p>
   * Sets the current board that this object represents.
   * </p>
   * <p>
   * This method accepts only legal boards: a board is legal if, and only if, it contains exactly
   * one white King and one black King.
   * </p>
   * <p>
   * <em>Hint for implementors</em>: it is very important to implement this method correctly.
   * Otherwise, most of the other methods will not work!
   * </p>
   *
   * @param inputBoard a list of exactly 64 entries, one for each square on a chess board,
   *        indicating, for each square, whether there is a {@link Piece} on that square, and if so,
   *        which piece. Each entry in this list consists of the empty string if there is no piece
   *        at the corresponding square, or two capital letters, corresponding to the color of a
   *        piece followed by its identification letter. The first entry in this list corresponds to
   *        the square "a1", the second one to "b1", …, the eighth one to "h1", the ninth one to
   *        "a2", and so on until "h8".
   * @return {@code true} iff this board has changed as a result of this call.
   * @see #setBoardByPieces(List)
   */
  public boolean setBoardByString(List<String> inputBoard);

  /**
   * <p>
   * Sets the current board that this object represents.
   * </p>
   * <p>
   * This method works exactly as {@link #setBoardByString(List)} with the difference that the
   * entries in the list are an empty optional if there is no piece at the corresponding square, or
   * an optional containing the piece at the corresponding square.
   * </p>
   */
  public boolean setBoardByPieces(List<Optional<Piece>> inputBoard);

  /**
   * <p>
   * Returns a map that has positions as keys and (string) pieces as values.
   * </p>
   * <p>
   * A position key is in the map if, and only if, there is a piece at that position. A position key
   * is a two letter string representing a square on the board, such as "a1" for example. A value is
   * a two letter string, the first of which is a color, the second of which is an identification
   * letter of a piece, such as "BK" for example.
   * </p>
   *
   * @see Piece
   */
  public ImmutableMap<String, String> getStringPiecesByPosition();

  /**
   * Returns a map that has positions as keys and pieces as values. A position key is in the map if,
   * and only if, there is a piece at that position.
   *
   * @see #getStringPiecesByPosition()
   */
  public ImmutableMap<String, Piece> getPiecesByPosition();

  /**
   * Returns an optional containing the piece at the given position, or an empty optional if there
   * is no piece at the given position in this board.
   *
   * @param position any position from {@code "a1"} to {@code "h8"}
   */
  public Optional<Piece> getPieceByPosition(String position);

  /**
   * Returns the set of pieces on this board that have the given color. (Note that this method does
   * not permit to distinguish, for example, the white player having two rooks from having only
   * one.)
   *
   * @param color the string "B" or "W"
   */
  public ImmutableSet<Piece> getPieces(String color);

  /**
   * Returns the list of pieces on this board of the given color, ordered alphabetically by their
   * identifying letter.
   *
   * @param color the string "B" or "W"
   */
  public ImmutableList<Piece> getOrderedPieces(String color);

  /**
   * <p>
   * Changes this board so that the given piece moves to its new position: the old position becomes
   * an empty square, and the piece at the old position becomes the piece at the new position.
   * becomes If another piece was at the new position, that other piece disappears from the board as
   * a result.
   * </p>
   * <p>
   * This class does not check that this is a legal move by the rules of the game, but this class
   * does check, as usual, that the positions are syntactically valid (for example, it rejects the
   * string "aaabbb" as one of the arguments) and that there is indeed a piece at the old position.
   * </p>
   * <p>
   * (Note for those who know the rules of chess: for simplicity, this method does not implement the
   * special rules of capture of pawns or castling.)
   * </p>
   *
   * @param oldPosition the position the piece moves from, any position from {@code "a1"} to
   *        {@code "h8"} containing a piece
   * @param newPosition the position that piece is going to, any position from {@code "a1"} to
   *        {@code "h8"}
   */
  public void movePiece(String oldPosition, String newPosition);
}
