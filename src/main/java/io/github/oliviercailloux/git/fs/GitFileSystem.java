package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Set;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.utils.SeekableInMemoryByteChannel;

public class GitFileSystem extends FileSystem {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystem.class);

	public static GitFileSystem given(GitFileSystemProvider provider, Path gitDir) throws IOException {
		final GitFileSystem fs = new GitFileSystem(provider, gitDir);
		fs.open();
		return fs;
	}

	/**
	 * It is crucial to always use the same instance of Jimfs, because Jimfs refuses
	 * to resolve paths coming from different instances.
	 */
	static final FileSystem JIM_FS = Jimfs.newFileSystem(Configuration.unix());
	static final Path JIM_FS_SLASH = JIM_FS.getPath("/");
	static final Path JIM_FS_EMPTY = JIM_FS.getPath("");
	private final GitFileSystemProvider gitProvider;
	/**
	 * Must be default FS because of limitations of JGit.
	 */
	private final Path gitDir;
	private boolean isOpen;
	private Repository repository;
	private ObjectReader reader;

	private GitFileSystem(GitFileSystemProvider gitProvider, Path gitDir) {
		this.gitProvider = checkNotNull(gitProvider);
		this.gitDir = checkNotNull(gitDir);
		checkArgument(gitDir.getFileSystem().equals(FileSystems.getDefault()));
	}

	private void open() throws IOException {
		repository = new FileRepository(this.gitDir.toFile());
		reader = repository.newObjectReader();
		reader.setAvoidUnreachableObjects(true);
		isOpen = true;
	}

	public Path getGitDir() {
		return gitDir;
	}

	@Override
	public FileSystemProvider provider() {
		return gitProvider;
	}

	@Override
	public void close() throws IOException {
		isOpen = false;
		reader.close();
		repository.close();
		gitProvider.hasBeenClosedEvent(this);
	}

	@Override
	public boolean isOpen() {
		return isOpen;
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public String getSeparator() {
		assert JIM_FS.getSeparator().equals("/");
		return "/";
	}

	@Override
	public ImmutableSortedSet<Path> getRootDirectories() {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}
		throw new UnsupportedOperationException();
	}

	/**
	 * @return absolute paths whose rev strings are sha1 object ids.
	 */
	public ImmutableSortedSet<GitPath> getGitRootDirectories() {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		throw new UnsupportedOperationException();
	}

	@Override
	public GitPath getPath(String first, String... more) {
		/**
		 * Get first arg until // or end of string. First arg may not start with /.
		 *
		 * Build a list of dirAndFile arguments, constituted of the rest of the first
		 * arg if it contains "//" (taking everything after the first / in "//", thus
		 * including a slash), and all others, concatenated then split at "/". If
		 * resulting revStr is not empty, and the list of dirAndFile is not empty, then
		 * dirAndFile must start with "/". If revStr is not empty and dirAndFile is
		 * empty, dirAndFile is considered as slash. If revStr is empty, and the list of
		 * dirAndFile is not empty, then dirAndFile must not start with /. If revStr is
		 * empty, and dirAndFile is empty, dirAndFile is considered as "".
		 */
		checkArgument(!first.startsWith("/"));
		checkArgument(first.isEmpty() || first.contains("//") || first.endsWith("/"));
		final int startDoubleSlash = first.indexOf("//");
		Verify.verify(startDoubleSlash != 0);
		final String revStr;
		if (startDoubleSlash == -1) {
			revStr = first.isEmpty() ? "" : first.substring(0, first.length() - 1);
		} else {
			revStr = first.substring(0, startDoubleSlash);
		}
		Verify.verify(!revStr.startsWith("/"));
		Verify.verify(!revStr.endsWith("/"));
		final String restFirst;
		if (startDoubleSlash == -1) {
			restFirst = "";
		} else {
			restFirst = first.substring(startDoubleSlash + 1, first.length());
			Verify.verify(restFirst.startsWith("/"));
		}

		final ImmutableList<String> dirAndFile = ImmutableList.<String>builder().add(restFirst)
				.addAll(Arrays.asList(more)).build().stream().filter((s) -> !s.isEmpty())
				.collect(ImmutableList.toImmutableList());

		if (!dirAndFile.isEmpty()) {
			checkArgument(revStr.isEmpty() == !dirAndFile.get(0).startsWith("/"));
		}

		final Path effectiveDirAndFile;
		if (dirAndFile.isEmpty()) {
			if (revStr.isEmpty()) {
				effectiveDirAndFile = JIM_FS_EMPTY;
			} else {
				effectiveDirAndFile = JIM_FS_SLASH;
			}
		} else {
			effectiveDirAndFile = JIM_FS.getPath(dirAndFile.get(0),
					dirAndFile.subList(1, dirAndFile.size()).toArray(new String[] {}));
		}

		Verify.verify(!revStr.isEmpty() == effectiveDirAndFile.isAbsolute());

		return new GitPath(this, revStr, effectiveDirAndFile);
	}

	public GitPath getRelativePath(String first, String... more) {
		return getPath("", ImmutableList.<String>builder().add(first).addAll(Arrays.asList(more)).build()
				.toArray(new String[] {}));
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		throw new UnsupportedOperationException();
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchService newWatchService() throws IOException {
		throw new UnsupportedOperationException();
	}

	public SeekableByteChannel newByteChannel(GitPath gitPath)
			throws IOException, MissingObjectException, IncorrectObjectTypeException {
		checkArgument(gitPath.isAbsolute());

		final String revStr = gitPath.getRevStr();
		final Ref ref = repository.findRef(revStr);
		if (ref == null) {
			throw new FileNotFoundException("Rev str " + revStr + " not found.");
		}

		final ObjectId commitId = ref.getLeaf().getObjectId();
		if (commitId == null) {
			throw new FileNotFoundException("Ref " + ref.getName() + " does not exist.");
		}

		final RevTree tree;
		try (RevWalk walk = new RevWalk(reader)) {
			tree = walk.parseCommit(commitId).getTree();
		}

		final ObjectId fileId;
		final GitPath withoutRoot = gitPath.getWithoutRoot();
		try (TreeWalk treeWalk = TreeWalk.forPath(repository, withoutRoot.toString(), tree)) {
			if (treeWalk == null) {
				throw new FileNotFoundException("Path " + withoutRoot + " not found.");
			}
			fileId = treeWalk.getObjectId(0);
			verify(!treeWalk.next());
		}

		final ObjectLoader fileLoader = reader.open(fileId, Constants.OBJ_BLOB);
		final byte[] bytes = fileLoader.getBytes();
		LOGGER.info("Read: {}.", new String(bytes, StandardCharsets.UTF_8));
		return new SeekableInMemoryByteChannel(bytes);
	}

}
