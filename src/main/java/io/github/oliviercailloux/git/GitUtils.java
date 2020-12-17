package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;

import io.github.oliviercailloux.utils.Utils;

public class GitUtils {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitUtils.class);

	/**
	 * TODO seems like an incorrect assumption (corrected somewhere else!)
	 */
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
				LOGGER.info("No object database at " + gitDir + ", did you forget to use the GIT_DIR?");
				throw new RepositoryNotFoundException(gitDir);
			}
			history = getOldHistory(repo);
		}
		return history;
	}

	public static GitLocalHistory getOldHistory(Repository repository) throws IOException {
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

	public static GitHistory getHistory(Repository repository) throws IOException {
		/**
		 * Log command fails (with org.eclipse.jgit.api.errors.NoHeadException) if “No
		 * HEAD exists and no explicit starting revision was specified”.
		 */
//		if (!repository.getRefDatabase().hasRefs()) {
//			return GitHistory.create(GraphBuilder.directed().build(), ImmutableMap.of());
//		}

		final ImmutableSet<RevCommit> allCommits;
		/** Taken from GitFileSystem. */
		try (RevWalk walk = new RevWalk(repository)) {
			final List<Ref> refs = repository.getRefDatabase().getRefsByPrefix(Constants.R_REFS);
			walk.setRetainBody(true);
			for (Ref ref : refs) {
				walk.markStart(walk.parseCommit(ref.getLeaf().getObjectId()));
			}
			allCommits = ImmutableSet.copyOf(walk);
		}

		final Graph<ObjectId> graph = Graphs.transpose(Utils.asGraph(c -> Arrays.asList(c.getParents()), allCommits));
		final ImmutableMap<ObjectId, Instant> dates = allCommits.stream()
				.collect(ImmutableMap.toImmutableMap(c -> c, c -> getCreationTime(c).toInstant()));

		allCommits.stream().forEach(RevCommit::disposeBody);

		return GitHistory.create(graph, dates);
	}

}
