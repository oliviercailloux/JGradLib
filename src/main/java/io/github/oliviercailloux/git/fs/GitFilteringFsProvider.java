package io.github.oliviercailloux.git.fs;

import io.github.oliviercailloux.gitjfs.AbsoluteLinkException;
import io.github.oliviercailloux.gitjfs.impl.GitDfsFileSystemImpl;
import io.github.oliviercailloux.gitjfs.impl.GitFileFileSystemImpl;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemImpl;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemProviderImpl;
import io.github.oliviercailloux.gitjfs.impl.GitPathImpl;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotLinkException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;

public class GitFilteringFsProvider extends GitFileSystemProviderImpl {

	@Override
	public String getScheme() {
		return super.getScheme() + "-filtering";
	}

	@Deprecated
	@Override
	public GitFileFileSystemImpl newFileSystem(URI gitFsUri, Map<String, ?> env)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException {
		throw new UnsupportedOperationException();
	}

	@Deprecated
	@Override
	public GitFileFileSystemImpl newFileSystem(Path gitDir, Map<String, ?> env)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException {
		throw new UnsupportedOperationException();
	}

	@Deprecated
	@Override
	public GitFileFileSystemImpl newFileSystemFromGitDir(Path gitDir)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException {
		TODO();
		return super.newFileSystemFromGitDir(gitDir);
	}

	@Deprecated
	@Override
	public GitFileSystemImpl newFileSystemFromRepository(Repository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, IOException {
		TODO();
		return super.newFileSystemFromRepository(repository);
	}

	@Override
	public GitFileFileSystemImpl newFileSystemFromFileRepository(FileRepository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, IOException {
		TODO();
		return super.newFileSystemFromFileRepository(repository);
	}

	@Deprecated
	@Override
	public GitDfsFileSystemImpl newFileSystemFromDfsRepository(DfsRepository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException {
		TODO();
		return super.newFileSystemFromDfsRepository(repository);
	}

	@Deprecated
	@Override
	public GitFileSystemImpl getFileSystem(URI gitFsUri) throws FileSystemNotFoundException {
		TODO();
		return super.getFileSystem(gitFsUri);
	}

	@Override
	public GitFileFileSystemImpl getFileSystemFromGitDir(Path gitDir) throws FileSystemNotFoundException {
		TODO();
		return super.getFileSystemFromGitDir(gitDir);
	}

	@Override
	public GitDfsFileSystemImpl getFileSystemFromRepositoryName(String name) throws FileSystemNotFoundException {
		TODO();
		return super.getFileSystemFromRepositoryName(name);
	}

	@Override
	public GitPathImpl getPath(URI gitFsUri) {
		TODO();
		return super.getPath(gitFsUri);
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		TODO();
		return super.newByteChannel(path, options, attrs);
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		TODO();
		return super.newDirectoryStream(dir, filter);
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws ReadOnlyFileSystemException {
		TODO();
		super.createDirectory(dir, attrs);
	}

	@Override
	public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs)
			throws ReadOnlyFileSystemException {
		TODO();
		super.createSymbolicLink(link, target, attrs);
	}

	@Override
	public void createLink(Path link, Path existing) throws ReadOnlyFileSystemException {
		TODO();
		super.createLink(link, existing);
	}

	@Override
	public void delete(Path path) throws ReadOnlyFileSystemException {
		TODO();
		super.delete(path);
	}

	@Override
	public boolean deleteIfExists(Path path) throws ReadOnlyFileSystemException {
		TODO();
		return super.deleteIfExists(path);
	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) throws ReadOnlyFileSystemException {
		TODO();
		super.copy(source, target, options);
	}

	@Override
	public void move(Path source, Path target, CopyOption... options) throws ReadOnlyFileSystemException {
		TODO();
		super.move(source, target, options);
	}

	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		TODO();
		return super.isSameFile(path, path2);
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		TODO();
		return super.isHidden(path);
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		TODO();
		return super.getFileStore(path);
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes)
			throws ReadOnlyFileSystemException, AccessDeniedException, NoSuchFileException, IOException {
		TODO();
		super.checkAccess(path, modes);
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		TODO();
		return super.getFileAttributeView(path, type, options);
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException {
		TODO();
		return super.readAttributes(path, type, options);
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		TODO();
		return super.readAttributes(path, attributes, options);
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
			throws ReadOnlyFileSystemException {
		TODO();
		super.setAttribute(path, attribute, value, options);
	}

	@Override
	public Path readSymbolicLink(Path link)
			throws IOException, NoSuchFileException, NotLinkException, AbsoluteLinkException, SecurityException {
		TODO();
		return super.readSymbolicLink(link);
	}
}
