package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSetMultimap.Builder;
import com.google.common.collect.ImmutableSortedSet;

import io.github.oliviercailloux.git_hub_gql.IssueBareQL;
import io.github.oliviercailloux.git_hub_gql.IssueEventQL;
import io.github.oliviercailloux.git_hub_gql.IssueSnapshotQL;
import io.github.oliviercailloux.git_hub_gql.RenamedTitleEvent;
import io.github.oliviercailloux.git_hub_gql.RepositoryQL;
import io.github.oliviercailloux.git_hub_gql.UserQL;

/**
 *
 * TODO forbid pull requests as issues. Simplify ordering of issue with history:
 * use only the first one created among homonyms, and use only their original
 * name.
 *
 * @author Olivier Cailloux
 *
 */
public class RepositoryWithIssuesWithHistoryQL {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryWithIssuesWithHistoryQL.class);

	public static RepositoryWithIssuesWithHistoryQL from(JsonObject json) {
		return new RepositoryWithIssuesWithHistoryQL(json);
	}

	private final ImmutableSetMultimap<String, IssueWithHistoryQL> allIssuesByName;

	private final ImmutableSetMultimap<String, IssueWithHistoryQL> allIssuesByOriginalName;

	final private List<IssueWithHistoryQL> issues;

	private final RepositoryQL repository;

	private RepositoryWithIssuesWithHistoryQL(JsonObject json) {
		repository = RepositoryQL.from(json);
		final JsonObject issuesJson = json.getJsonObject("issues");
		final JsonArray nodes = issuesJson.getJsonArray("nodes");
		checkArgument(issuesJson.getInt("totalCount") == nodes.size());
		checkArgument(!issuesJson.getJsonObject("pageInfo").getBoolean("hasNextPage"));
		issues = nodes.stream().map(JsonValue::asJsonObject).map(IssueBareQL::from).map(this::getIssue)
				.collect(Collectors.toList());
		{
			final Builder<String, IssueWithHistoryQL> builderByOriginalName = ImmutableSetMultimap
					.<String, IssueWithHistoryQL>builder().orderValuesBy(Comparator.<IssueWithHistoryQL>naturalOrder());
			for (IssueWithHistoryQL issue : issues) {
				builderByOriginalName.put(issue.getOriginalName(), issue);
			}
			allIssuesByOriginalName = builderByOriginalName.build();
		}
		{
			final Builder<String, IssueWithHistoryQL> builderByName = ImmutableSetMultimap
					.<String, IssueWithHistoryQL>builder().orderValuesBy(Comparator.<IssueWithHistoryQL>naturalOrder());
			for (IssueWithHistoryQL issue : issues) {
				final List<String> names = issue.getNames();
				for (String name : names) {
					builderByName.put(name, issue);
				}
			}
			allIssuesByName = builderByName.build();
		}
	}

	public RepositoryQL getBare() {
		return repository;
	}

	public List<IssueWithHistoryQL> getIssues() {
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
	public ImmutableSortedSet<IssueWithHistoryQL> getIssuesNamed(String name) {
		requireNonNull(name);
		final ImmutableSet<IssueWithHistoryQL> homonyms = allIssuesByName.get(name);
		/**
		 * Guaranteed by the way we built the ImmutableSetMultimap (except after
		 * de-serialization).
		 */
		assert homonyms instanceof ImmutableSortedSet;
		return (ImmutableSortedSet<IssueWithHistoryQL>) homonyms;
	}

	/**
	 * Returns all the issues that have that name as original name, ordered by their
	 * date of “first done” (earliest first), with any issues that do not have
	 * “first done” coming last.
	 *
	 * @return may be empty.
	 */
	public ImmutableSortedSet<IssueWithHistoryQL> getIssuesOriginallyNamed(String name) {
		requireNonNull(name);
		final ImmutableSet<IssueWithHistoryQL> homonyms = allIssuesByOriginalName.get(name);
		/**
		 * Guaranteed by the way we built the ImmutableSetMultimap (except after
		 * de-serialization).
		 */
		assert homonyms instanceof ImmutableSortedSet;
		return (ImmutableSortedSet<IssueWithHistoryQL>) homonyms;
	}

	public UserQL getOwner() {
		return repository.getOwner();
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		helper.addValue(repository.getName());
		return helper.toString();
	}

	private IssueWithHistoryQL getIssue(IssueBareQL issueBare) {
		LOGGER.debug("Taking care of issue: {}.", issueBare);
		final List<IssueEventQL> events = issueBare.getEvents();

		boolean open = true;
		final String name;
		{
			final Optional<RenamedTitleEvent> firstRename = events.stream()
					.filter((e) -> e instanceof RenamedTitleEvent).map((e) -> (RenamedTitleEvent) e).findFirst();
			if (firstRename.isPresent()) {
				name = firstRename.get().getPreviousTitle();
			} else {
				final String endName = issueBare.getTitle();
				name = endName;
			}
		}

		final List<IssueSnapshotQL> snaps = new ArrayList<>();
		IssueSnapshotQL snap = IssueSnapshotQL.of(issueBare.getCreatedAt(), name, open, ImmutableSet.of());
		snaps.add(snap);

		for (IssueEventQL event : events) {
			snap = event.applyTo(snap);
			snaps.add(snap);
		}
		return IssueWithHistoryQL.from(issueBare, snaps);
	}
}
