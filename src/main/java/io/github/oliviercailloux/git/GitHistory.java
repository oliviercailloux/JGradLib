package io.github.oliviercailloux.git;

import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Graph;

import io.github.oliviercailloux.utils.Utils;

public class GitHistory extends GitGenericHistory<RevCommit> {
	public static GitHistory from(Iterable<RevCommit> commits) {
		return new GitHistory(Utils.asGraph((c) -> ImmutableList.copyOf(c.getParents()), ImmutableSet.copyOf(commits)));
	}

	private GitHistory(Graph<RevCommit> commits) {
		super(commits);
	}
}
