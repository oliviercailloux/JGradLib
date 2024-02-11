package io.github.oliviercailloux.samples.chess;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Comparator;
import java.util.Objects;

import com.google.common.base.MoreObjects;

/**
 * A piece of the game of chess, with its <em>color</em> ({@code W} for White and {@code B} for
 * Black) and its <em>identifying letter</em> ({@code P} for Pawn, {@code R} for Rook, {@code B} for
 * Bishop, {@code N} for kNight, {@code Q} for Queen, {@code K} for King).
 * </p>
 * <p>
 * (A better design would use {@link Enum}s.)
 * </p>
 * <p>
 * This class must not be modified.
 * </p>
 *
 */
public class Piece {
  private final String color;
  private final String identifyingLetter;

  public static Comparator<Piece> getComparator() {
    return Comparator.comparing(Piece::getColor).thenComparing(Piece::getIdentifyingLetter);
  }

  /**
   * @param color one of "W" or "B"
   * @param identifyingLetter one of "P", "R", "B", "N", "Q", "K"
   * @return the corresponding piece
   */
  public static Piece given(String color, String identifyingLetter) {
    return new Piece(color, identifyingLetter);
  }

  /**
   * @param color one of "W" or "B"
   * @return the corresponding piece
   */
  public static Piece pawn(String color) {
    return new Piece(color, "P");
  }

  /**
   * @param color one of "W" or "B"
   * @return the corresponding piece
   */
  public static Piece rook(String color) {
    return new Piece(color, "R");
  }

  /**
   * @param color one of "W" or "B"
   * @return the corresponding piece
   */
  public static Piece knight(String color) {
    return new Piece(color, "N");
  }

  /**
   * @param color one of "W" or "B"
   * @return the corresponding piece
   */
  public static Piece bishop(String color) {
    return new Piece(color, "B");
  }

  /**
   * @param color one of "W" or "B"
   * @return the corresponding piece
   */
  public static Piece queen(String color) {
    return new Piece(color, "Q");
  }

  /**
   * @param color one of "W" or "B"
   * @return the corresponding piece
   */
  public static Piece king(String color) {
    return new Piece(color, "K");
  }

  private Piece(String color, String identifyingLetter) {
    this.color = checkNotNull(color);
    checkArgument(color.equals("W") || color.equals("B"));

    this.identifyingLetter = checkNotNull(identifyingLetter);
    switch (identifyingLetter) {
      case "P":
      case "R":
      case "N":
      case "B":
      case "Q":
      case "K":
        break;
      default:
        throw new IllegalArgumentException();
    }
  }

  /**
   * Returns the color of this piece.
   *
   * @return one of "W" for a white piece or "B" for a black piece.
   */
  public String getColor() {
    return color;
  }

  public boolean isWhite() {
    return color.equals("W");
  }

  public boolean isBlack() {
    return color.equals("B");
  }

  /**
   * Returns the identifying letter of this piece.
   *
   * @return one of "P", "R", "B", "N", "Q", "K"
   */
  public String getIdentifyingLetter() {
    return identifyingLetter;
  }

  /**
   * Returns {@code true} iff the given object is also a {@link Piece} and has the same color and
   * identifying letter as this one.
   */
  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof Piece)) {
      return false;
    }
    final Piece p2 = (Piece) o2;
    return color.equals(p2.color) && identifyingLetter.equals(p2.identifyingLetter);
  }

  @Override
  public int hashCode() {
    return Objects.hash(color, identifyingLetter);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("Color", color)
        .add("Identifying letter", identifyingLetter).toString();
  }
}
