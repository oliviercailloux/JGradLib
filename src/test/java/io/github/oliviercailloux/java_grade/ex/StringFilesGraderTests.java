package io.github.oliviercailloux.java_grade.ex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitRepoFileSystem;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.utils.Utils;
import io.github.oliviercailloux.xml.XmlUtils;

class StringFilesGraderTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(StringFilesGraderTests.class);

	@Test
	void testGood() throws Exception {
		try (GitRepoFileSystem gitFs = new GitFileSystemProvider()
				.newFileSystemFromGitDir(Path.of("../../Samples/string files/.git"))) {
			final GitLocalHistory history = gitFs.getHistory();
			final ImmutableGraph<ObjectId> graphO = Utils.asImmutableGraph(history.getGraph(), o -> o);
			final ImmutableMap<ObjectId, Instant> asMap = history.getCommitDates().entrySet().stream()
					.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
			final GitHubHistory fakeGitHubHistory = GitHubHistory.given(graphO, asMap, asMap);

			final StringFilesGrader grader = new StringFilesGrader();
			final GitPath implPath = gitFs.getAbsolutePath("impl");
			final ObjectId implTop = gitFs.getCommitId(implPath).get();
			grader.deadline = history.getCommitDateById(implTop).plus(Duration.ofSeconds(1));
			final IGrade grade = grader.grade("Olivier Cailloux", gitFs, fakeGitHubHistory);

			Files.writeString(Path.of("grade.html"), XmlUtils.asString(HtmlGrades.asHtml(grade, "My grade")));

			assertEquals(1d, grade.getPoints(), grade.toString());
		}
	}
}
