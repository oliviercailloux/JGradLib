package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import io.github.oliviercailloux.utils.Utils;

public class GitCloner {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitCloner.class);
	private ImmutableTable<String, String, ObjectId> remoteRefs;
	private ImmutableMap<String, ObjectId> localRefs;
	private boolean checkCommonRefsAgree;

	public GitCloner() {
		remoteRefs = null;
		localRefs = null;
		checkCommonRefsAgree = true;
	}

	public boolean checksCommonRefsAgree() {
		return checkCommonRefsAgree;
	}

	public void setCheckCommonRefsAgree(boolean checkCommonRefsAgree) {
		this.checkCommonRefsAgree = checkCommonRefsAgree;
	}

	public void download(GitUri uri) throws IOException {
		download(uri, getGitFolderPathInTemp(uri.getRepositoryName()));
	}

	public void download(GitUri uri, Path workTree) throws IOException {
		download(uri, workTree, false);
	}

	public void downloadBare(GitUri uri, Path gitDir) throws IOException {
		download(uri, gitDir, true);
	}

	/**
	 * @param repositoryDirectory GIT_DIR (replacing .git dir) if bare (see
	 *                            {@link FileRepository}), otherwise, work tree dir,
	 *                            in which a .git dir will be created (or exists).
	 * @param allowBare           <code>true</code> to clone bare if not exists (if
	 *                            exists, this method will not check whether it is
	 *                            bare)
	 */
	private void download(GitUri uri, Path repositoryDirectory, boolean allowBare) throws IOException {
		localRefs = null;
		remoteRefs = null;
		final boolean exists = Files.exists(repositoryDirectory);
		if (!exists) {
			final CloneCommand cloneCmd = Git.cloneRepository();
			cloneCmd.setURI(uri.getGitString());
			cloneCmd.setBare(allowBare);
			final File dest = repositoryDirectory.toFile();
			cloneCmd.setDirectory(dest);
			LOGGER.info("Cloning {} to {}.", uri, dest);
			try {
				cloneCmd.call().close();
			} catch (GitAPIException e) {
				throw new IOException(e);
			}
		} else {
			try (Git git = Git.open(repositoryDirectory.toFile())) {
				final List<RemoteConfig> remoteList = git.remoteList().call();
				final Optional<RemoteConfig> origin = remoteList.stream().filter((r) -> r.getName().equals("origin"))
						.collect(MoreCollectors.toOptional());
				if (!git.getRepository().isBare() && !git.status().call().isClean()) {
					throw new IllegalStateException("Can’t update: not clean.");
				}
				LOGGER.info("HEAD: {}.", git.getRepository().getFullBranch());
				if (origin.isPresent() && origin.get().getURIs().size() == 1
						&& origin.get().getURIs().get(0).toString().equals(uri.getGitString())) {
					if (git.getRepository().isBare()) {
						final FetchResult result = git.fetch().call();
						final String messages = result.getMessages();
						if (!messages.isEmpty()) {
							LOGGER.error("Fetch result: {}.", messages);
							throw new IllegalStateException("Fetch failed (perhaps)");
						}
					} else {
						final PullResult result = git.pull().call();
						if (!result.isSuccessful()) {
							LOGGER.error("Merge failed with results: {}, {}, {}.", result.getFetchResult(),
									result.getMergeResult(), result.getRebaseResult());
							throw new IllegalStateException("Merge failed");
						}
					}
				} else {
					throw new IllegalStateException("Unexpected remote.");
				}

				if (checkCommonRefsAgree) {
					final List<Ref> branches = git.branchList().setListMode(ListMode.ALL).call();
					parse(branches);

					final ImmutableMap<String, ObjectId> originRefs = remoteRefs.row("origin");
					final SetView<String> commonRefShortNames = Sets.intersection(originRefs.keySet(),
							localRefs.keySet());
					final ImmutableSet<String> disagreeingRefShortNames = commonRefShortNames.stream()
							.filter((s) -> !originRefs.get(s).equals(localRefs.get(s)))
							.collect(ImmutableSet.toImmutableSet());
					checkState(disagreeingRefShortNames.isEmpty(),
							String.format("Disagreeing: %s. Origin refs: %s; local refs: %s.", disagreeingRefShortNames,
									originRefs, localRefs));
				}
			} catch (GitAPIException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	private Path getGitFolderPathInTemp(String repositoryName) {
		final Path tmpDir = Utils.getTempDirectory();
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

	private void parse(List<Ref> branches) {
		final ImmutableTable.Builder<String, String, ObjectId> remoteRefsBuilder = ImmutableTable.builder();
		final ImmutableMap.Builder<String, ObjectId> localRefsBuilder = ImmutableMap.builder();
		for (Ref branch : branches) {
			final String fullName = branch.getName();
			final Pattern refPattern = Pattern
					.compile("refs/(?<kind>[^/]+)(/(?<remoteName>[^/]+))?/(?<shortName>[^/]+)");
			final Matcher matcher = refPattern.matcher(fullName);
			checkState(matcher.matches(), fullName);
			final String kind = matcher.group("kind");
			final String remoteName = matcher.group("remoteName");
			final String shortName = matcher.group("shortName");
			final ObjectId objectId = branch.getObjectId();
			switch (kind) {
			case "remotes":
				checkState(remoteName.length() >= 1);
				remoteRefsBuilder.put(remoteName, shortName, objectId);
				break;
			case "heads":
				checkState(remoteName == null);
				localRefsBuilder.put(shortName, objectId);
				break;
			default:
				throw new IllegalStateException("Unknown ref kind: " + kind);
			}
		}
		remoteRefs = remoteRefsBuilder.build();
		localRefs = localRefsBuilder.build();
	}

}
