package io.github.oliviercailloux.samples.chess;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

class MyChessBoardTests {
  @Test
  void testGetStringPiecesByPosition() throws Exception {
    final MyChessBoard board = MyChessBoard.newInstance();
    assertEquals(ImmutableMap.of("e1", "WK", "e8", "BK"), board.getStringPiecesByPosition());

    board.setBoardByString(getBoardRNK());
    final ImmutableMap<String, String> expected =
        ImmutableMap.<String, String>builder().put("a1", "WR").put("e1", "WK").put("h1", "WR")
            .put("a8", "BR").put("b8", "BN").put("e8", "BK").build();
    assertEquals(expected, board.getStringPiecesByPosition());
  }

  @Test
  void testGetPiecesByPosition() throws Exception {
    final MyChessBoard board = MyChessBoard.newInstance();
    assertEquals(ImmutableMap.of("e1", Piece.king("W"), "e8", Piece.king("B")),
        board.getPiecesByPosition());

    board.setBoardByString(getBoardRNK());
    final ImmutableMap<String,
        Piece> expected = ImmutableMap.<String, Piece>builder().put("a1", Piece.rook("W"))
            .put("e1", Piece.king("W")).put("h1", Piece.rook("W")).put("a8", Piece.rook("B"))
            .put("b8", Piece.knight("B")).put("e8", Piece.king("B")).build();
    assertEquals(expected, board.getPiecesByPosition());
  }

  @Test
  void testGetPieceByPosition() throws Exception {
    final MyChessBoard board = MyChessBoard.newInstance();
    assertEquals(Optional.of(Piece.king("W")), board.getPieceByPosition("e1"));
    assertEquals(Optional.empty(), board.getPieceByPosition("e2"));
    assertEquals(Optional.of(Piece.king("B")), board.getPieceByPosition("e8"));

    board.setBoardByString(getBoardRNK());
    assertEquals(Optional.of(Piece.rook("W")), board.getPieceByPosition("a1"));
    assertEquals(Optional.empty(), board.getPieceByPosition("a2"));
    assertEquals(Optional.of(Piece.king("W")), board.getPieceByPosition("e1"));
    assertEquals(Optional.of(Piece.rook("W")), board.getPieceByPosition("h1"));
    assertEquals(Optional.of(Piece.rook("B")), board.getPieceByPosition("a8"));
    assertEquals(Optional.of(Piece.knight("B")), board.getPieceByPosition("b8"));
    assertEquals(Optional.of(Piece.king("B")), board.getPieceByPosition("e8"));
  }

  @Test
  void testGetPieces() throws Exception {
    final MyChessBoard board = MyChessBoard.newInstance();
    assertEquals(ImmutableSet.of(Piece.king("W")), board.getPieces("W"));
    assertEquals(ImmutableSet.of(Piece.king("B")), board.getPieces("B"));

    board.setBoardByString(getBoardRNK());
    assertEquals(ImmutableSet.of(Piece.king("W"), Piece.rook("W")), board.getPieces("W"));
    assertEquals(ImmutableSet.of(Piece.rook("B"), Piece.king("B"), Piece.knight("B")),
        board.getPieces("B"));
  }

  @Test
  void testGetOrderedPieces() throws Exception {
    final MyChessBoard board = MyChessBoard.newInstance();
    assertEquals(ImmutableList.of(Piece.king("W")), board.getOrderedPieces("W"));
    assertEquals(ImmutableList.of(Piece.king("B")), board.getOrderedPieces("B"));

    board.setBoardByString(getBoardRNK());
    assertEquals(ImmutableList.of(Piece.king("W"), Piece.rook("W"), Piece.rook("W")),
        board.getOrderedPieces("W"));
    assertEquals(ImmutableList.of(Piece.king("B"), Piece.knight("B"), Piece.rook("B")),
        board.getOrderedPieces("B"));
  }

  /**
   * TODO test to be completed…
   */
  @Test
  void testMovePieces() throws Exception {
    // final MyChessBoard board = MyChessBoard.newInstance();

  }

  private ImmutableList<String> getBoardRNK() {
    final ImmutableList<String> row8 = ImmutableList.of("BR", "BN", "", "", "BK", "", "", "");
    final ImmutableList<String> row7 = ImmutableList.of("", "", "", "", "", "", "", "");
    final ImmutableList<String> row6 = ImmutableList.of("", "", "", "", "", "", "", "");
    final ImmutableList<String> row5 = ImmutableList.of("", "", "", "", "", "", "", "");
    final ImmutableList<String> row4 = ImmutableList.of("", "", "", "", "", "", "", "");
    final ImmutableList<String> row3 = ImmutableList.of("", "", "", "", "", "", "", "");
    final ImmutableList<String> row2 = ImmutableList.of("", "", "", "", "", "", "", "");
    final ImmutableList<String> row1 = ImmutableList.of("WR", "", "", "", "WK", "", "", "WR");
    /** Joins the eight rows into one big list of 64 entries. */
    return ImmutableList.of(row1, row2, row3, row4, row5, row6, row7, row8).stream()
        .flatMap(ImmutableList::stream).collect(ImmutableList.toImmutableList());
  }
}
