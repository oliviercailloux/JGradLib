package io.github.oliviercailloux.java_grade.ex.chess;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.JGit;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitRepoFileSystem;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.java_grade.ex.GitBrGraderTests;
import io.github.oliviercailloux.java_grade.ex.chess.ChessGrader;
import io.github.oliviercailloux.xml.XmlUtils;

class ChessGraderTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ChessGraderTests.class);

	@Test
	void testDoesNotCompile() throws Exception {
		final Path basePath = Path.of(getClass().getResource("chess empty impl").toURI());
		try (Repository repository = JGit.createRepository(new PersonIdent("Me", "email"), basePath)) {
			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromRepository(repository)) {
				final GitHubHistory fakeGitHubHistory = GitBrGraderTests
						.getMinGitHubHistory(gitFs.getHistory().getGraph());
				final IGrade grade = new ChessGrader().grade("wrongname", gitFs, fakeGitHubHistory);

				LOGGER.debug("Grade does not compile: {}.", grade);

				assertEquals(0d, grade.getPoints());
			}
		}
	}

	@Test
	void testBad() throws Exception {
		final Path basePath = Path.of(getClass().getResource("chess bad impl").toURI());
		try (Repository repository = JGit.createRepository(new PersonIdent("Me", "email"), basePath)) {
			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromRepository(repository)) {
				final GitHubHistory fakeGitHubHistory = GitBrGraderTests
						.getMinGitHubHistory(gitFs.getHistory().getGraph());
				final IGrade grade = new ChessGrader().grade("wrongname", gitFs, fakeGitHubHistory);

				LOGGER.debug("Grade bad: {}.", grade);
				Files.writeString(Path.of("grade.html"), XmlUtils.asString(HtmlGrades.asHtml(grade, "My grade")));

				assertEquals(ChessGrader.COMPILE_POINTS, grade.getPoints(), 0.0001d);
			}
		}
	}

	@Test
	void testGood() throws Exception {
		final Path basePath = Path.of(getClass().getResource("chess full impl").toURI());
		try (Repository repository = JGit.createRepository(new PersonIdent("Me", "email"), basePath)) {
			try (GitRepoFileSystem gitFs = new GitFileSystemProvider().newFileSystemFromRepository(repository)) {
				final GitHubHistory fakeGitHubHistory = GitBrGraderTests
						.getMinGitHubHistory(gitFs.getHistory().getGraph());
				final IGrade grade = new ChessGrader().grade("Me", gitFs, fakeGitHubHistory);

				LOGGER.debug("Grade good: {}.", grade);
				Files.writeString(Path.of("grade.html"), XmlUtils.asString(HtmlGrades.asHtml(grade, "My grade")));

				assertEquals(1d, grade.getPoints());
			}
		}
	}
}
