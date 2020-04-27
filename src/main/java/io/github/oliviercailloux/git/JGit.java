package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class JGit {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JGit.class);

	public static ImmutableList<ObjectId> createBasicRepo(Repository repository) throws IOException {
		repository.create(true);
		final ObjectDatabase objectDatabase = repository.getObjectDatabase();

		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix());
				ObjectInserter inserter = objectDatabase.newInserter()) {
			final Path workDir = jimFs.getPath("");
			final PersonIdent personIdent = new PersonIdent("Me", "email");
			final ImmutableList.Builder<ObjectId> builder = ImmutableList.builder();

			Files.writeString(workDir.resolve("file1.txt"), "Hello, world");
			final ObjectId commitStart = insertCommit(inserter, personIdent, workDir, ImmutableList.of(),
					"First commit");
			builder.add(commitStart);

			Files.writeString(workDir.resolve("file2.txt"), "Hello again");
			final ObjectId commitNext = insertCommit(inserter, personIdent, workDir, ImmutableList.of(commitStart),
					"Second commit");
			builder.add(commitNext);

			final ImmutableList<ObjectId> commits = builder.build();

			setMaster(repository, commits.get(commits.size() - 1));

			return commits;
		}
	}

	public static ImmutableList<ObjectId> createRepoWithSubDir(Repository repository) throws IOException {
		repository.create(true);
		final ObjectDatabase objectDatabase = repository.getObjectDatabase();
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix());
				ObjectInserter inserter = objectDatabase.newInserter()) {
			final Path workDir = jimFs.getPath("");
			final PersonIdent personIdent = new PersonIdent("Me", "email");
			final ImmutableList.Builder<ObjectId> builder = ImmutableList.builder();

			Files.writeString(workDir.resolve("file1.txt"), "Hello, world");
			final ObjectId commitStart = insertCommit(inserter, personIdent, workDir, ImmutableList.of(),
					"First commit");
			builder.add(commitStart);

			Files.writeString(workDir.resolve("file2.txt"), "Hello again");
			final ObjectId commitNext = insertCommit(inserter, personIdent, workDir, ImmutableList.of(commitStart),
					"Second commit");
			builder.add(commitNext);

			Files.writeString(workDir.resolve("file2.txt"), "I insist");
			final Path subDirectory = workDir.resolve("dir");
			Files.createDirectory(subDirectory);
			Files.writeString(subDirectory.resolve("file.txt"), "Hello from sub dir");
			final ObjectId commitThird = insertCommit(inserter, personIdent, workDir, ImmutableList.of(commitNext),
					"Third commit");
			builder.add(commitThird);

			final ImmutableList<ObjectId> commits = builder.build();

			setMaster(repository, commits.get(commits.size() - 1));

			return commits;
		}
	}

	public static ObjectId insertCommit(ObjectInserter inserter, PersonIdent personIdent, Path directory,
			List<ObjectId> parents, String commitMessage) throws IOException {
		final ObjectId treeId = insertTree(inserter, directory);
		return insertCommit(inserter, personIdent, treeId, parents, commitMessage);
	}

	public static ObjectId insertCommit(ObjectInserter inserter, PersonIdent personIdent, ObjectId treeId,
			List<ObjectId> parents, String commitMessage) throws IOException {
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

	/**
	 * Inserts a tree containing the content of the given directory.
	 * <p>
	 * Does not flush the inserter.
	 */
	public static ObjectId insertTree(ObjectInserter inserter, Path directory) throws IOException {
		checkArgument(Files.isDirectory(directory));

		/**
		 * TODO TreeFormatter says that the entries must come in the <i>right</i> order;
		 * what’s that?
		 */
		final TreeFormatter treeFormatter = new TreeFormatter();

		/** See TreeFormatter: “This formatter does not process subtrees”. */
		try (Stream<Path> content = Files.list(directory);) {
			for (Path relEntry : (Iterable<Path>) content::iterator) {
				final String entryName = relEntry.getFileName().toString();
				/** Work around Jimfs bug, see https://github.com/google/jimfs/issues/105 . */
				final Path entry = relEntry.toAbsolutePath();
				if (Files.isRegularFile(entry)) {
					final String fileContent = Files.readString(entry);
					final ObjectId fileOId = inserter.insert(Constants.OBJ_BLOB,
							fileContent.getBytes(StandardCharsets.UTF_8));
					treeFormatter.append(entryName, FileMode.REGULAR_FILE, fileOId);
				} else if (Files.isDirectory(entry)) {
					final ObjectId tree = insertTree(inserter, entry);
					treeFormatter.append(entryName, FileMode.TREE, tree);
				} else {
					throw new IllegalArgumentException("Unknown entry: " + entry);
				}
			}
		}

		final ObjectId inserted = inserter.insert(treeFormatter);
		return inserted;
	}

	public static void setMaster(Repository repository, ObjectId newId) throws IOException {
		final RefUpdate updateRef = repository.updateRef("refs/heads/master");
		updateRef.setNewObjectId(newId);
		final Result updateResult = updateRef.update();
		Verify.verify(updateResult == Result.NEW, updateResult.toString());
	}

}
