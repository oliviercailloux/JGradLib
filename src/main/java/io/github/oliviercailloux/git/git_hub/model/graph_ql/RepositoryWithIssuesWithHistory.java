package io.github.oliviercailloux.git.git_hub.model.graph_ql;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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

import io.github.oliviercailloux.st_projects.utils.JsonUtils;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class RepositoryWithIssuesWithHistory {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryWithIssuesWithHistory.class);

	public static RepositoryWithIssuesWithHistory from(JsonObject json) {
		return new RepositoryWithIssuesWithHistory(json);
	}

	private final ImmutableSetMultimap<String, IssueWithHistory> allIssuesByName;

	private final ImmutableSetMultimap<String, IssueWithHistory> allIssuesByOriginalName;

	private final ImmutableList<String> files;

	/**
	 * Not <code>null</code>.
	 */
	final private ImmutableList<IssueWithHistory> issues;

	private final boolean issuesComplete;

	private final Repository repository;

	private RepositoryWithIssuesWithHistory(JsonObject json) {
		LOGGER.debug(JsonUtils.asPrettyString(json));
		repository = Repository.from(json);
		final JsonObject issuesConnection = json.getJsonObject("issues");
		/**
		 * We have to possibly account for incomplete issues list: some repository may
		 * match some search criteria of ours while being a huge repository with many
		 * issues. In such case, it is still useful to create an object of this class
		 * for debug output and inspection of the results.
		 */
		issuesComplete = Utils.isConnectionComplete(issuesConnection);
		issues = Utils.getContent(issuesConnection, true).map(IssueBare::from).map(this::getIssue)
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
			files = json.isNull("masterObject") ? ImmutableList.of()
					: json.getJsonObject("masterObject").getJsonArray("entries").stream().map(JsonValue::asJsonObject)
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
		checkState(issuesComplete);
		return issues;
	}

	/**
	 * Returns all the issues that have that name as original name or a name
	 * corresponding to the same functionality, for example, "function-2"
	 * corresponds to the name "function". The returned set is ordered by alphabetic
	 * order of name. No two issues in the set have the same name. In case of
	 * homonyms, the issue kept for a given name is the one that has the earliest
	 * date of “first done”.
	 *
	 * @return may be empty.
	 */
	public ImmutableSortedSet<IssueWithHistory> getIssuesCorrespondingTo(String name) {
		checkState(issuesComplete);
		final Collector<IssueWithHistory, ?, ImmutableSortedSet<IssueWithHistory>> collector = ImmutableSortedSet
				.toImmutableSortedSet(Comparator.naturalOrder());
		final Map<String, IssueWithHistory> collected = issues.stream()
				.filter((i) -> i.getOriginalName().matches(name + "(-[2-9]+\\d*)?"))
				.collect(Collectors.groupingBy(IssueWithHistory::getOriginalName,
						Collectors.collectingAndThen(collector, (l) -> l.iterator().next())));
		final ImmutableSortedSet<IssueWithHistory> issuesM = collected.values().stream().collect(
				ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(IssueWithHistory::getOriginalName)));
		if (issuesM.isEmpty() || !issuesM.iterator().next().getOriginalName().equals(name)) {
//			LOGGER.info("Oops with {}.", issuesM);
			return ImmutableSortedSet.of();
		}
		return issuesM;
	}

	/**
	 * Returns all the issues that have ever had that name, ordered by their date of
	 * “first done” (earliest first), with any issues that do not have “first done”
	 * coming last.
	 *
	 * @return may be empty.
	 */
	public ImmutableSortedSet<IssueWithHistory> getIssuesNamed(String name) {
		checkState(issuesComplete);
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
		checkState(issuesComplete);
		/**
		 * TODO bug if an issue is named Resources, another ResourcesBlah then renamed
		 * to Resources-2, another Resources-3, then we get Resources and Resources-3
		 * written.
		 *
		 * TODO bug apparently if an issue is called WSCallRank and another is called
		 * WSCall (see XM-GUI).
		 */
		requireNonNull(name);
		final ImmutableSet<IssueWithHistory> homonyms = allIssuesByOriginalName.get(name);
		/**
		 * Guaranteed by the way we built the ImmutableSetMultimap (except after
		 * de-serialization).
		 */
		assert homonyms instanceof ImmutableSortedSet;
		return (ImmutableSortedSet<IssueWithHistory>) homonyms;
	}

	public ImmutableList<IssueWithHistory> getIssuesSample() {
		return issues;
	}

	public User getOwner() {
		return repository.getOwner();
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		helper.addValue(repository.getCoordinates());
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

		final List<IssueSnapshot> snaps = new ArrayList<>();
		IssueSnapshot snap = IssueSnapshot.of(issueBare.getCreatedAt(), name, open, ImmutableSet.of());
		snaps.add(snap);

		for (IssueEvent event : events) {
			snap = event.applyTo(snap);
			snaps.add(snap);
		}
		return IssueWithHistory.from(issueBare, snaps);
	}
}
