package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.utils.Utils;

public class FindEnds {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(FindEnds.class);

	public static FindEnds withPrefix(String prefix) {
		return new FindEnds(prefix);
	}

	public static void main(String[] args) {
		final ImmutableSet<RepositoryCoordinatesWithPrefix> ended = FindEnds.withPrefix("commit").getEnded();
		LOGGER.info("Ended: {}.", ended);
	}

	private static final Path WORK_DIR = Paths.get("../../Java L3/En cours");
	private final String prefix;

	private FindEnds(String prefix) {
		this.prefix = checkNotNull(prefix);
	}

	public ImmutableSet<RepositoryCoordinatesWithPrefix> getEnded() {
		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", prefix);
		}
		final ImmutableSet<RepositoryCoordinatesWithPrefix> ended = repositories.stream().filter(this::hasEnd)
				.collect(ImmutableSet.toImmutableSet());
		return ended;
	}

	public boolean hasEnd(RepositoryCoordinates coord) {
		final Path projectsBaseDir = WORK_DIR.resolve(prefix);
		final Path projectDir = projectsBaseDir.resolve(coord.getRepositoryName());
		Utils.uncheck(() -> new GitCloner().download(GitUri.fromGitUri(coord.asURI()), projectDir));

		try (Git git = Utils.getOrThrowIO(() -> Git.open(projectDir.resolve(".git").toFile()))) {
			final List<Ref> remoteRefs = Utils.getOrThrow(() -> git.branchList().setListMode(ListMode.REMOTE).call());
			LOGGER.info("Remote refs for {}: {}.", coord, remoteRefs);
			return remoteRefs.stream().anyMatch(r -> r.getName().equals("refs/remotes/origin/END"));
		}
	}
}
