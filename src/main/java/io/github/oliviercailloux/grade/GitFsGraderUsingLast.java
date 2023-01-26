package io.github.oliviercailloux.grade;

import static com.google.common.base.Verify.verify;

import io.github.oliviercailloux.git.fs.GitHistorySimple;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;

public class GitFsGraderUsingLast<X extends Exception> implements GitFsGrader<X> {
	public static <X extends Exception> GitFsGraderUsingLast<X> using(PathGrader<X> g) {
		return new GitFsGraderUsingLast<>(g);
	}

	private final PathGrader<X> g;

	private GitFsGraderUsingLast(PathGrader<X> g) {
		this.g = g;
	}

	@Override
	public MarksTree grade(GitHistorySimple data) throws X {
		verify(!data.graph().nodes().isEmpty());

		final GitPathRootSha leaf = ByTimeGrader.last(data);
		return g.grade(leaf);
	}

	@Override
	public GradeAggregator getAggregator() {
		return g.getAggregator();
	}
}
