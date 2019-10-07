package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RemoteConfig;

import com.google.common.collect.MoreCollectors;

import io.github.oliviercailloux.git.fs.GitScheme;
import io.github.oliviercailloux.utils.Utils;

public class GitCloner {

	private Path getGitFolderPathInTemp(GitUri uris) {
		final Path tmpDir = Utils.getTempDirectory();
		final String repositoryName = uris.getRepositoryName();
		checkArgument(!repositoryName.contains(FileSystems.getDefault().getSeparator()));
		final Path subFolder = tmpDir.resolve(repositoryName);
		/**
		 * This check is required because the separator we check against is only the
		 * default one, there could be others, which would allow to create something
		 * like /tmp/..-mypath, where the - designates this alternative separator.
		 */
		checkArgument(subFolder.getParent().equals(tmpDir));
		return subFolder;
	}

	private void update(GitUri uri, Path workTree) throws IOException {
		/**
		 * If file repo, and this path equals the repo path: nothing to check, it’s up
		 * to date.
		 */
		final boolean direct = uri.getGitScheme() == GitScheme.FILE
				&& workTree.toString().equals(uri.getRepositoryPath());
		final boolean exists = Files.exists(workTree);
		if (direct || !update) {
			checkArgument(exists);
		}
		if (!exists && !direct && update) {
			final CloneCommand cloneCmd = Git.cloneRepository();
			cloneCmd.setURI(uri.getGitString());
			final File dest = workTree.toFile();
			cloneCmd.setDirectory(dest);
			LOGGER.info("Cloning {} to {}.", uri, dest);
			try {
				cloneCmd.call().close();
			} catch (GitAPIException e) {
				throw new IOException(e);
			}
		}
		if (exists && !direct && update) {
			try (Repository repo = new FileRepositoryBuilder().setWorkTree(workTree.toFile()).build()) {
				try (Git git = Git.wrap(repo)) {
					final List<RemoteConfig> remoteList = git.remoteList().call();
					final Optional<RemoteConfig> origin = remoteList.stream()
							.filter((r) -> r.getName().equals("origin")).collect(MoreCollectors.toOptional());
					if (!git.status().call().isClean()) {
						throw new IllegalStateException("Can’t update: not clean.");
					}
					LOGGER.info("Full branch: {}.", repo.getFullBranch());
					if (origin.isPresent() && origin.get().getURIs().size() == 1
							&& origin.get().getURIs().get(0).toString().equals(uri.getGitString())) {
						final PullResult result = git.pull().call();
						if (!result.isSuccessful()) {
							LOGGER.error("Merge failed with results: {}, {}, {}.", result.getFetchResult(),
									result.getMergeResult(), result.getRebaseResult());
							throw new IllegalStateException("Merge failed");
						}
					} else {
						throw new IllegalStateException("Unexpected remote.");
					}
				} catch (GitAPIException e) {
					throw new IllegalStateException(e);
				}
			}
		}
	}

}
