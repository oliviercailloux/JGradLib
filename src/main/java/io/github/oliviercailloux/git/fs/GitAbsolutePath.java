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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevTree;

import com.google.common.collect.PeekingIterator;

import io.github.oliviercailloux.git.fs.GitFileSystem.TreeWalkDirectoryStream;
import io.github.oliviercailloux.utils.SeekableInMemoryByteChannel;

/**
 * A git path with a root component and a (possibly empty) sequence of non-empty
 * names.
 */
abstract class GitAbsolutePath extends GitPath {

	private static class TransformedPeekingIterator implements PeekingIterator<GitPath> {
		private PeekingIterator<String> delegate;
		private final Function<String, GitPath> transform;

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
		private final Filter<? super GitPath> filter;
		private GitPath next;

		public PathIterator(PeekingIterator<GitPath> unfilteredIterator, Filter<? super GitPath> filter) {
			this.unfilteredIterator = checkNotNull(unfilteredIterator);
			this.filter = checkNotNull(filter);
			next = null;
		}

		@Override
		public GitPath peek() throws DirectoryIteratorException {
			if (next == null) {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
			}
			verify(hasNext());
			verify(next != null);
			return next;
		}

		@Override
		public boolean hasNext() {
			if (next != null) {
				return true;
			}
			boolean accepted = false;
			while (unfilteredIterator.hasNext() && !accepted) {
				next = unfilteredIterator.next();
				verify(next != null);
				try {
					accepted = filter.accept(next);
				} catch (IOException e) {
					throw new DirectoryIteratorException(e);
				}
			}
			return accepted;
		}

		@Override
		public GitPath next() {
			final GitPath current = peek();
			next = null;
			return current;
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
	 * @param names may contain empty strings; first non-empty string, if any, must
	 *              start with a slash
	 */
	static GitAbsolutePath givenRoot(GitPathRoot root, List<String> names) {
		checkNotNull(root);
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
	abstract GitObject getGitObject() throws NoSuchFileException, IOException;

	/**
	 * Returns a rev tree iff this path refers to a directory, and the commit tree
	 * iff this path is solely a root component. TODO what about links?
	 *
	 * @throws NoSuchFileException
	 * @throws NotDirectoryException
	 * @throws IOException
	 */
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

	BasicFileAttributes readAttributes(Set<LinkOption> optionsSet) throws NoSuchFileException, IOException {
		final GitObject gitObject = getGitObject();

		final GitBasicFileAttributes gitBasicFileAttributes = new GitBasicFileAttributes(gitObject,
				getFileSystem().getSize(gitObject));
		if (!optionsSet.contains(LinkOption.NOFOLLOW_LINKS) && gitBasicFileAttributes.isSymbolicLink()) {
			// TODO
			throw new UnsupportedOperationException(
					"Path " + toString() + "is a sym link; I do not follow symlinks yet.");
		}
		return gitBasicFileAttributes;
	}

	@SuppressWarnings("resource")
	DirectoryStream<GitPath> newDirectoryStream(Filter<? super GitPath> filter) throws IOException {
		// TODO test directory stream.
		final RevTree tree = getRevTree();
		final TreeWalkDirectoryStream directoryStream = getFileSystem().iterate(tree);

		final DirectoryStream<GitPath> toReturn = new DirectoryStream<>() {
			@Override
			public void close() throws IOException {
				directoryStream.close();
			}

			/**
			 * As requested per the contract of {@link DirectoryStream}, invoking the
			 * iterator method to obtain a second or subsequent iterator throws
			 * IllegalStateException.
			 * <p>
			 * An important property of the directory stream's Iterator is that its hasNext
			 * method is guaranteed to read-ahead by at least one element. If hasNext method
			 * returns true, and is followed by a call to the next method, it is guaranteed
			 * that the next method will not throw an exception due to an I/O error, or
			 * because the stream has been closed. The Iterator does not support the remove
			 * operation.
			 * <p>
			 * Once a directory stream is closed, then further access to the directory,
			 * using the Iterator, behaves as if the end of stream has been reached. Due to
			 * read-ahead, the Iterator may return one or more elements after the directory
			 * stream has been closed.
			 * <p>
			 * If an I/O error is encountered when accessing the directory then it causes
			 * the Iterator's hasNext or next methods to throw DirectoryIteratorException
			 * with the IOException as the cause. As stated above, the hasNext method is
			 * guaranteed to read-ahead by at least one element. This means that if hasNext
			 * method returns true, and is followed by a call to the next method, then it is
			 * guaranteed that the next method will not fail with a
			 * DirectoryIteratorException.
			 */
			@Override
			public Iterator<GitPath> iterator() {
				final PeekingIterator<String> namesIterator = directoryStream.iterator();
				final TransformedPeekingIterator unfilteredPathIterator = new TransformedPeekingIterator(namesIterator,
						s -> resolveRelativePath(s));
				return new PathIterator(unfilteredPathIterator, filter);
			}
		};
		getFileSystem().toClose(toReturn);
		return toReturn;
	}

	GitPath resolveRelativePath(String directoryEntry) {
		verify(!directoryEntry.isEmpty());
		verify(!directoryEntry.startsWith("/"));
		return resolve(directoryEntry);
	}
}
