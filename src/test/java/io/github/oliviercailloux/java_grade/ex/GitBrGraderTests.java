package io.github.oliviercailloux.java_grade.ex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.graph.Graph;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.utils.Utils;

public class GitBrGraderTests {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitBrGraderTests.class);

	public static <E extends ObjectId> GitHubHistory getMinGitHubHistory(Graph<E> graph) {
		final ImmutableGraph<ObjectId> graphO = Utils.asImmutableGraph(graph, o -> o);
		final Map<ObjectId, Instant> asMap = Maps.asMap(graphO.nodes(), o -> Instant.MIN);
		final GitHubHistory fakeGitHubHistory = GitHubHistory.given(graphO, asMap, asMap);
		return fakeGitHubHistory;
	}

	@Test
	void testGradeBad() throws Exception {
		final GitUri gitUri = GitUri.fromUri(URI.create("https://github.com/oliviercailloux/Assisted-Board-Games.git"));
		try (Repository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			repository.create(true);
			new GitCloner().clone(gitUri, repository);

			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository)) {
				final GitLocalHistory history = gitFs.getHistory();
				final GitHubHistory fakeGitHubHistory = GitBrGraderTests.getMinGitHubHistory(history.getGraph());
				final IGrade grade = new GitBrGrader().grade("me", gitFs, fakeGitHubHistory);
				assertEquals(0d, grade.getPoints(), grade.toString());
			}
		}
	}

	@Test
	void testGradeFull() throws Exception {
		final GitUri gitUri = GitUri.fromUri(URI.create("https://github.com/oliviercailloux/Assisted-Board-Games.git"));
		final Path wT = Utils.getTempUniqueDirectory("git-test");
		new GitCloner().download(gitUri, wT);
		final RevCommit commitA;
		try (Repository repository = new FileRepository(wT.resolve(".git").toString())) {
			try (ObjectInserter inserter = repository.getObjectDatabase().newInserter()) {
				try (Git git = Git.wrap(repository)) {
					final Ref br1 = git.branchCreate().setName("br1").setStartPoint(GitBrGrader.START.getName()).call();
					final Ref newBr1 = git.checkout().setName(br1.getName()).call();
					assertEquals(GitBrGrader.START, br1.getObjectId());
					assertEquals(GitBrGrader.START, newBr1.getObjectId());

					Files.writeString(wT.resolve("hello.txt"), "first try");
					git.add().addFilepattern("hello.txt").call();
					commitA = git.commit().setCommitter("teststudent", "student@dauphine.fr").setMessage("commit A")
							.call();

					final Ref br2 = git.branchCreate().setName("br2").setStartPoint(GitBrGrader.START.getName()).call();
					git.checkout().setName(br2.getName()).call();

					Files.writeString(wT.resolve("hello.txt"), "second try");
					git.add().addFilepattern("hello.txt").call();
					git.commit().setCommitter("teststudent", "student@dauphine.fr").setMessage("commit B").call();

					Files.writeString(wT.resolve("supplements.txt"), "Hello, world");
					git.add().addFilepattern("supplements.txt").call();
					git.commit().setCommitter("teststudent", "student@dauphine.fr").setMessage("commit C").call();

					final Ref br3 = git.branchCreate().setName("br3").call();
					git.checkout().setName(br3.getName()).call();

					final MergeResult result = git.merge().include(commitA).call();
					assertEquals(MergeResult.MergeStatus.CONFLICTING, result.getMergeStatus());
					assertEquals(ImmutableSet.of("hello.txt"), result.getConflicts().keySet());

					Files.writeString(wT.resolve("hello.txt"), "first try\nsecond try");
					git.add().addFilepattern("hello.txt").call();
					git.commit().setCommitter("teststudent", "student@dauphine.fr").call();
				}
			}
		}
		try (Repository repository = new FileRepository(wT.resolve(".git").toString())) {
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository)) {
				final ObjectId br1 = gitFs.getAbsolutePath("br1").getRoot().getCommit().getId();
				assertEquals(commitA, br1);

				final GitBrGrader grader = new GitBrGrader();
				grader.branchPrefix = "heads";
				final GitHubHistory fakeGitHubHistory = GitBrGraderTests
						.getMinGitHubHistory(gitFs.getHistory().getGraph());
				final IGrade grade = grader.grade("teststudent", gitFs, fakeGitHubHistory);
				Files.writeString(Path.of("grade.json"),
						JsonbUtils.toJsonObject(grade, JsonGrade.asAdapter()).toString());
				assertEquals(1d, grade.getPoints(), grade.toString());
			}
		}
	}

}
