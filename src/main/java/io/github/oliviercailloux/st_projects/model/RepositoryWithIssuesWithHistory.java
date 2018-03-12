package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSetMultimap.Builder;
import com.google.common.collect.ImmutableSortedSet;

import io.github.oliviercailloux.git_hub_gql.IssueBare;
import io.github.oliviercailloux.git_hub_gql.IssueEvent;
import io.github.oliviercailloux.git_hub_gql.IssueSnapshotQL;
import io.github.oliviercailloux.git_hub_gql.RenamedTitleEvent;
import io.github.oliviercailloux.git_hub_gql.Repository;
import io.github.oliviercailloux.git_hub_gql.User;

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

	public static RepositoryWithIssuesWithHistory from(JsonObject json) {
		return new RepositoryWithIssuesWithHistory(json);
	}

	private final ImmutableSetMultimap<String, IssueWithHistory> allIssuesByName;

	private final ImmutableSetMultimap<String, IssueWithHistory> allIssuesByOriginalName;

	private final ImmutableList<String> files;

	final private ImmutableList<IssueWithHistory> issues;

	private final Repository repository;

	private RepositoryWithIssuesWithHistory(JsonObject json) {
		repository = Repository.from(json);
		final JsonObject issuesJson = json.getJsonObject("issues");
		final JsonArray nodes = issuesJson.getJsonArray("nodes");
		checkArgument(issuesJson.getInt("totalCount") == nodes.size());
		checkArgument(!issuesJson.getJsonObject("pageInfo").getBoolean("hasNextPage"));
		issues = nodes.stream().map(JsonValue::asJsonObject).map(IssueBare::from).map(this::getIssue)
				.collect(ImmutableList.toImmutableList());
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
		{
			files = json.getJsonObject("masterObject").getJsonArray("entries").stream().map(JsonValue::asJsonObject)
					.filter((e) -> e.getString("type").equals("blob")).map((e) -> e.getString("name"))
					.collect(ImmutableList.toImmutableList());
		}

	}

	public Repository getBare() {
		return repository;
	}

	/**
	 * @return the first-level files in this repository.
	 */
	public ImmutableList<String> getFiles() {
		return files;
	}

	public ImmutableList<IssueWithHistory> getIssues() {
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
		final ImmutableSet<IssueWithHistory> homonyms = allIssuesByOriginalName
				.get(name);/**
							 * Guaranteed by the way we built the ImmutableSetMultimap (except after
							 * de-serialization).
							 */
		assert homonyms instanceof ImmutableSortedSet;
		return (ImmutableSortedSet<IssueWithHistory>) homonyms;
	}

	public User getOwner() {
		return repository.getOwner();
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		helper.addValue(repository.getName());
		return helper.toString();
	}

	private IssueWithHistory getIssue(IssueBare issueBare) {
		LOGGER.debug("Taking care of issue: {}.", issueBare);
		final List<IssueEvent> events = issueBare.getEvents();

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

		for (IssueEvent event : events) {
			snap = event.applyTo(snap);
			snaps.add(snap);
		}
		return IssueWithHistory.from(issueBare, snaps);
	}
}
