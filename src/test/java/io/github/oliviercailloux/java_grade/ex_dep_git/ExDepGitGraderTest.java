package io.github.oliviercailloux.java_grade.ex_dep_git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitRepoFileSystem;
import io.github.oliviercailloux.grade.IGrade;

class ExDepGitGraderTest {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExDepGitGraderTest.class);

	@Test
	void testGradeBad() throws Exception {
		final GitUri gitUri = GitUri
				.fromGitUri(URI.create("https://github.com/oliviercailloux/google-or-tools-java.git"));
		try (Repository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			repository.create();
			new GitCloner().clone(gitUri, repository);

			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromRepository(repository)) {
				final IGrade grade = new ExDepGitGraderSimpler().grade("me", gitFs, gitFs.getHistory());
				assertEquals(0d, grade.getPoints());
			}
		}
	}

	@Test
	void testGradeFull() throws Exception {
		final GitUri gitUri = GitUri
				.fromGitUri(URI.create("https://github.com/oliviercailloux/google-or-tools-java.git"));
		/**
		 * Too hard to do with the plumbing API: need to commit to change an existing
		 * non-trivial commit; then merge it with another non-trivial commit. I think
		 * none of this is supported by the plumbing API, and it certainly looks
		 * complicated to implement (see CommitCommand#createTemporaryIndex).
		 */
		final Path wT = Path.of("git-test " + Instant.now());
		new GitCloner().download(gitUri, wT);
		try (Repository repository = new FileRepository(wT.resolve(".git").toString())) {

			try (ObjectInserter inserter = repository.getObjectDatabase().newInserter()) {
				try (Git git = Git.wrap(repository)) {
					final Ref myBranch = git.branchCreate().setName("my-branch")
							.setStartPoint(ExDepGitGraderSimpler.COMMIT_STARTING.getName()).call();

					git.checkout().setName(myBranch.getName()).call();

					Files.copy(Path.of(getClass().getResource("pom.xml").toURI()), wT.resolve("pom.xml"),
							StandardCopyOption.REPLACE_EXISTING);
					git.add().addFilepattern("pom.xml").call();
					git.commit().setCommitter("teststudent", "student@dauphine.fr").setMessage("Pom").call();

					final MergeResult result = git.merge().setCommit(false)
							.include(ExDepGitGraderSimpler.COMMIT_FOLLOWING).call();
					assertEquals(MergeResult.MergeStatus.MERGED_NOT_COMMITTED, result.getMergeStatus(),
							String.valueOf(result.getFailingPaths()));
					git.commit().setCommitter("teststudent", "student@dauphine.fr").call();
				}
			}
		}
		try (Repository repository = new FileRepository(wT.resolve(".git").toString())) {
			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromRepository(repository)) {
				final IGrade grade = new ExDepGitGraderSimpler().grade("teststudent", gitFs, gitFs.getHistory());
//				Files.writeString(Path.of("grade.json"),
//						JsonbUtils.toJsonObject(grade, JsonGrade.asAdapter()).toString());
				assertEquals(1d, grade.getPoints());
			}
		}
	}

}
