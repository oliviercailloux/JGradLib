package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;

import com.google.common.base.Verify;

import io.github.oliviercailloux.git.GitUri;

class GitFileSystemProviderTests {
	public static void main(String[] args) throws Exception {
		final GitFileSystemProvider provider = new GitFileSystemProvider();
		provider.setUpdate(true);
		provider.newFileSystem(GitUri.fromGitUri(URI.create("https://github.com/oliviercailloux/testrel/")),
				Path.of("testrel cloned using https")).close();
		final Path sshPath = Path.of("testrel cloned using ssh");
		provider.newFileSystem(GitUri.fromGitUri(URI.create("ssh:git@github.com:oliviercailloux/testrel.git")),
				sshPath).close();
		provider.setUpdate(true);
		provider.newFileSystem(GitUri.fromGitUri(sshPath.toUri()),
				Path.of("testrel cloned using file transport to ssh clone")).close();
		Files.writeString(sshPath.resolve("newfile.txt"), "newcontent");
		try (Repository repo = new FileRepository(sshPath.resolve(".git").toFile())) {
			try (Git git = new Git(repo)) {
				git.add().addFilepattern("newfile.txt").call();
				final CommitCommand commit = git.commit();
				commit.setCommitter(new PersonIdent("Me", "email"));
				commit.setMessage("New commit");
				final RevCommit newCommit = commit.call();
				final Ref master = repo.exactRef("refs/heads/master");
				Verify.verify(master.getObjectId().equals(newCommit));
			}
		}
		provider.newFileSystem(GitUri.fromGitUri(sshPath.toUri()),
				Path.of("testrel cloned using file transport to ssh clone")).close();
		provider.newFileSystem(GitUri.fromGitUri(sshPath.toUri()), sshPath).close();
	}

	@Test
	void test() {
		fail("Not yet implemented");
	}

}
