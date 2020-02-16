package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class GitUtils {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitUtils.class);

	public static ZonedDateTime getCreationTime(RevCommit commit) {
		/** https://stackoverflow.com/questions/11856983 */
		final PersonIdent authorIdent = commit.getAuthorIdent();
		final ZonedDateTime authorCreationTime = getCreationTime(authorIdent);
		final PersonIdent committerIdent = commit.getCommitterIdent();
		final ZonedDateTime committerCreationTime = getCreationTime(committerIdent);
		checkArgument(!authorCreationTime.isAfter(committerCreationTime),
				String.format("Author %s, creation time: %s, committer %s, creation time: %s", authorIdent.getName(),
						authorCreationTime, committerIdent.getName(), committerCreationTime));
		return committerCreationTime;
	}

	public static ZonedDateTime getCreationTime(PersonIdent ident) {
		final Date creationInstant = ident.getWhen();
		final TimeZone creationZone = ident.getTimeZone();
		final ZonedDateTime creationTime = ZonedDateTime.ofInstant(creationInstant.toInstant(),
				creationZone.toZoneId());
		return creationTime;
	}

	public static ImmutableList<String> toOIds(Collection<RevCommit> commits) {
		return commits.stream().map(RevCommit::getName).collect(ImmutableList.toImmutableList());
	}

	/**
	 * @param gitDir the GIT_DIR directory (usually ending with .git)
	 * @return the graph of commits pointed to by at least one ref in /refs plus
	 *         HEAD, thus including remotes and local branches and tags, together
	 *         with their parents.
	 */
	public static GitLocalHistory getHistory(File gitDir) throws IOException {
		final GitLocalHistory history;
		try (FileRepository repo = new FileRepository(gitDir)) {
			if (!repo.getObjectDatabase().exists()) {
				throw new RepositoryNotFoundException(gitDir);
			}
			history = getHistory(repo);
		}
		return history;
	}

	public static GitLocalHistory getHistory(Repository repository) throws IOException {
		verify(repository.getObjectDatabase().exists());
		final GitLocalHistory history;
		/**
		 * Log command fails (with org.eclipse.jgit.api.errors.NoHeadException) if “No
		 * HEAD exists and no explicit starting revision was specified”.
		 */
		if (!repository.getRefDatabase().hasRefs()) {
			return GitLocalHistory.from(ImmutableList.of());
		}
		try (Git git = Git.wrap(repository)) {
			final Iterable<RevCommit> commits = git.log().all().call();
			history = GitLocalHistory.from(commits);
			LOGGER.info("Commits: {}.", history.getGraph().nodes());
		} catch (NoHeadException e) {
			throw new IllegalStateException(e);
		} catch (GitAPIException e) {
			throw new IllegalStateException(e);
		}
		return history;
	}

}
