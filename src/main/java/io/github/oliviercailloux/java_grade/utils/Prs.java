package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.IssueWithHistory;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Prs {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Prs.class);

	public static void main(String[] args) throws Exception {
		final ImmutableSet<RepositoryCoordinates> allCoordinates;
		{
			final ImmutableSet.Builder<RepositoryCoordinates> builderCoordinates = ImmutableSet.builder();
			builderCoordinates.add(RepositoryCoordinates.from("oliviercailloux-org", "projet-2d-library"));
			builderCoordinates.add(RepositoryCoordinates.from("oliviercailloux-org", "projet-apartments-1"));
			builderCoordinates.add(RepositoryCoordinates.from("oliviercailloux-org", "projet-assisted-board-games-1"));
			builderCoordinates.add(RepositoryCoordinates.from("oliviercailloux-org", "projet-j-confs"));
			builderCoordinates.add(RepositoryCoordinates.from("oliviercailloux-org", "projet-mido-svg"));
			allCoordinates = builderCoordinates.build();
		}

		final ImmutableSet<RepositoryWithIssuesWithHistory> repositories;
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			final ImmutableSet.Builder<RepositoryWithIssuesWithHistory> builderRepositories = ImmutableSet.builder();
			for (RepositoryCoordinates coordinates : allCoordinates) {
				final RepositoryWithIssuesWithHistory repository = fetcher.getRepositoryWithPRs(coordinates)
						.orElseThrow();
				builderRepositories.add(repository);
				LOGGER.debug("Prs: {}.", repository.getIssues().stream().map(IssueWithHistory::getNames)
						.map(l -> l.get(l.size() - 1)).collect(ImmutableList.toImmutableList()));
			}
			repositories = builderRepositories.build();
		}

		final String csv = export(repositories);
		Files.writeString(Path.of("Prs.csv"), csv);
	}

	private static String export(Collection<RepositoryWithIssuesWithHistory> repositories) {
		final StringWriter stringWriter = new StringWriter();
		final CsvWriter writer = new CsvWriter(stringWriter, new CsvWriterSettings());
		writer.writeHeaders("Repository", "Name", "Milestone", "Url", "Assignee 1", "Assignee 2", "Assignees rest");
		for (RepositoryWithIssuesWithHistory repository : repositories) {
			for (IssueWithHistory pr : repository.getIssues()) {
				final List<String> names = pr.getNames();
				verify(!names.isEmpty());
				writer.addValue("Repository", repository.getBare().getName());
				writer.addValue("Name", names.get(names.size() - 1));
				writer.addValue("Milestone", pr.getBare().getMilestone().orElse(""));
				writer.addValue("Url", pr.getBare().getHtmlURI());
				final ImmutableList<GitHubUsername> assignees = pr.getBare().getAssignees();
				final ImmutableList<String> assigneesString = assignees.stream().map(GitHubUsername::getUsername)
						.collect(ImmutableList.toImmutableList());
				writer.addValue("Assignee 1", assigneesString.size() >= 1 ? assigneesString.get(0) : "");
				writer.addValue("Assignee 2", assigneesString.size() >= 2 ? assigneesString.get(1) : "");
				final ImmutableList<String> rest = assigneesString.size() <= 1 ? ImmutableList.of()
						: assigneesString.subList(2, assigneesString.size());
				writer.addValue("Assignees rest", rest.isEmpty() ? "" : rest.toString());
				writer.writeValuesToRow();
			}
		}
		return stringWriter.toString();
	}
}
