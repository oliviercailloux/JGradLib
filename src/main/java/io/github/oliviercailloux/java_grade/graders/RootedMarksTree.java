package io.github.oliviercailloux.java_grade.graders;

import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.SubMarksTree;

public record RootedMarksTree(GitPathRootShaCached root, MarksTree tree) {
	public SubMarksTree commented() {
		return SubMarksTree.given(Criterion.given("Using " + root.getCommit().id().getName()), tree);
	}
}