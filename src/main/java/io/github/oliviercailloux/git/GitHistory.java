package io.github.oliviercailloux.git;

import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class GitHistory extends GitGenericHistory<RevCommit> {
	public static GitHistory from(Iterable<RevCommit> commits) {
		return new GitHistory(commits);
	}

	private GitHistory(Iterable<RevCommit> commits) {
		super((c) -> ImmutableList.copyOf(c.getParents()), ImmutableSet.copyOf(commits));
	}
}
