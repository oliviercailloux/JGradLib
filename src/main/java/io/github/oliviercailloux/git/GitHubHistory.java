package io.github.oliviercailloux.git;

/**
 * Interface GitHistory.
 *
 * + create(Map, Graph) (using either author or commit date from RevCommits, for
 * example)
 *
 * + getGraph() // unmodifiable
 *
 * + getRoot()
 *
 * Class GitHistorySimple.
 *
 * Class GitHistoryIncreasable.
 *
 * + addCommits(Stream<>).
 *
 * Class MultiDatesHistory.
 *
 * + create(Map authorDates, Map commitDates, Map pushsDates, Graph commits)
 *
 * - graph
 *
 * + getCommitHistory
 *
 * + getAuthorHistory
 *
 * + getPushHistory // all three the same graph
 *
 * + getPushedBeforeCommitted
 *
 * GitHubUtils::toCoherent(MultiDatesHistory): MultiDatesHistory.
 *
 * @param <E>
 */
public class GitHubHistory {

}
