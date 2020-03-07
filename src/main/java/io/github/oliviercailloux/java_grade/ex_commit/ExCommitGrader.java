package io.github.oliviercailloux.java_grade.ex_commit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Graph;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitRepoFileSystem;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.java_grade.GraderOrchestrator;
import io.github.oliviercailloux.java_grade.JavaCriterion;
import io.github.oliviercailloux.java_grade.testers.MarkHelper;
import io.github.oliviercailloux.java_grade.utils.HarvestIds;
import io.github.oliviercailloux.java_grade.utils.Summarize;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.supann.QueriesHelper;
import io.github.oliviercailloux.supann.SupannQuerier;
import io.github.oliviercailloux.utils.Utils;

public class ExCommitGrader {
	public static enum ExCommitCriterion implements Criterion {
		ONE_COMMIT, ID_FILE_EXISTS, ID_FILE_CONTENTS;

		@Override
		public String getName() {
			return toString();
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExCommitGrader.class);

	private static final String PREFIX = "commit";

	private static final Instant DEADLINE = ZonedDateTime.parse("2020-02-27T14:06:00+01:00").toInstant();

	private static final Path WORK_DIR = Paths.get("../../Java L3/");

	public static void main(String[] args) throws Exception {
		QueriesHelper.setDefaultAuthenticator();

		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", PREFIX);
		}
//		repositories = ImmutableList
//				.of(RepositoryCoordinatesWithPrefix.from("oliviercailloux-org", PREFIX, ""));

		final ExCommitGrader grader = new ExCommitGrader();
		final ImmutableMap<String, IGrade> grades = repositories.stream().collect(ImmutableMap
				.toImmutableMap(RepositoryCoordinatesWithPrefix::getUsername, Utils.uncheck(r -> grader.grade(r))));

		final Path outDir = Path.of("");
		Files.writeString(outDir.resolve("all grades " + PREFIX + ".json"),
				JsonbUtils.toJsonObject(grades, JsonGrade.asAdapter()).toString());

		Summarize.summarize(PREFIX, outDir);
	}

	private final SupannQuerier supannQuerier;

	ExCommitGrader() {
		supannQuerier = new SupannQuerier();
		// Nothing.
	}

	public IGrade grade(RepositoryCoordinatesWithPrefix coord) throws IOException, GitAPIException {
		final Path projectsBaseDir = WORK_DIR.resolve(PREFIX);
		final Path projectDir = projectsBaseDir.resolve(coord.getRepositoryName());
		new GitCloner().download(GitUri.fromGitUri(coord.asURI()), projectDir);

		try (GitRepoFileSystem fs = new GitFileSystemProvider().newFileSystemFromGitDir(projectDir.resolve(".git"))) {
			final GitHubHistory gitHubHistory = GraderOrchestrator.getGitHubHistory(coord);
			final GitLocalHistory filtered = GraderOrchestrator.getFilteredHistory(fs, gitHubHistory, DEADLINE);
			final IGrade grade = grade(coord.getUsername(), fs, filtered);
			LOGGER.info("Grade {}: {}.", coord, grade);
			return grade;
		}
	}

	public IGrade grade(String owner, GitRepoFileSystem fs, GitLocalHistory history) throws IOException {
		fs.getHistory();
		LOGGER.debug("Graph history: {}.", history.getGraph());
		final GitLocalHistory manual = history
				.filter(o -> !MarkHelper.committerIsGitHub(fs.getCachedHistory().getCommit(o)));
		LOGGER.debug("Graph manual: {}.", manual.getGraph().edges());
		final Graph<ObjectId> graph = Utils.asGraph(n -> manual.getGraph().successors(manual.getCommit(n)),
				ImmutableSet.copyOf(manual.getTips()));
		LOGGER.debug("Graph copied from manual: {}.", graph.edges());

		if (graph.nodes().isEmpty()) {
			return Mark.zero("Found no manual commit (not counting commits from GitHub).");
		}

		final Set<RevCommit> unfiltered = fs.getHistory().getGraph().nodes();
		@SuppressWarnings("unlikely-arg-type")
		final boolean containsAll = unfiltered.containsAll(graph.nodes());
		Verify.verify(containsAll);
		if (!unfiltered.equals(graph.nodes())) {
			LOGGER.warn("Risk of accessing a commit beyond deadline (e.g. master!).");
		}

		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();

		gradeBuilder.put(JavaCriterion.COMMIT, Mark.one());

		final ImmutableList<ObjectId> ownWithCorrectIdentity = graph.nodes().stream()
				.filter(o -> MarkHelper.committerAndAuthorIs(fs.getCachedHistory().getCommit(o), owner))
				.collect(ImmutableList.toImmutableList());
		gradeBuilder.put(JavaCriterion.ID, Mark.binary(ownWithCorrectIdentity.size() >= 1));

		gradeBuilder.put(ExCommitCriterion.ONE_COMMIT, Mark.binary(graph.nodes().size() == 1));

		final GitPath idPath = fs.getRelativePath("id.txt");

		gradeBuilder.put(ExCommitCriterion.ID_FILE_EXISTS, Mark.binary(Files.exists(idPath)));

		final Optional<Integer> id = HarvestIds.getId(fs);
		final boolean foundStudent = id
				.map(Utils.uncheck(i -> supannQuerier.getStudents("id = '" + i + "'").size() == 1)).orElse(false);
		gradeBuilder.put(ExCommitCriterion.ID_FILE_CONTENTS, Mark.binary(foundStudent, "", "Id found: " + id));

		final ImmutableMap<Criterion, IGrade> subGrades = gradeBuilder.build();
		return WeightingGrade.from(subGrades,
				ImmutableMap.of(JavaCriterion.COMMIT, 2d, JavaCriterion.ID, 2d, ExCommitCriterion.ONE_COMMIT, 2d,
						ExCommitCriterion.ID_FILE_EXISTS, 3d, ExCommitCriterion.ID_FILE_CONTENTS, 1d));
	}

}
