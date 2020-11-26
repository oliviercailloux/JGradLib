package io.github.oliviercailloux.java_grade.ex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.JGit;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.xml.XmlUtils;

class ScoreGraderTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ScoreGraderTests.class);

	@Test
	void testDoesNotCompile() throws Exception {
		final Path basePath = Path.of(getClass().getResource("score").toURI()).resolve("empty impl");
		try (Repository repository = JGit.createRepository(new PersonIdent("Me", "email"), basePath)) {
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository)) {
				final GitHubHistory fakeGitHubHistory = GitBrGraderTests
						.getMinGitHubHistory(gitFs.getHistory().getGraph());
				final IGrade grade = new ScoreGrader().grade("wrongname", gitFs, fakeGitHubHistory);
				assertEquals(0d, grade.getPoints());
			}
		}
	}

	@Test
	void testBad() throws Exception {
		final Path basePath = Path.of(getClass().getResource("score").toURI()).resolve("bad impl");
		try (Repository repository = JGit.createRepository(new PersonIdent("Me", "email"), basePath)) {
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository)) {
				final GitHubHistory fakeGitHubHistory = GitBrGraderTests
						.getMinGitHubHistory(gitFs.getHistory().getGraph());
				final IGrade grade = new ScoreGrader().grade("wrongname", gitFs, fakeGitHubHistory);
				assertEquals(ScoreGrader.COMPILE_POINTS, grade.getPoints(), 0.0001d);
			}
		}
	}

	@Test
	void testGood() throws Exception {
		final Path basePath = Path.of(getClass().getResource("score").toURI()).resolve("full impl");
		try (Repository repository = JGit.createRepository(new PersonIdent("Me", "email"), basePath)) {
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository)) {
				final GitHubHistory fakeGitHubHistory = GitBrGraderTests
						.getMinGitHubHistory(gitFs.getHistory().getGraph());
				final IGrade grade = new ScoreGrader().grade("Me", gitFs, fakeGitHubHistory);

				Files.writeString(Path.of("grade.html"), XmlUtils.asString(HtmlGrades.asHtml(grade, "My grade")));

				assertEquals(1d, grade.getPoints());
			}
		}
	}
}
