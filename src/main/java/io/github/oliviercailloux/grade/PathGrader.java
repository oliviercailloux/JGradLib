package io.github.oliviercailloux.grade;

import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import io.github.oliviercailloux.git.fs.GitPathRootSha;
import java.nio.file.Path;
import java.util.Comparator;

public interface PathGrader<X extends Exception> extends Grader<X> {
	MarksTree gradeProject(Path project) throws X;

	@Override
	default MarksTree grade(GitFileSystemHistory data) throws X {
		verify(!data.getGraph().nodes().isEmpty());

		final ImmutableSet<GitPathRootSha> leaves = data.getLeaves();
		final Comparator<GitPathRootSha> byDate = Comparator
				.comparing((GitPathRootSha r) -> IO_UNCHECKER.getUsing(r::getCommit).getAuthorDate().toInstant());
		final ImmutableSortedSet<GitPathRootSha> sortedLeaves = ImmutableSortedSet.copyOf(byDate, leaves);
		final GitPathRootSha leaf = sortedLeaves.last();
		return gradeProject(leaf);
	}
}
