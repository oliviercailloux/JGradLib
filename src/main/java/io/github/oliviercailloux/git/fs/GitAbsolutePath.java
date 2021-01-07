package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.fs.GitFileSystem.FollowLinksBehavior;
import io.github.oliviercailloux.git.fs.GitFileSystem.GitObject;
import io.github.oliviercailloux.git.fs.GitFileSystem.NoContextAbsoluteLinkException;
import io.github.oliviercailloux.utils.SeekableInMemoryByteChannel;

/**
 * A git path with a root component and a (possibly empty) sequence of non-empty
 * names.
 */
abstract class GitAbsolutePath extends GitPath {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitAbsolutePath.GitBasicFileAttributes.class);

	private static class DirectoryChannel implements SeekableByteChannel {
		private boolean open;

		public DirectoryChannel() {
			open = true;
		}

		@Override
		public void close() throws IOException {
			open = false;
		}

		@Override
		public boolean isOpen() {
			return open;
		}

		@Override
		public long position() throws IOException {
			return 0;
		}

		@Override
		public SeekableByteChannel position(long newPosition) throws IOException {
			throw new IOException("is a folder");
		}

		@Override
		public int read(ByteBuffer dst) throws IOException {
			throw new IOException("is a folder");
		}

		@Override
		public long size() throws IOException {
			throw new IOException("is a folder");
		}

		@Override
		public SeekableByteChannel truncate(long size) throws IOException {
			throw new IOException("is a folder");
		}

		@Override
		public int write(ByteBuffer src) throws IOException {
			throw new IOException("is a folder");
		}

	}

	private static class GitBasicFileAttributes implements BasicFileAttributes {
		private final ObjectId objectId;
		private final FileMode fileMode;
		private final long size;

		public GitBasicFileAttributes(GitObject gitObject, long size) {
			this.objectId = checkNotNull(gitObject.getObjectId());
			this.fileMode = gitObject.getFileMode();
			checkArgument(!fileMode.equals(FileMode.TYPE_GITLINK));
			checkArgument(!fileMode.equals(FileMode.TYPE_MASK));
			checkArgument(!fileMode.equals(FileMode.TYPE_MISSING));
			this.size = size;
		}

		@Override
		public FileTime creationTime() {
			return FileTime.fromMillis(0);
		}

		@Override
		public ObjectId fileKey() {
			return objectId;
		}

		@Override
		public boolean isDirectory() {
			return fileMode == null || Objects.equals(fileMode, FileMode.TREE);
		}

		@Override
		public boolean isOther() {
			return false;
		}

		@Override
		public boolean isRegularFile() {
			return Objects.equals(fileMode, FileMode.REGULAR_FILE)
					|| Objects.equals(fileMode, FileMode.EXECUTABLE_FILE);
		}

		@Override
		public boolean isSymbolicLink() {
			return Objects.equals(fileMode, FileMode.SYMLINK);
		}

		@Override
		public FileTime lastAccessTime() {
			return FileTime.fromMillis(0);
		}

		@Override
		public FileTime lastModifiedTime() {
			return FileTime.fromMillis(0);
		}

		@Override
		public long size() {
			return size;
		}

	}

	/**
	 * @param names may contain empty strings but must contain some non-empty
	 *              strings; first non-empty string must start with a slash
	 */
	static GitAbsolutePath givenRoot(GitPathRoot root, List<String> names) {
		checkNotNull(root);
		checkArgument(!names.isEmpty());
		final String first = names.isEmpty() ? "" : names.get(0);
		final String[] more = names.isEmpty() ? new String[] {}
				: names.subList(1, names.size()).toArray(new String[] {});
		final Path internal = GitFileSystem.JIM_FS.getPath(first, more);
		checkArgument(internal.isAbsolute());
//		final String longName = names.stream().collect(Collectors.joining("/"));
//		final Path internal = GitFileSystem.JIM_FS_SLASH.resolve(longName);
		verify(!internal.toString().contains("//"));
		return givenRoot(root, internal);
	}

	static GitAbsolutePath givenRoot(GitPathRoot root, Path internalPath) {
		checkNotNull(root);
		checkArgument(internalPath.isAbsolute());

		final GitAbsolutePath absolute;
		if (internalPath.getNameCount() == 0) {
			verify(internalPath.toString().equals("/"));
			absolute = root;
		} else {
			absolute = new GitAbsolutePathWithInternal(root, internalPath);
		}

		return absolute;
	}

	protected GitAbsolutePath() {
	}

	@Override
	public boolean isAbsolute() {
		return true;
	}

	/**
	 * If this path refers to a directory, returns a tree (which is the commit tree
	 * iff this path is solely a root component). If this path refers to a file,
	 * returns a blob. TODO what about links?
	 *
	 * @return guaranteed to exist
	 */
	abstract GitObject getGitObject(FollowLinksBehavior behavior) throws NoSuchFileException, IOException;

	/**
	 * Returns a rev tree iff this path refers to a directory, and the commit tree
	 * iff this path is solely a root component. TODO what about links?
	 *
	 * @throws NoSuchFileException
	 * @throws NotDirectoryException
	 * @throws IOException
	 */
	abstract RevTree getRevTree(boolean followLinks) throws NoSuchFileException, NotDirectoryException, IOException;

	SeekableByteChannel newByteChannel(boolean followLinks) throws NoSuchFileException, IOException {
		final GitObject gitObject = getGitObject(
				followLinks ? FollowLinksBehavior.FOLLOW_ALL_LINKS : FollowLinksBehavior.DO_NOT_FOLLOW_LINKS);
		if (gitObject.getFileMode().equals(FileMode.TYPE_TREE)) {
			return new DirectoryChannel();
		}
		if (!gitObject.getFileMode().equals(FileMode.TYPE_FILE)) {
			throw new IOException("Unexpected file type: " + gitObject.getFileMode());
		}

		final byte[] bytes = getFileSystem().getBytes(gitObject.getObjectId());

		/**
		 * Should not log here: if the charset is not UTF-8, this messes up the output.
		 */
		// LOGGER.debug("Read: {}.", new String(bytes, StandardCharsets.UTF_8));
		return new SeekableInMemoryByteChannel(bytes);
	}

	BasicFileAttributes readAttributes(Set<LinkOption> optionsSet) throws NoSuchFileException, IOException {
		final boolean followLinks = !optionsSet.contains(LinkOption.NOFOLLOW_LINKS);
		final GitObject gitObject = getGitObject(
				followLinks ? FollowLinksBehavior.FOLLOW_ALL_LINKS : FollowLinksBehavior.DO_NOT_FOLLOW_LINKS);

		LOGGER.info("Reading attributes of {}.", toString());
		final GitBasicFileAttributes gitBasicFileAttributes = new GitBasicFileAttributes(gitObject,
				getFileSystem().getSize(gitObject));
		if (followLinks) {
			verify(!gitBasicFileAttributes.isSymbolicLink());
		}
		return gitBasicFileAttributes;
	}

	GitPath readSymbolicLink()
			throws IOException, NoSuchFileException, NotLinkException, AbsoluteLinkException, SecurityException {
		final GitObject gitObject = getGitObject(FollowLinksBehavior.FOLLOW_LINKS_BUT_END);
		if (!gitObject.getFileMode().equals(FileMode.SYMLINK)) {
			throw new NotLinkException(toString());
		}
		Path target;
		try {
			target = getFileSystem().getLinkTarget(gitObject.getObjectId());
		} catch (NoContextAbsoluteLinkException e) {
			throw new AbsoluteLinkException(this, e.getTarget());
		}
		return GitRelativePath.relative(getFileSystem(), target);
	}
}
