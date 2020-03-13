package io.github.oliviercailloux.java_grade.ex.print_exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.JGit;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitRepoFileSystem;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.java_grade.compiler.SimpleCompiler;
import io.github.oliviercailloux.java_grade.ex.GitBrGraderTests;
import io.github.oliviercailloux.utils.Utils;

class PrintExecGraderTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PrintExecGraderTests.class);

	@Test
	void testCompile() throws Exception {
		final String content = Files
				.readString(Path.of("src/main/java").resolve(PrintExec.class.getName().replace(".", "/") + ".java"));
		final JavaFileObject source = SimpleCompiler.asJavaSource(PrintExec.class.getName(), content);
		final List<Diagnostic<? extends JavaFileObject>> diags = SimpleCompiler.compile(ImmutableList.of(source),
				ImmutableSet.of(), Utils.getTempDirectory().resolve(Instant.now().toString()));
		assertTrue(diags.isEmpty(), diags.toString());
	}

	@Test
	void testPrintExecGood() throws Exception {
		final String content = Files
				.readString(Path.of("src/main/java").resolve(PrintExec.class.getName().replace(".", "/") + ".java"))
				.replaceAll("package .*", "");

		try (Repository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			// final Path wT = Path.of("git-test " + Instant.now());
			// try (Repository repository = new
			// FileRepository(wT.resolve(".git").toString())) {
			repository.create(true);
			final ObjectId commit;
			try (ObjectInserter inserter = repository.getObjectDatabase().newInserter()) {
				commit = JGit.insertCommit(inserter, new PersonIdent("me", "me@dauphine.fr"),
						ImmutableMap.of("PrintExec.java", content), ImmutableList.of(), "Initial commit");
				LOGGER.debug("Committed {}.", commit);
			}
			JGit.setMaster(repository, commit);

			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromRepository(repository)) {
				final GitLocalHistory history = gitFs.getHistory();
				assertEquals(ImmutableSet.of(commit), history.getGraph().nodes());
				final GitHubHistory minGitHubHistory = GitBrGraderTests.getMinGitHubHistory(history.getGraph());
				final IGrade grade = new PrintExecGrader().grade("me", gitFs, minGitHubHistory);
				assertEquals(1d, grade.getPoints(), grade.toString());
			}
		}

	}

	@Test
	void testPrintExecBad() throws Exception {
		final String content = Files
				.readString(Path.of("src/main/java").resolve(PrintExec.class.getName().replace(".", "/") + ".java"))
				.replaceAll("package .*", "").replaceAll("System.out.*", "");

		try (Repository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			// final Path wT = Path.of("git-test " + Instant.now());
			// try (Repository repository = new
			// FileRepository(wT.resolve(".git").toString())) {
			repository.create(true);
			final ObjectId commit;
			try (ObjectInserter inserter = repository.getObjectDatabase().newInserter()) {
				commit = JGit.insertCommit(inserter, new PersonIdent("me", "me@dauphine.fr"),
						ImmutableMap.of("PrintExec.java", content), ImmutableList.of(), "Initial commit");
				LOGGER.debug("Committed {}.", commit);
			}
			JGit.setMaster(repository, commit);

			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromRepository(repository)) {
				final GitLocalHistory history = gitFs.getHistory();
				assertEquals(ImmutableSet.of(commit), history.getGraph().nodes());
				final GitHubHistory minGitHubHistory = GitBrGraderTests.getMinGitHubHistory(history.getGraph());
				final IGrade grade = new PrintExecGrader().grade("me", gitFs, minGitHubHistory);
//				Files.writeString(Path.of("grade.json"),
//						JsonbUtils.toJsonObject(grade, JsonGrade.asAdapter()).toString());
//				final Document doc = HtmlGrade.asHtml(grade, "print exec");
//				Files.writeString(Path.of("grade.html"), XmlUtils.asString(doc));
				assertEquals(1.5d / 20d, grade.getPoints(), 0.0001d, grade.toString());
			}
		}

	}

	@Test
	void testPrintExecDoesNotCompile() throws Exception {
		final String content = Files
				.readString(Path.of("src/main/java").resolve(PrintExec.class.getName().replace(".", "/") + ".java"))
				.replaceAll("package .*", "").replaceAll("System.out.*", "fail_to_compile();");

		try (Repository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			// final Path wT = Path.of("git-test " + Instant.now());
			// try (Repository repository = new
			// FileRepository(wT.resolve(".git").toString())) {
			repository.create(true);
			final ObjectId commit;
			try (ObjectInserter inserter = repository.getObjectDatabase().newInserter()) {
				commit = JGit.insertCommit(inserter, new PersonIdent("me", "me@dauphine.fr"),
						ImmutableMap.of("PrintExec.java", content), ImmutableList.of(), "Initial commit");
				LOGGER.debug("Committed {}.", commit);
			}
			JGit.setMaster(repository, commit);

			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromRepository(repository)) {
				final GitLocalHistory history = gitFs.getHistory();
				assertEquals(ImmutableSet.of(commit), history.getGraph().nodes());
				final GitHubHistory minGitHubHistory = GitBrGraderTests.getMinGitHubHistory(history.getGraph());
				final IGrade grade = new PrintExecGrader().grade("me", gitFs, minGitHubHistory);
				assertEquals(1d / 20d, grade.getPoints(), grade.toString());
			}
		}

	}

}
