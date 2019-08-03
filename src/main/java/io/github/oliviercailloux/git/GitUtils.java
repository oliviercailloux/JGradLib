package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.v3.CommitGitHubDescription;
import io.github.oliviercailloux.git.git_hub.model.v3.PayloadCommitDescription;
import io.github.oliviercailloux.git.git_hub.model.v3.PushEvent;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;

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
		Date creationInstant = ident.getWhen();
		TimeZone creationZone = ident.getTimeZone();
		final ZonedDateTime creationTime = ZonedDateTime.ofInstant(creationInstant.toInstant(),
				creationZone.toZoneId());
		return creationTime;
	}

	public static ImmutableList<String> toOIds(Collection<RevCommit> commits) {
		return commits.stream().map(RevCommit::getName).collect(ImmutableList.toImmutableList());
	}

	public static void main(String[] args) throws Exception {
//		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "java-course");// 82-82, no diff
//		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "projets");// 8-8, no diff
//		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "jmcda-xmcda2-ws-examples");//8, no diff
		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "xerces-user");// 1-0, diff!
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			final ImmutableList<ObjectId> commits = fetcher.getCommitsGitHubDescriptions(coord).stream()
					.filter((c) -> c.getCommitterCommitDate().compareTo(Instant.now().minus(90, ChronoUnit.DAYS)) >= 0)
					.map(CommitGitHubDescription::getSha).collect(ImmutableList.toImmutableList());
			final ImmutableList<PushEvent> pushes = fetcher.getPushEvents(coord);
			final ImmutableList<ObjectId> pushedCommits = pushes.stream().map(PushEvent::getPushPayload)
					.flatMap((p) -> p.getCommits().stream()).map(PayloadCommitDescription::getSha)
					.collect(ImmutableList.toImmutableList());
			final ImmutableSet<ObjectId> diff = Sets
					.symmetricDifference(ImmutableSet.copyOf(commits), ImmutableSet.copyOf(pushedCommits))
					.immutableCopy();
			LOGGER.info("Diff ({}): {}.", diff.size(), diff);
			LOGGER.info("All commits ({}): {}, pushed ones ({}): {}.", commits.size(), commits, pushedCommits.size(),
					pushedCommits);
		}
	}

}
