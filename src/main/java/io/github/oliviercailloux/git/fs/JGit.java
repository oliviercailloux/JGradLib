package io.github.oliviercailloux.git.fs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class JGit {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JGit.class);

	public static ImmutableList<ObjectId> createBasicRepo(Repository repo) throws IOException {
		repo.create(true);
		final ObjectDatabase objectDatabase = repo.getObjectDatabase();
		try (ObjectInserter inserter = objectDatabase.newInserter()) {
			final PersonIdent personIdent = new PersonIdent("Me", "email");
			final ImmutableList.Builder<ObjectId> builder = ImmutableList.builder();
			final ObjectId commitStart = insertCommit(inserter, personIdent,
					ImmutableMap.of("file1.txt", "Hello, world"), ImmutableList.of(), "First commit");
			builder.add(commitStart);
			final ObjectId commitNext = insertCommit(inserter, personIdent,
					ImmutableMap.of("file1.txt", "Hello, world", "file2.txt", "Hello again"),
					ImmutableList.of(commitStart), "Second commit");
			builder.add(commitNext);

			final ImmutableList<ObjectId> commits = builder.build();

			final RefUpdate updateRef = repo.updateRef("refs/heads/master");
			updateRef.setNewObjectId(commits.get(commits.size() - 1));
			final Result updateResult = updateRef.update();
			Verify.verify(updateResult == Result.NEW, updateResult.toString());

			return commits;
		}
	}

	public static ImmutableList<ObjectId> createRepoWithSubDir(Repository repo) throws IOException {
		repo.create(true);
		final ObjectDatabase objectDatabase = repo.getObjectDatabase();
		try (ObjectInserter inserter = objectDatabase.newInserter()) {
			final PersonIdent personIdent = new PersonIdent("Me", "email");
			final ImmutableList.Builder<ObjectId> builder = ImmutableList.builder();
			final ObjectId commitStart = insertCommit(inserter, personIdent,
					ImmutableMap.of("file1.txt", "Hello, world"), ImmutableList.of(), "First commit");
			builder.add(commitStart);
			final ObjectId commitNext = insertCommit(inserter, personIdent,
					ImmutableMap.of("file1.txt", "Hello, world", "file2.txt", "Hello again"),
					ImmutableList.of(commitStart), "Second commit");
			builder.add(commitNext);
			final ObjectId commitThird = insertCommit(inserter, personIdent,
					ImmutableMap.of("file1.txt", "Hello, world", "dir/file.txt", "Hello from sub dir"),
					ImmutableList.of(commitNext), "Third commit");
			builder.add(commitThird);

			final ImmutableList<ObjectId> commits = builder.build();

			final RefUpdate updateRef = repo.updateRef("refs/heads/master");
			updateRef.setNewObjectId(commits.get(commits.size() - 1));
			final Result updateResult = updateRef.update();
			Verify.verify(updateResult == Result.NEW, updateResult.toString());

			return commits;
		}
	}

	public static ObjectId insertCommit(ObjectInserter inserter, PersonIdent personIdent, Map<String, String> files,
			List<ObjectId> parents, String commitMessage) throws IOException {
		final ObjectId treeId = insertTree(inserter, files);
		final CommitBuilder commitBuilder = new CommitBuilder();
		commitBuilder.setMessage(commitMessage);
		commitBuilder.setAuthor(personIdent);
		commitBuilder.setCommitter(personIdent);
		commitBuilder.setTreeId(treeId);
		for (ObjectId parent : parents) {
			commitBuilder.addParentId(parent);
		}
		final ObjectId commitId = inserter.insert(commitBuilder);
		inserter.flush();
		LOGGER.info("Created commit: {}.", commitId);
		return commitId;
	}

	public static ObjectId insertTree(ObjectInserter inserter, Map<String, String> files) throws IOException {
		final TreeFormatter treeFormatter = new TreeFormatter();
		for (String fileName : files.keySet()) {
			/**
			 * This works even when the file name contains / (creates the required
			 * sub-directories).
			 */
			final String fileContent = files.get(fileName);
			final ObjectId fileOId = inserter.insert(Constants.OBJ_BLOB, fileContent.getBytes(StandardCharsets.UTF_8));
			treeFormatter.append(fileName, FileMode.REGULAR_FILE, fileOId);
		}
		return inserter.insert(treeFormatter);
	}

}
