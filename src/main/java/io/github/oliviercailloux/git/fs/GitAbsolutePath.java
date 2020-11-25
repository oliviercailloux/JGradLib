package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevTree;

import com.google.common.collect.PeekingIterator;

import io.github.oliviercailloux.utils.SeekableInMemoryByteChannel;

abstract class GitAbsolutePath extends GitPath {

	private static class TransformedPeekingIterator implements PeekingIterator<GitPath> {
		private PeekingIterator<String> delegate;
		private Function<String, GitPath> transform;

		public TransformedPeekingIterator(PeekingIterator<String> delegate, Function<String, GitPath> transform) {
			this.delegate = checkNotNull(delegate);
			this.transform = checkNotNull(transform);
		}

		@Override
		public boolean hasNext() {
			return delegate.hasNext();
		}

		@Override
		public GitPath peek() {
			return transform.apply(delegate.peek());
		}

		@Override
		public GitPath next() {
			return transform.apply(delegate.next());
		}

		@Override
		public void remove() {
			delegate.remove();
		}
	}

	private static class PathIterator implements PeekingIterator<GitPath> {
		private final PeekingIterator<GitPath> unfilteredIterator;
		private Filter<? super GitPath> filter;

		public PathIterator(PeekingIterator<GitPath> unfilteredIterator, Filter<? super GitPath> filter) {
			this.unfilteredIterator = checkNotNull(unfilteredIterator);
			this.filter = checkNotNull(filter);
		}

		@Override
		public GitPath peek() throws DirectoryIteratorException {
			hasNext();
			return unfilteredIterator.peek();
		}

		@Override
		public boolean hasNext() {
			while (unfilteredIterator.hasNext()) {
				final GitPath next = unfilteredIterator.peek();
				final boolean accept;
				try {
					accept = filter.accept(next);
				} catch (IOException e) {
					throw new DirectoryIteratorException(e);
				}
				if (accept) {
					break;
				}
				unfilteredIterator.next();
			}
			return unfilteredIterator.hasNext();
		}

		@Override
		public GitPath next() {
			final GitPath peek = peek();
			unfilteredIterator.next();
			return peek;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

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
		private ObjectId objectId;
		private FileMode fileMode;
		private long size;

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

	static GitAbsolutePath givenRoot(GitPathRoot root, Path internalPath) {
		checkNotNull(root);
		checkArgument(internalPath.isAbsolute());

		final GitAbsolutePath absolute;
		if (internalPath.getNameCount() == 0) {
			verify(internalPath.toString().equals("/"));
			absolute = root;
		} else {
			absolute = new GitPathNonRoot(root, internalPath);
		}

		return absolute;
	}

	static GitAbsolutePath givenRev(GitFileSystem fs, GitRev gitRev, Path internalPath) {
		checkNotNull(gitRev);
		final GitPathRoot root = new GitPathRoot(fs, gitRev);
		return givenRoot(root, internalPath);
	}

	@Override
	public GitAbsolutePath toAbsolutePath() {
		return this;
	}

	@Override
	public boolean isAbsolute() {
		return true;
	}

	/**
	 * Guaranteed to exist.
	 *
	 * @return a tree if this path represents a directory; a blob if this path
	 *         represents a file; the commit tree iff this path is solely a root
	 *         component.
	 */
	abstract GitObject getGitObject() throws NoSuchFileException, IOException;

	abstract RevTree getRevTree() throws NoSuchFileException, NotDirectoryException, IOException;

	SeekableByteChannel newByteChannel() throws NoSuchFileException, IOException {
		final GitObject gitObject = getGitObject();
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

	BasicFileAttributes readAttributes(Set<LinkOption> optionsSet) throws IOException {
		final GitObject gitObject = getGitObject();

		final GitBasicFileAttributes gitBasicFileAttributes = new GitBasicFileAttributes(gitObject,
				getFileSystem().getSize(gitObject));
		if (!optionsSet.contains(LinkOption.NOFOLLOW_LINKS) && gitBasicFileAttributes.isSymbolicLink()) {
			throw new UnsupportedOperationException(
					"Path " + toString() + "is a sym link; I do not follow symlinks yet.");
		}
		return gitBasicFileAttributes;
	}

	@SuppressWarnings("resource")
	DirectoryStream<GitPath> newDirectoryStream(Filter<? super GitPath> filter) throws IOException {
		final RevTree tree = getRevTree();
		final DirectoryStream<String> directoryStream = getFileSystem().iterate(tree);

		return new DirectoryStream<>() {
			@Override
			public void close() throws IOException {
				directoryStream.close();
			}

			@Override
			public Iterator<GitPath> iterator() {
				final PeekingIterator<String> namesIterator = (PeekingIterator<String>) directoryStream.iterator();
				final TransformedPeekingIterator unfilteredPathIterator = new TransformedPeekingIterator(namesIterator,
						s -> resolveUsingRelativePath(s));
				return new PathIterator(unfilteredPathIterator, filter);
			}
		};
	}

	GitPath resolveUsingRelativePath(String directoryEntry) {
		// TODO test directory stream. Check resolve(String).
		verify(!directoryEntry.isEmpty());
		final Path jimFsDirectoryEntry = GitFileSystem.JIM_FS_EMPTY.resolve(directoryEntry);
		final Path jimFsAbsoluteDirectoryEntry = getInternalPath().resolve(jimFsDirectoryEntry);
		final GitPath next = GitPath.given(getRoot(), jimFsAbsoluteDirectoryEntry);
		return next;
	}
}
