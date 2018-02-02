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

import io.github.oliviercailloux.st_projects.model.ProjectOnGitHub;

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

	public void clone(ProjectOnGitHub project) throws GitAPIException {
		final File projectDirFile = getProjectDirectoryAsFile(project);
		final CloneCommand cloneCmd = Git.cloneRepository();
		LOGGER.info("Cloning {}.", project.getName());
		cloneCmd.setURI(project.getSshURLString());
		cloneCmd.setDirectory(projectDirFile);
		cloneCmd.call();
	}

	public void retrieve(ProjectOnGitHub project) throws GitAPIException, IOException {
		final String name = project.getName();
		final Path outputProjectDir = outputBaseDir.resolve(name);
		if (Files.exists(outputProjectDir)) {
			update(project);
		} else {
			clone(project);
		}
	}

	public void update(ProjectOnGitHub project) throws GitAPIException, IOException {
		try (Git repo = Git.open(getProjectDirectoryAsFile(project))) {
			LOGGER.info("Updating {}.", project.getName());
			repo.fetch().call();

//			final Ref masterRef = repo.getRepository().exactRef("refs/heads/master");
//			assert masterRef != null : repo.branchList().call();
//			final RevCommit masterCommit = repo.getRepository().parseCommit(masterRef.getObjectId());
//			assert masterCommit != null : masterRef.getObjectId();
//			final CheckoutCommand checkoutCmd = repo.checkout();
//			checkoutCmd.setStartPoint(masterCommit).call();
			repo.checkout().setName("master").call();

			final Ref originMasterRef = repo.getRepository().exactRef("refs/remotes/origin/master");
			assert originMasterRef != null : repo.branchList().setListMode(ListMode.REMOTE).call();
			final MergeResult res = repo.merge().include(originMasterRef).call();
			assert res.getMergeStatus() == MergeStatus.ALREADY_UP_TO_DATE
					|| res.getMergeStatus() == MergeStatus.MERGED : res.getMergeStatus();
		}
	}

	private File getProjectDirectoryAsFile(ProjectOnGitHub project) {
		final String name = project.getName();
		final Path outputProjectDir = outputBaseDir.resolve(name);
		final File projectDirFile = outputProjectDir.toFile();
		return projectDirFile;
	}
}
