package io.github.oliviercailloux.samples.chess;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
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
    final Builder<Optional<Piece>> builder = ImmutableList.builder();
    for (String pieceStr : inputBoard) {
      final Optional<Piece> piece;
      if (pieceStr.isEmpty()) {
        piece = Optional.empty();
      } else {
        checkArgument(pieceStr.length() == 2);
        piece = Optional.of(Piece.given(pieceStr.substring(0, 1), pieceStr.substring(1, 2)));
      }
      builder.add(piece);
    }
    final ImmutableList<Optional<Piece>> inputBoardP = builder.build();

    final boolean changed = setBoardByPieces(inputBoardP);
    return changed;
  }

  @Override
  public boolean setBoardByPieces(List<Optional<Piece>> inputBoard) {
    checkArgument(inputBoard.size() == 64);

    boolean changed = false;
    boolean seenWK = false;
    boolean seenBK = false;

    final LinkedHashMap<String, Piece> newBoard = new LinkedHashMap<>();
    final Iterator<Optional<Piece>> iterator = inputBoard.iterator();
    for (int row = 1; row <= 8; ++row) {
      for (String col : COLUMNS) {
        final String coordinates = col + String.valueOf(row);

        final Optional<Piece> pieceOpt = iterator.next();
        if (pieceOpt.isPresent()) {
          final Piece piece = pieceOpt.get();
          newBoard.put(coordinates, piece);
          if (piece.isWhite() && piece.getIdentifyingLetter().equals("K")) {
            checkArgument(!seenWK);
            seenWK = true;
          }
          if (piece.isBlack() && piece.getIdentifyingLetter().equals("K")) {
            checkArgument(!seenBK);
            seenBK = true;
          }
        }

        final Optional<Piece> previous = getPieceByPosition(coordinates);
        if (!pieceOpt.equals(previous)) {
          changed = true;
        }
      }
    }
    checkArgument(seenWK);
    checkArgument(seenBK);

    board = newBoard;

    return changed;
  }

  @Override
  public ImmutableMap<String, String> getStringPiecesByPosition() {
    final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (String position : board.keySet()) {
      final Piece piece = board.get(position);
      builder.put(position, piece.getColor() + piece.getIdentifyingLetter());
    }
    return builder.build();
  }

  @Override
  public ImmutableMap<String, Piece> getPiecesByPosition() {
    return ImmutableMap.copyOf(board);
  }

  @Override
  public Optional<Piece> getPieceByPosition(String position) {
    checkNotNull(position);
    checkArgument(position.length() == 2);
    final String column = position.substring(0, 1);
    final String row = position.substring(1, 2);
    checkArgument(COLUMNS.contains(column));
    final int rowNb;
    try {
      rowNb = Integer.parseInt(row);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(e);
    }
    checkArgument(1 <= rowNb);
    checkArgument(rowNb <= 8);

    return Optional.ofNullable(board.get(position));
  }

  @Override
  public ImmutableSet<Piece> getPieces(String color) {
    final ImmutableList<Piece> pieces = getPiecesList(color);
    return ImmutableSet.copyOf(pieces);
  }

  private ImmutableList<Piece> getPiecesList(String color) {
    checkNotNull(color);
    checkArgument(color.equals("W") || color.equals("B"));

    final ImmutableList.Builder<Piece> builder = ImmutableList.builder();
    for (Piece piece : board.values()) {
      if (piece.getColor().equals(color)) {
        builder.add(piece);
      }
    }

    final ImmutableList<Piece> pieces = builder.build();
    return pieces;
  }

  @Override
  public ImmutableList<Piece> getOrderedPieces(String color) {
    final ImmutableList<Piece> pieces = getPiecesList(color);
    final List<Piece> list = new ArrayList<>(pieces);
    Collections.sort(list, Piece.getComparator());
    return ImmutableList.copyOf(list);
  }

  @Override
  public void movePiece(String oldPosition, String newPosition) {
    final Piece piece = board.get(oldPosition);
    checkArgument(piece != null);
    board.remove(oldPosition);
    board.put(newPosition, piece);
  }

}
