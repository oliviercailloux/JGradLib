package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSetMultimap.Builder;
import com.google.common.collect.ImmutableSortedSet;

import io.github.oliviercailloux.git_hub.low.Repository;
import io.github.oliviercailloux.git_hub.low.User;

/**
 *
 * TODO forbid pull requests as issues. Simplify ordering of issue with history:
 * use only the first one created among homonyms, and use only their original
 * name.
 *
 * @author Olivier Cailloux
 *
 */
public class RepositoryWithIssuesWithHistory {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryWithIssuesWithHistory.class);

	public static RepositoryWithIssuesWithHistory from(Repository bare, User owner, List<IssueWithHistory> issues) {
		return new RepositoryWithIssuesWithHistory(bare, owner, issues);
	}

	private final ImmutableSetMultimap<String, IssueWithHistory> allIssuesByName;

	private final ImmutableSetMultimap<String, IssueWithHistory> allIssuesByOriginalName;

	final private List<IssueWithHistory> issues;

	final private User owner;

	private final Repository repository;

	private RepositoryWithIssuesWithHistory(Repository bare, User owner, List<IssueWithHistory> issues) {
		this.repository = requireNonNull(bare);
		this.owner = owner;
		this.issues = requireNonNull(issues);
		{
			final Builder<String, IssueWithHistory> builderByOriginalName = ImmutableSetMultimap
					.<String, IssueWithHistory>builder().orderValuesBy(Comparator.<IssueWithHistory>naturalOrder());
			for (IssueWithHistory issue : issues) {
				builderByOriginalName.put(issue.getOriginalName(), issue);
			}
			allIssuesByOriginalName = builderByOriginalName.build();
		}
		{
			final Builder<String, IssueWithHistory> builderByName = ImmutableSetMultimap
					.<String, IssueWithHistory>builder().orderValuesBy(Comparator.<IssueWithHistory>naturalOrder());
			for (IssueWithHistory issue : issues) {
				final List<String> names = issue.getNames();
				for (String name : names) {
					builderByName.put(name, issue);
				}
			}
			allIssuesByName = builderByName.build();
		}
	}

	public Repository getBare() {
		return repository;
	}

	public List<IssueWithHistory> getIssues() {
		checkState(issues != null);
		return issues;
	}

	/**
	 * Returns all the issues that have ever had that name, ordered by their date of
	 * “first done” (earliest first), with any issues that do not have “first done”
	 * coming last.
	 *
	 * @return may be empty.
	 */
	public ImmutableSortedSet<IssueWithHistory> getIssuesNamed(String name) {
		requireNonNull(name);
		final ImmutableSet<IssueWithHistory> homonyms = allIssuesByName.get(name);
		/**
		 * Guaranteed by the way we built the ImmutableSetMultimap (except after
		 * de-serialization).
		 */
		assert homonyms instanceof ImmutableSortedSet;
		return (ImmutableSortedSet<IssueWithHistory>) homonyms;
	}

	/**
	 * Returns all the issues that have that name as original name, ordered by their
	 * date of “first done” (earliest first), with any issues that do not have
	 * “first done” coming last.
	 *
	 * @return may be empty.
	 */
	public ImmutableSortedSet<IssueWithHistory> getIssuesOriginallyNamed(String name) {
		requireNonNull(name);
		final ImmutableSet<IssueWithHistory> homonyms = allIssuesByOriginalName.get(name);
		/**
		 * Guaranteed by the way we built the ImmutableSetMultimap (except after
		 * de-serialization).
		 */
		assert homonyms instanceof ImmutableSortedSet;
		return (ImmutableSortedSet<IssueWithHistory>) homonyms;
	}

	public User getOwner() {
		return owner;
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		helper.addValue(repository.getName());
		return helper.toString();
	}
}
