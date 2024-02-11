package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.common.GitUri;
import io.github.oliviercailloux.git.factory.GitCloner;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.jaris.exceptions.Unchecker;
import io.github.oliviercailloux.utils.Utils;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindEnds {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(FindEnds.class);

	public static FindEnds withPrefix(String prefix) {
		return new FindEnds(prefix);
	}

	public static void main(String[] args) {
		final ImmutableSet<RepositoryCoordinatesWithPrefix> ended =
				FindEnds.withPrefix("colors").getEnded();
		LOGGER.info("Ended: {}.", ended);
	}

	private static final Path WORK_DIR = Utils.getTempDirectory();
	private final String prefix;

	private FindEnds(String prefix) {
		this.prefix = checkNotNull(prefix);
	}

	public ImmutableSet<RepositoryCoordinatesWithPrefix> getEnded() {
		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", prefix);
		}
		final ImmutableSet<RepositoryCoordinatesWithPrefix> ended =
				repositories.stream().filter(this::hasEnd).collect(ImmutableSet.toImmutableSet());
		return ended;
	}

	public boolean hasEnd(RepositoryCoordinates coord) {
		final Path projectsBaseDir = WORK_DIR.resolve(prefix);
		final Path projectDir = projectsBaseDir.resolve(coord.getRepositoryName());
		/* False because sometimes the main branch has a strange name. */
		GitCloner.create().download(GitUri.fromUri(coord.asURI()), projectDir).close();

		try (Git git = IO_UNCHECKER.getUsing(() -> Git.open(projectDir.resolve(".git").toFile()))) {
			final List<Ref> remoteRefs = Unchecker.wrappingWith(IllegalStateException::new)
					.getUsing(() -> git.branchList().setListMode(ListMode.REMOTE).call());
			LOGGER.info("Remote refs for {}: {}.", coord, remoteRefs);
			return remoteRefs.stream()
					.anyMatch(r -> r.getName().equalsIgnoreCase("refs/remotes/origin/end"));
		}
	}
}
