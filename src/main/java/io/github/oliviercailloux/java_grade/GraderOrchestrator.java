package io.github.oliviercailloux.java_grade;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.fs.GitRepoFileSystem;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.mycourse.json.StudentsReaderFromJson;

public class GraderOrchestrator {

	public static GitHubHistory getGitHubHistory(RepositoryCoordinates coord) {
		final GitHubHistory gitHubHistory;
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			gitHubHistory = fetcher.getGitHubHistory(coord);
		}
		final ImmutableGraph<ObjectId> patched = gitHubHistory.getPatchedKnowns();
		if (!patched.nodes().isEmpty()) {
			LOGGER.warn("Patched: {}.", patched);
		}
		return gitHubHistory;
	}

	public static GitLocalHistory getFilteredHistory(GitRepoFileSystem fs, GitHubHistory gitHubHistory,
			Instant deadline) throws IOException {
		final ImmutableSortedSet<Instant> pushedDates = gitHubHistory.getRefsBySortedPushedDates(true).keySet();
		final Optional<Instant> lastOnTimeOpt = Optional.ofNullable(pushedDates.floor(deadline));
		final Instant lastOnTime = lastOnTimeOpt.orElse(Instant.MIN);
		LOGGER.debug("Last on time: {}.", lastOnTime);
		final GitLocalHistory filtered = fs.getHistory()
				.filter(o -> !gitHubHistory.getCorrectedAndCompletedPushedDates().get(o).isAfter(lastOnTime));
		return filtered;
	}

	public static ImmutableList<RepositoryCoordinates> readRepositories(String org, String prefix) {
		final ImmutableList<RepositoryCoordinates> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositories(org);
		}
		final Pattern pattern = Pattern.compile(prefix + "-(.*)");
		return repositories.stream().filter((r) -> pattern.matcher(r.getRepositoryName()).matches())
				.collect(ImmutableList.toImmutableList());
	}

	public GraderOrchestrator(String prefix) {
		this.prefix = prefix;
		usernames = new StudentsReaderFromJson();
		repositoriesByStudent = null;
	}

	public void setSingleRepo(String studentGitHubUsername) {
		final RepositoryCoordinates aRepo = RepositoryCoordinates.from("oliviercailloux-org",
				prefix + "-" + studentGitHubUsername);
		repositoriesByStudent = ImmutableMap.of(usernames.getStudentOnGitHub(studentGitHubUsername), aRepo);
	}

	public void readRepositories() {
		final ImmutableList<RepositoryCoordinates> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositories("oliviercailloux-org");
		}
		final Pattern pattern = Pattern.compile(prefix + "-(.*)");
		ImmutableMap.Builder<StudentOnGitHub, RepositoryCoordinates> repoBuilder = ImmutableMap.builder();
		for (RepositoryCoordinates repo : repositories) {
			final Matcher matcher = pattern.matcher(repo.getRepositoryName());
			final boolean matches = matcher.matches();
			if (!matches) {
				continue;
			}
			final String gitHubUsername = matcher.group(1);
			repoBuilder.put(usernames.getStudentOnGitHub(gitHubUsername), repo);
		}
		repositoriesByStudent = repoBuilder.build();
		LOGGER.info("Repos: {}.", repositoriesByStudent);
	}

	/**
	 * The students must all be known.
	 */
	public ImmutableMap<StudentOnGitHubKnown, RepositoryCoordinates> getRepositoriesByStudentKnown() {
		return repositoriesByStudent.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap((e) -> e.getKey().asStudentOnGitHubKnown(), Map.Entry::getValue));
	}

	public void readUsernames(Path path) throws IOException {
		try (InputStream inputStream = Files.newInputStream(path)) {
			usernames.read(inputStream);
		}
	}

	private final StudentsReaderFromJson usernames;

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GraderOrchestrator.class);

	private ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositoriesByStudent;

	private final String prefix;

	public ImmutableMap<StudentOnGitHub, RepositoryCoordinates> getRepositoriesByStudent() {
		return repositoriesByStudent;
	}

	public StudentsReaderFromJson getUsernames() {
		return usernames;
	}

}