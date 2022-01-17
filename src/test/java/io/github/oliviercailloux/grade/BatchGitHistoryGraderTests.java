package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

public class BatchGitHistoryGraderTests {
	@Test
	void testBatch() throws Exception {
		final BatchGitHistoryGrader<Exception> batchGrader = new BatchGitHistoryGrader<>("prefix", ZonedDateTime.now(),
				this::grader);
		final ImmutableMap<GitHubUsername, IGrade> grades = batchGrader.getAndWriteGrades(Path.of("test grades.json"));

	}

	public IGrade grader(GitFileSystemHistory history) throws Exception {

	}
}
