package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import java.io.IOException;

public interface GitFileSystemWithHistoryFetcher extends AutoCloseable {

	public ImmutableSet<GitHubUsername> getAuthors();

	/**
	 * Retrieves the instance corresponding to the given author, closes any previously retrieved one.
	 *
	 * @param author the author whose git file system history must be retrieved
	 * @return the instance corresponding to the given author
	 */
	public GitHistorySimple goToFs(GitHubUsername author) throws IOException;

	@Override
	void close() throws IOException;
}
