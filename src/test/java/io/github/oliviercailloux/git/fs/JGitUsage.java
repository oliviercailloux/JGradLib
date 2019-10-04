package io.github.oliviercailloux.git.fs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class JGitUsage {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JGitUsage.class);

	/**
	 * This does not really test code, it illustrates usage of JGit API for possible
	 * future use.
	 */
	@Test
	void testCreateRepoInMemory() throws Exception {
//		final Path gitDir = Path.of("git-bare");
//		Files.createDirectory(gitDir);
//		try (Repository repo = new FileRepository(gitDir.toString())) {
		try (Repository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			createBasicRepo(repo);
		}
	}

	public static void createBasicRepo(Repository repo) throws IOException {
		repo.create(true);
		final ObjectDatabase objectDatabase = repo.getObjectDatabase();
		try (ObjectInserter inserter = objectDatabase.newInserter()) {
			final PersonIdent personIdent = new PersonIdent("Me", "email");
			final ObjectId commitStart = insertCommit(inserter, personIdent, ImmutableMap.of("name", "Hello, world"),
					ImmutableList.of(), "First commit");
			final ObjectId commitNext = insertCommit(inserter, personIdent,
					ImmutableMap.of("name", "Hello, world", "new", "Hello again"), ImmutableList.of(commitStart),
					"Second commit");

			final RefUpdate updateRef = repo.updateRef("refs/heads/master");
			updateRef.setNewObjectId(commitStart);
			final Result updateResult = updateRef.update();
			Verify.verify(updateResult == Result.NEW, updateResult.toString());
		}
	}

	public static ObjectId insertCommit(ObjectInserter inserter, PersonIdent personIdent,
			ImmutableMap<String, String> files, ImmutableList<ObjectId> parents, String commitMessage)
			throws IOException {
		final TreeFormatter treeFormatter = new TreeFormatter();
		for (String fileName : files.keySet()) {
			final String fileContent = files.get(fileName);
			final ObjectId fileOId = inserter.insert(Constants.OBJ_BLOB, fileContent.getBytes(StandardCharsets.UTF_8));
			treeFormatter.append(fileName, FileMode.REGULAR_FILE, fileOId);
		}
		final ObjectId treeId = inserter.insert(treeFormatter);
		final CommitBuilder commitBuilder = new CommitBuilder();
		commitBuilder.setMessage(commitMessage);
		commitBuilder.setAuthor(personIdent);
		commitBuilder.setCommitter(personIdent);
		commitBuilder.setTreeId(treeId);
		for (ObjectId parent : parents) {
			commitBuilder.addParentId(parent);
		}
		final ObjectId commitId = inserter.insert(commitBuilder);
		LOGGER.info("Created commit: {}.", commitId);
		return commitId;
	}

}
