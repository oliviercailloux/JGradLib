package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import io.github.oliviercailloux.jaris.exceptions.Unchecker;

public class GitCloner {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitCloner.class);
	@SuppressWarnings("unused")
	private static final Unchecker<GitAPIException, IllegalStateException> UNCHECKER = Unchecker
			.wrappingWith(IllegalStateException::new);

	public static GitCloner create() {
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

	public GitCloner setCheckCommonRefsAgree(boolean checkCommonRefsAgree) {
		this.checkCommonRefsAgree = checkCommonRefsAgree;
		return this;
	}

	public void clone(GitUri gitUri, Repository repo) {
		try (Git git = Git.wrap(repo)) {
			git.fetch().setRemote(gitUri.asString()).setRefSpecs(new RefSpec("+refs/heads/*:refs/heads/*")).call();
			maybeCheckCommonRefs(git);
		} catch (GitAPIException e) {
			throw new IllegalStateException(e);
		}
	}

	public FileRepository download(GitUri uri, Path workTree) {
		return download(uri, workTree, false);
	}

	public FileRepository downloadBare(GitUri uri, Path gitDir) {
		return download(uri, gitDir, true);
	}

	/**
	 * If the given uri contains an empty repository, this returns an empty
	 * repository: repository.getObjectDatabase().exists() is true;
	 * repository.getRefDatabase().hasRefs() is false.
	 *
	 * @param repositoryDirectory GIT_DIR (replacing .git dir) if bare (see
	 *                            {@link FileRepository}), otherwise, work tree dir,
	 *                            in which a .git dir will be created (or exists).
	 * @param allowBare           <code>true</code> to clone bare if not exists (if
	 *                            exists, this method will not check whether it is
	 *                            bare)
	 * @return
	 */
	private FileRepository download(GitUri uri, Path repositoryDirectory, boolean allowBare) {
		localRefs = null;
		remoteRefs = null;
		final FileRepository repository;
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
			try {
				Git git = cloneCmd.call();
				maybeCheckCommonRefs(git);
				repository = (FileRepository) git.getRepository();
			} catch (GitAPIException e) {
				throw new IllegalStateException(e);
			}
		} else {
			try {
				repository = (FileRepository) new FileRepositoryBuilder().setWorkTree(repositoryDirectory.toFile())
						.build();
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			try (Git git = Git.wrap(repository)) {
				final List<RemoteConfig> remoteList = git.remoteList().call();
				final Optional<RemoteConfig> origin = remoteList.stream().filter((r) -> r.getName().equals("origin"))
						.collect(MoreCollectors.toOptional());
				final Status status = git.status().call();
				/**
				 * Creates a problem probably related to https://stackoverflow.com/a/4162672
				 * (oliviercailloux-org/eclipse-LucasLePort)
				 */
				if (!git.getRepository().isBare() && !status.isClean()) {
					throw new IllegalStateException("Can’t update " + uri + ": not clean (" + toString(status) + ").");
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
					throw new IllegalStateException("Unexpected remote: " + remoteList);
				}

				maybeCheckCommonRefs(git);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} catch (GitAPIException e) {
				throw new IllegalStateException(e);
			}
		}
		return repository;
	}

	private void maybeCheckCommonRefs(Git git) throws GitAPIException {
		if (checkCommonRefsAgree) {
			/** Seems like this also includes HEAD when detached. */
			final List<Ref> branches = git.branchList().setListMode(ListMode.ALL).call();
			final List<Ref> allRefs = IO_UNCHECKER.getUsing(() -> git.getRepository().getRefDatabase().getRefs());
			LOGGER.debug("All refs: {}, branches: {}.", allRefs, branches);

			parse(branches);

			final Ref head = IO_UNCHECKER.getUsing(() -> git.getRepository().findRef(Constants.HEAD));
			checkArgument(head != null, "Did you forget to create the repository?");
			checkArgument(head.getTarget().getName().equals("refs/heads/master"));

			final ImmutableMap<String, ObjectId> originRefs = remoteRefs.row("origin");
			final SetView<String> commonRefShortNames = Sets.intersection(originRefs.keySet(), localRefs.keySet());
			final ImmutableSet<String> disagreeingRefShortNames = commonRefShortNames.stream()
					.filter((s) -> !originRefs.get(s).equals(localRefs.get(s))).collect(ImmutableSet.toImmutableSet());
			checkArgument(disagreeingRefShortNames.isEmpty(),
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
			checkArgument(matcher.matches(), fullName);
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
				throw new IllegalArgumentException("Unknown ref kind: " + kind);
			}
		}
		remoteRefs = remoteRefsBuilder.build();
		localRefs = localRefsBuilder.build();
	}

	private String toString(Status status) {
		return MoreObjects.toStringHelper(status).add("Added", status.getAdded()).add("Changed", status.getChanged())
				.add("Conflicting", status.getConflicting()).add("Ignored not in index", status.getIgnoredNotInIndex())
				.add("Missing", status.getMissing()).add("Modified", status.getModified())
				.add("Removed", status.getRemoved()).add("Uncommitted changes", status.getUncommittedChanges())
				.add("Untracked", status.getUntracked()).toString();
	}

}
