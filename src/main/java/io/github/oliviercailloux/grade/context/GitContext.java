package io.github.oliviercailloux.grade.context;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.jgit.revwalk.RevCommit;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.GradingException;

/**
 * TODO currently this object is used both to access a “default” commit and to
 * navigate through commits. This should be separated.
 */
public interface GitContext {

	public Client getClient();

	public Optional<RevCommit> getMainCommit();

	/**
	 * If no main commit, returns a files reader which never finds any file.
	 */
	public default FilesReader getMainCommitFilesReader() {
		return getMainCommit().isPresent() ? getFilesReader(getMainCommit().get()) : new FilesReader() {
			@Override
			public MultiContent getMultiContent(Predicate<FileContent> predicate) throws GradingException {
				return MultiContent.empty();
			}

			@Override
			public String getContent(Path relativePath) throws GradingException {
				return "";
			}
		};
	}

	public FilesReader getFilesReader(RevCommit sourceCommit);

}
