package io.github.oliviercailloux.grade.markers;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class MarkHelper {

  /**
   * Returns an empty set iff no element match any predicate; give it a predicate that matches
   * everything as last predicate if you want to make sure the returned set is non empty when the
   * given set is non empty.
   */
  public static <E> ImmutableSet<E> findBestMatches(Set<E> sources, List<Predicate<E>> predicates) {
    final ImmutableSetMultimap<Integer, E> byMatchDegree = sources.stream().collect(
        ImmutableSetMultimap.toImmutableSetMultimap(s -> getMatchQuality(s, predicates), s -> s));
    if (byMatchDegree.isEmpty()) {
      verify(sources.isEmpty());
      return ImmutableSet.of();
    }
    final int bestQuality = byMatchDegree.isEmpty() ? -predicates.size()
        : byMatchDegree.keySet().stream().max(Comparator.naturalOrder()).get();
    final ImmutableSet<E> bestSources =
        bestQuality == -predicates.size() ? ImmutableSet.of() : byMatchDegree.get(bestQuality);
    return bestSources;
  }

  private static <E> int getMatchQuality(E element, List<Predicate<E>> predicates) {
    int quality = 0;
    for (Predicate<E> predicate : predicates) {
      if (predicate.test(element)) {
        return quality;
      }
      --quality;
    }
    return quality;
  }
}
