package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.graph.ImmutableGraph;
import io.github.oliviercailloux.gitjfs.GitDfsFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystemProvider;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.jgit.JGit;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitFilteringFsTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFilteringFsTests.class);

	@Test
	void test() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
			final GitDfsFileSystem fs = GitFileSystemProvider.instance().newFileSystemFromDfsRepository(repo);
			LOGGER.debug("Shas: " + fs.graph().nodes());

			final GitFilteringFs none = GitFilteringFs.filter(fs, c -> c.id().equals(null));
			assertEquals(0, none.graph().nodes().size());
			final GitFilteringFs first = GitFilteringFs.filter(fs, c -> c.id().equals(commits.get(0)));
			assertEquals(1, first.graph().nodes().size());
			assertEquals(first.getPathRoot(commits.get(0)), Iterables.getOnlyElement(first.graph().nodes()));

			assertEquals(3, commits.size());
			final GitFilteringFs middle = GitFilteringFs.filter(fs, c -> !c.id().equals(commits.get(1)));
			final ImmutableGraph<GitPathRootShaCached> graph = middle.graph();
			assertEquals(2, graph.nodes().size());
			final GitPathRootShaCached c0 = middle.getPathRoot(commits.get(0)).toShaCached();
			final GitPathRootShaCached c2 = middle.getPathRoot(commits.get(2)).toShaCached();
			assertEquals(ImmutableSet.of(c0), graph.predecessors(c2));
			assertEquals(ImmutableSet.of(), graph.predecessors(c0));
		}
	}
}
