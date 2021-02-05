package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.GitHubHistory;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.utils.Utils;
import io.github.oliviercailloux.xml.XmlUtils;
import name.falgout.jeffrey.throwing.stream.ThrowingStream;

/**
 * An instance of this should have a deadline and a grader.
 *
 * Then this is a grader given a GitWork: a GitOnlineUsername (renaming of
 * GitHubUsername) and a GitFileSystemHistory.
 *
 * Note that out of the repository coordinates comes the GitWork.
 */
public class GitGeneralGrader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitGeneralGrader.class);

	public static GitGeneralGrader using(RepositoryFetcher repositoryFetcher, DeadlineGrader deadlineGrader) {
		return new GitGeneralGrader(repositoryFetcher.fetch(), deadlineGrader,
				Path.of("grades " + repositoryFetcher.getPrefix() + ".json"));
	}

	private final Set<RepositoryCoordinatesWithPrefix> repositories;
	private boolean excludeCommitsByGitHub;
	private ImmutableSet<String> excludedAuthors;
	private boolean fromDir;
	private final DeadlineGrader deadlineGrader;
	private Path out;

	private GitGeneralGrader(Set<RepositoryCoordinatesWithPrefix> repositories, DeadlineGrader deadlineGrader,
			Path out) {
		this.repositories = checkNotNull(repositories);
		this.excludeCommitsByGitHub = false;
		this.excludedAuthors = ImmutableSet.of();
		this.fromDir = false;
		this.deadlineGrader = checkNotNull(deadlineGrader);
		this.out = checkNotNull(out);
	}

	public Path getOut() {
		return out;
	}

	public void setOut(Path out) {
		this.out = out;
	}

	public GitGeneralGrader setExcludeCommitsByGitHub(boolean excludeCommitsByGitHub) {
		this.excludeCommitsByGitHub = excludeCommitsByGitHub;
		return this;
	}

	public GitGeneralGrader setExcludeCommitsByAuthors(Set<String> excludedAuthors) {
		this.excludedAuthors = ImmutableSet.copyOf(excludedAuthors);
		return this;
	}

	public GitGeneralGrader setFromDir(boolean fromDir) {
		this.fromDir = fromDir;
		return this;
	}

	public void grade() throws IOException {
		final ImmutableMap.Builder<String, IGrade> builder = ImmutableMap.builder();
		for (RepositoryCoordinatesWithPrefix repository : repositories) {
			final String username = repository.getUsername();
			final IGrade grade = grade(repository);
			builder.put(username, grade);
		}
		final ImmutableMap<String, IGrade> grades = builder.build();
		Files.writeString(out, JsonbUtils.toJsonObject(grades, JsonGrade.asAdapter()).toString());
		Files.writeString(Path.of("grades.html"), XmlUtils.asString(HtmlGrades.asHtml(grades, "Grades", 20d)));
		LOGGER.info("Grades: {}.", grades);
	}

	IGrade grade(RepositoryCoordinatesWithPrefix coordinates) throws IOException {
		final Path dir = Utils.getTempDirectory().resolve(coordinates.getRepositoryName());
		try (FileRepository repository = getFileRepo(coordinates, dir)) {
			final GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository);

			final GitHistory pushHistory;
			{
				final GitHubHistory gitHubHistory;
				try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
					gitHubHistory = fetcher.getReversedGitHubHistory(coordinates);
				}
				if (!gitHubHistory.getPatchedPushCommits().nodes().isEmpty()) {
					LOGGER.warn("Patched: {}.", gitHubHistory.getPatchedPushCommits());
				}
				pushHistory = gitHubHistory.getConsistentPushHistory();
				verify(pushHistory.getGraph().equals(Utils.asImmutableGraph(gitFs.getCommitsGraph(),
						IO_UNCHECKER.wrapFunction(r -> r.getCommit().getId()))));
				LOGGER.debug("Push history: {}.", pushHistory);
			}

			final GitFileSystemHistory history = GitFileSystemHistory.create(gitFs, pushHistory);
			final GitWork work = GitWork.given(GitHubUsername.given(coordinates.getUsername()), history);
			return grade(work);
		}
	}

	private FileRepository getFileRepo(RepositoryCoordinatesWithPrefix coordinates, Path dir) throws IOException {
		if (fromDir) {
			return (FileRepository) new FileRepositoryBuilder().setWorkTree(dir.toFile()).build();
		}
		return GitCloner.create().setCheckCommonRefsAgree(false).download(coordinates.asGitUri(), dir);
	}

	IGrade grade(GitWork work) throws IOException {
		final GitFileSystemHistory manual;
		final ImmutableSet<GitPathRoot> excludedByGitHub;
		if (excludeCommitsByGitHub) {
			final ThrowingStream<GitPathRoot, IOException> stream = ThrowingStream
					.of(work.getHistory().getGraph().nodes().stream(), IOException.class);
			manual = work.getHistory().filter(r -> !JavaMarkHelper.committerIsGitHub(r));
			excludedByGitHub = stream.filter(JavaMarkHelper::committerIsGitHub).collect(ImmutableSet.toImmutableSet());
		} else {
			manual = work.getHistory();
			excludedByGitHub = ImmutableSet.of();
		}

		final GitFileSystemHistory filteredHistory = manual
				.filter(r -> !excludedAuthors.contains(r.getCommit().getAuthorName()));
		final IGrade grade = deadlineGrader.grade(GitWork.given(work.getAuthor(), filteredHistory));
		final String added = excludedByGitHub.isEmpty() ? ""
				: " (Ignored commits by GitHub: " + excludedByGitHub.stream()
						.map(r -> r.getStaticCommitId().getName().toString()).collect(Collectors.joining(", "));
		return grade.withComment(grade.getComment() + added);
	}

}
