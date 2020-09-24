package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.github.oliviercailloux.exceptions.Unchecker.IO_UNCHECKER;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import io.github.oliviercailloux.exceptions.Unchecker;
import io.github.oliviercailloux.utils.Utils;

public class GitCloner {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitCloner.class);
	@SuppressWarnings("unused")
	private static final Unchecker<GitAPIException, IllegalStateException> UNCHECKER = Unchecker
			.wrappingWith(IllegalStateException::new);

	public static GitCloner newInstance() {
		return new GitCloner();
	}

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

	public void clone(GitUri gitUri, Repository repo) {
		try (Git git = Git.wrap(repo)) {
			git.fetch().setRemote(gitUri.asString()).setRefSpecs(new RefSpec("+refs/heads/*:refs/heads/*")).call();
			maybeCheckCommonRefs(git);
		} catch (GitAPIException e) {
			throw new IllegalStateException(e);
		}
	}

	public void download(GitUri uri, Path workTree) {
		download(uri, workTree, false);
	}

	public void downloadBare(GitUri uri, Path gitDir) {
		download(uri, gitDir, true);
	}

	/**
	 * TODO return the repository.
	 *
	 * @param repositoryDirectory GIT_DIR (replacing .git dir) if bare (see
	 *                            {@link FileRepository}), otherwise, work tree dir,
	 *                            in which a .git dir will be created (or exists).
	 * @param allowBare           <code>true</code> to clone bare if not exists (if
	 *                            exists, this method will not check whether it is
	 *                            bare)
	 */
	private void download(GitUri uri, Path repositoryDirectory, boolean allowBare) {
		localRefs = null;
		remoteRefs = null;
		final boolean exists = Files.exists(repositoryDirectory);
		if (!exists) {
			final CloneCommand cloneCmd = Git.cloneRepository();
			cloneCmd.setURI(uri.asString());
			cloneCmd.setBare(allowBare);
			final File dest = repositoryDirectory.toFile();
			cloneCmd.setDirectory(dest);
			LOGGER.info("Cloning {} to {}.", uri, dest);
//			try {
//				cloneCmd.call().close();
//			} catch (GitAPIException e) {
//				throw new IOException(e);
//			}
//			try (Git git = Git.open(repositoryDirectory.toFile())) {
			try (Git git = cloneCmd.call()) {
				maybeCheckCommonRefs(git);
			} catch (GitAPIException e) {
				throw new IllegalStateException(e);
			}
		} else {
			try (Git git = Git.open(repositoryDirectory.toFile())) {
				final List<RemoteConfig> remoteList = git.remoteList().call();
				final Optional<RemoteConfig> origin = remoteList.stream().filter((r) -> r.getName().equals("origin"))
						.collect(MoreCollectors.toOptional());
				if (!git.getRepository().isBare() && !git.status().call().isClean()) {
					throw new IllegalStateException("Can’t update: not clean.");
				}
				final String fullBranch = git.getRepository().getFullBranch();
				LOGGER.debug("HEAD: {}.", fullBranch);
				if (origin.isPresent() && origin.get().getURIs().size() == 1
						&& origin.get().getURIs().get(0).toString().equals(uri.asString())) {
					final FetchResult fetchResult = git.fetch().call();
					if (git.getRepository().isBare()) {
						final String messages = fetchResult.getMessages();
						if (!messages.isEmpty()) {
							LOGGER.error("Fetch result: {}.", messages);
							throw new IllegalStateException("Fetch failed (perhaps)");
						}
					} else {
						final Ref r = fetchResult.getAdvertisedRef(fullBranch);
						if (r == null) {
							/** Happens with a repository on GitHub that has never been pushed to. */
							LOGGER.info("Did not pull, remote server did not advertise {}.", fullBranch);
						} else {
							final PullResult pullResult = git.pull().call();
							if (!pullResult.isSuccessful()) {
								LOGGER.error("Pull failed with results: {}, {}, {}.", pullResult.getFetchResult(),
										pullResult.getMergeResult(), pullResult.getRebaseResult());
								throw new IllegalStateException("Merge failed");
							}
						}
					}
				} else {
					throw new IllegalStateException("Unexpected remote.");
				}

				maybeCheckCommonRefs(git);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} catch (GitAPIException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	private void maybeCheckCommonRefs(Git git) throws GitAPIException {
		if (checkCommonRefsAgree) {
			final List<Ref> branches = git.branchList().setListMode(ListMode.ALL).call();
			final List<Ref> allRefs = IO_UNCHECKER.getUsing(() -> git.getRepository().getRefDatabase().getRefs());
			LOGGER.debug("All refs: {}, branches: {}.", allRefs, branches);

			parse(branches);

			final Ref head = IO_UNCHECKER.getUsing(() -> git.getRepository().findRef(Constants.HEAD));
			checkState(head != null, "Did you forget to create the repository?");
			checkState(head.getTarget().getName().equals("refs/heads/master"));

			final ImmutableMap<String, ObjectId> originRefs = remoteRefs.row("origin");
			final SetView<String> commonRefShortNames = Sets.intersection(originRefs.keySet(), localRefs.keySet());
			final ImmutableSet<String> disagreeingRefShortNames = commonRefShortNames.stream()
					.filter((s) -> !originRefs.get(s).equals(localRefs.get(s))).collect(ImmutableSet.toImmutableSet());
			checkState(disagreeingRefShortNames.isEmpty(),
					String.format("Disagreeing: %s. Origin refs: %s; local refs: %s.", disagreeingRefShortNames,
							originRefs, localRefs));
		}
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
				checkState(remoteName == null, fullName);
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
