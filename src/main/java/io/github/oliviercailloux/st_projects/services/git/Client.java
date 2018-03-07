package io.github.oliviercailloux.st_projects.services.git;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git_hub.low.Repository;

public class Client {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

	private Path outputBaseDir;

	public Client() {
		outputBaseDir = Paths.get("/home/olivier/Professions/Enseignement/Projets-ét");
	}

	public void checkout(String commitName) throws IOException, GitAPIException {
		/**
		 * 1. Find date issue closed.
		 *
		 * 2. Find last commit before that date according to git.
		 *
		 * 3. checkout that one.
		 */
		final Path dir = Paths.get("/home/olivier/Local/test-pdf-number-pages/");
		try (Git git = Git.open(dir.toFile())) {
			LOGGER.info("Work dir: {}.", git.getRepository().getWorkTree());
			final List<Ref> br = git.branchList().call();
			LOGGER.info("Branches: {}.", br);
			git.checkout().setName("master").call();
			git.checkout().setName(commitName).call();
		}
	}

	public void clone(Repository repo) throws GitAPIException {
		final File projectDirFile = getProjectDirectoryAsFile(repo);
		final CloneCommand cloneCmd = Git.cloneRepository();
		LOGGER.info("Cloning {}.", repo.getName());
		cloneCmd.setURI(repo.getSshURLString());
		cloneCmd.setDirectory(projectDirFile);
		cloneCmd.call();
	}

	public void retrieve(Repository repo) throws GitAPIException, IOException, IllegalStateException {
		final String name = repo.getName();
		final Path outputProjectDir = outputBaseDir.resolve(name);
		if (Files.exists(outputProjectDir)) {
			update(repo);
		} else {
			clone(repo);
		}
	}

	public void update(Repository repo) throws GitAPIException, IOException, IllegalStateException {
		try (Git git = Git.open(getProjectDirectoryAsFile(repo))) {
			LOGGER.info("Updating {}.", repo.getName());
			git.fetch().call();

//			final Ref masterRef = repo.getRepository().exactRef("refs/heads/master");
//			assert masterRef != null : repo.branchList().call();
//			final RevCommit masterCommit = repo.getRepository().parseCommit(masterRef.getObjectId());
//			assert masterCommit != null : masterRef.getObjectId();
//			final CheckoutCommand checkoutCmd = repo.checkout();
//			checkoutCmd.setStartPoint(masterCommit).call();
			git.checkout().setName("master").call();

			final Ref originMasterRef = git.getRepository().exactRef("refs/remotes/origin/master");
			assert originMasterRef != null : git.branchList().setListMode(ListMode.REMOTE).call();
			final MergeResult res = git.merge().include(originMasterRef).call();
			final boolean rightState = res.getMergeStatus() == MergeStatus.ALREADY_UP_TO_DATE
					|| res.getMergeStatus() == MergeStatus.MERGED || res.getMergeStatus() == MergeStatus.FAST_FORWARD;
			if (!rightState) {
				throw new IllegalStateException("Illegal merge result: " + res.getMergeStatus());
			}
		}
	}

	private File getProjectDirectoryAsFile(Repository repo) {
		final String name = repo.getName();
		final Path outputProjectDir = outputBaseDir.resolve(name);
		final File projectDirFile = outputProjectDir.toFile();
		return projectDirFile;
	}
}
