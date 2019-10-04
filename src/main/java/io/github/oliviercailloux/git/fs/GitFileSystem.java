package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.net.URI;
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

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class GitFileSystem extends FileSystem {
	/**
	 * It is crucial to always use the same instance of Jimfs, because Jimfs refuses
	 * to resolve paths coming from different instances.
	 */
	static final FileSystem JIM_FS = Jimfs.newFileSystem(Configuration.unix());
	static final Path JIM_FS_SLASH = JIM_FS.getPath("/");
	static final Path JIM_FS_EMPTY = JIM_FS.getPath("");
	private final GitFileSystemProvider gitProvider;
	private final URI gitFsUri;
	/**
	 * Must be default FS because of limitations of JGit.
	 */
	private final Path workTree;
	private boolean isOpen;

	GitFileSystem(GitFileSystemProvider gitProvider, URI gitFsUri, Path workTree) {
		this.gitProvider = checkNotNull(gitProvider);
		this.gitFsUri = checkNotNull(gitFsUri);
		this.workTree = checkNotNull(workTree);
		checkArgument(workTree.getFileSystem().equals(FileSystems.getDefault()));
		isOpen = true;
	}

	public URI getGitFsUri() {
		return gitFsUri;
	}

	public Path getWorkTree() {
		return workTree;
	}

	@Override
	public FileSystemProvider provider() {
		return gitProvider;
	}

	@Override
	public void close() throws IOException {
		isOpen = false;
		gitProvider.hasBeenClosed(this);
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
	public Iterable<Path> getRootDirectories() {
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

}
