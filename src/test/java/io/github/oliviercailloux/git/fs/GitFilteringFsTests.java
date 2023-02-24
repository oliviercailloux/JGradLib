package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.graph.ImmutableGraph;
import io.github.oliviercailloux.gitjfs.GitDfsFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystemProvider;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.jgit.JGit;
import java.nio.file.NoSuchFileException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
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
	void testGraph() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
			assertEquals(3, commits.size());
			try (GitDfsFileSystem fs = GitFileSystemProvider.instance().newFileSystemFromDfsRepository(repo)) {
				LOGGER.debug("Shas: " + fs.graph().nodes());

				final GitFilteringFs none = GitFilteringFs.filter(fs, c -> c.id().equals(null));
				assertEquals(0, none.graph().nodes().size());
				final GitFilteringFs first = GitFilteringFs.filter(fs, c -> c.id().equals(commits.get(0)));
				assertEquals(1, first.graph().nodes().size());
				assertEquals(first.getPathRoot(commits.get(0)), Iterables.getOnlyElement(first.graph().nodes()));

				final GitFilteringFs middle = GitFilteringFs.filter(fs, c -> !c.id().equals(commits.get(1)));
				final ImmutableGraph<GitPathRootShaCached> graph = middle.graph();
				LOGGER.debug("Middle: {}.", graph);
				assertEquals(2, graph.nodes().size());
				final ImmutableSet<ObjectId> middleIds = graph.nodes().stream().map(p -> p.getCommit().id())
						.collect(ImmutableSet.toImmutableSet());
				assertEquals(ImmutableSet.of(commits.get(0), commits.get(2)), middleIds);
				final GitPathRootShaCached c0 = middle.getPathRoot(commits.get(0)).toShaCached();
				final GitPathRootShaCached c2 = middle.getPathRoot(commits.get(2)).toShaCached();
				assertEquals(ImmutableSet.of(c0), graph.predecessors(c2));
				assertEquals(ImmutableSet.of(), graph.predecessors(c0));
			}
		}
	}

	@Test
	void testDiff() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
			try (GitDfsFileSystem fs = GitFileSystemProvider.instance().newFileSystemFromDfsRepository(repo)) {
				final GitFilteringFs gitFs = GitFilteringFs.filter(fs, c -> !c.id().equals(commits.get(1)));
				final GitPathRootSha p0 = gitFs.getPathRoot(commits.get(0));
				final GitPathRootSha p1 = gitFs.getPathRoot(commits.get(1));
				final GitPathRootSha p2 = gitFs.getPathRoot(commits.get(2));

				assertEquals(ImmutableSet.of(), gitFs.diff(p0, p0));
				{
					assertThrows(NoSuchFileException.class, () -> gitFs.diff(p0, p1));
				}
				{
					assertThrows(NoSuchFileException.class, () -> gitFs.diff(p1, p0));
				}
				{
					assertThrows(NoSuchFileException.class, () -> gitFs.diff(p1, p2));
				}
				{
					final ImmutableSet<DiffEntry> diffs02 = gitFs.diff(p0, p2);
					final UnmodifiableIterator<DiffEntry> iterator = diffs02.iterator();
					final DiffEntry diff02first = iterator.next();
					final DiffEntry diff02second = iterator.next();
					assertFalse(iterator.hasNext());
					assertEquals(ChangeType.ADD, diff02first.getChangeType());
					assertEquals("dir/file.txt", diff02first.getNewPath());
					assertEquals(ChangeType.ADD, diff02second.getChangeType());
					assertEquals("file2.txt", diff02second.getNewPath());
				}
			}
		}
	}
}
