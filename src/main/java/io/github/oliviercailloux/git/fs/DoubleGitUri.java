package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jgit.transport.URIish;

import com.diffplug.common.base.Predicates;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.MoreCollectors;

import io.github.oliviercailloux.utils.Utils;

public class DoubleGitUri {
	public static final String GIT_SCHEME_QUERY_PARAMETER = "git-scheme";
	private GitScheme gitScheme;
	private String userInfo;
	private String repoHost;
	private int repoPort;
	private String repoPath;
	private String repoName;

	public static DoubleGitUri fromGitUri(URI gitUri) {
		return new DoubleGitUri(GitScheme.valueOf(gitUri.getScheme().toUpperCase()),
				Strings.nullToEmpty(gitUri.getUserInfo()), Strings.nullToEmpty(gitUri.getHost()), gitUri.getPort(),
				gitUri.getPath());
	}

	public static DoubleGitUri fromGitFsUri(URI gitFsUri) {
		checkArgument(gitFsUri.getScheme().equalsIgnoreCase(GitFileSystemProvider.SCHEME));
		checkArgument(gitFsUri.isAbsolute());
		checkArgument(gitFsUri.getPath() != null);
		checkArgument(gitFsUri.getPath().startsWith("/"));

		final ImmutableList<String> gitSchemes = Utils.getQuery(gitFsUri).getOrDefault(GIT_SCHEME_QUERY_PARAMETER,
				ImmutableList.of());
		checkArgument(gitSchemes.size() <= 1);
		final Optional<String> gitSchemeStrOpt = gitSchemes.stream().collect(MoreCollectors.toOptional());
		checkArgument(!gitSchemeStrOpt.isPresent() || Arrays.asList(GitScheme.values()).stream()
				.map(GitScheme::toString).anyMatch(Predicates.equalTo(gitSchemeStrOpt.get().toUpperCase())));
		final Optional<GitScheme> gitSchemeOpt = gitSchemeStrOpt.map((s) -> GitScheme.valueOf(s.toUpperCase()));

		/**
		 * Scheme must be present, except that it is optional with file kind (no host)
		 * and with ssh kind (with user info).
		 */
		checkArgument(
				Utils.implies(gitFsUri.getHost() != null && gitFsUri.getUserInfo() == null, gitSchemeOpt.isPresent()));
		checkArgument(Utils.implies(gitSchemeOpt.isPresent() && gitSchemeOpt.get().equals(GitScheme.FILE),
				gitFsUri.getHost() == null));
		checkArgument(Utils.implies(gitSchemeOpt.isPresent() && !gitSchemeOpt.get().equals(GitScheme.FILE),
				gitFsUri.getHost() != null));
		checkArgument(Utils.implies(gitSchemeOpt.isPresent() && gitFsUri.getUserInfo() != null,
				gitSchemeOpt.equals(Optional.of(GitScheme.SSH))));

		final GitScheme gitScheme;
		if (gitFsUri.getUserInfo() != null) {
			gitScheme = GitScheme.SSH;
		} else if (gitFsUri.getHost() == null) {
			gitScheme = GitScheme.FILE;
		} else {
			gitScheme = gitSchemeOpt.get();
		}
		return new DoubleGitUri(gitScheme, Strings.nullToEmpty(gitFsUri.getUserInfo()),
				Strings.nullToEmpty(gitFsUri.getHost()), gitFsUri.getPort(), gitFsUri.getPath());
	}

	public DoubleGitUri(GitScheme gitScheme, String userInfo, String host, int port, String repoPath) {
		this.gitScheme = checkNotNull(gitScheme);
		this.userInfo = checkNotNull(userInfo);
		this.repoHost = checkNotNull(host);
		this.repoPort = port;
		this.repoPath = checkNotNull(repoPath);
		checkArgument(userInfo.isEmpty() || gitScheme == GitScheme.SSH);
		checkArgument(port == -1 || port >= 1);
		try {
			repoName = new URIish(gitScheme + "://" + repoPath).getHumanishName();
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public GitScheme getGitScheme() {
		return gitScheme;
	}

	public String getUserInfo() {
		return userInfo;
	}

	public String getHost() {
		return repoHost;
	}

	public int getPort() {
		return repoPort;
	}

	public String getRepositoryPath() {
		return repoPath;
	}

	public String getRepositoryName() {
		return repoName;
	}

	public URI getGitFsUri() {
		try {
			return new URI(GitFileSystemProvider.SCHEME, Strings.emptyToNull(userInfo), Strings.emptyToNull(repoHost),
					repoPort, Strings.emptyToNull(repoPath),
					GIT_SCHEME_QUERY_PARAMETER + "=" + gitScheme.toString().toLowerCase(), null);
		} catch (URISyntaxException e) {
			throw new AssertionError(e);
		}
	}

	public URI getGitUri() {
		try {
			return new URI(gitScheme.toString().toLowerCase(), Strings.emptyToNull(userInfo),
					Strings.emptyToNull(repoHost), repoPort, Strings.emptyToNull(repoPath), null, null);
		} catch (URISyntaxException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof DoubleGitUri)) {
			return false;
		}
		final DoubleGitUri d2 = (DoubleGitUri) o2;
		return gitScheme.equals(d2.gitScheme) && userInfo.equals(d2.userInfo) && repoHost.equals(d2.repoHost)
				&& repoPort == d2.repoPort && repoPath.equals(d2.repoPath);
	}

	@Override
	public int hashCode() {
		return Objects.hash(gitScheme, userInfo, repoHost, repoPort, repoPath);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Git scheme", gitScheme).add("User info", userInfo)
				.add("Host", repoHost).add("Port", repoPort).add("Repository path", repoPath)
				.add("Repository name", repoName).toString();
	}
}
