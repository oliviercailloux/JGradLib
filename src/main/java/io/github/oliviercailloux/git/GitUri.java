package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.base.Verify;

import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.utils.Utils;

/**
 * This class rejects relative file urls such as ./foobar, because this can’t be
 * translated to an absolute hierarchical url: an absolute url that is
 * hierarchical has an absolute path, see URI.
 *
 * This accepts the scp-like syntax with path starting with :, but converts it
 * to a valid URL using the (git standard) ssh:// format.
 *
 * Note that if ssh://user@stuff/~user, the name is ~user. (To test.)
 **
 * The only git standard “URL” (https://git-scm.com/docs/git-clone#_git_urls)
 * that is not accepted as a Java URI (because it is not an RFC valid URI) is
 * the SCP format, but it can be converted to an absolute hierarchical URI.
 * Also, relative file paths are rejected (although they are acceptable “git
 * URLs” per git doc). Thus, this object always represents an absolute
 * hierarchical URI.
 */
public class GitUri {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitUri.class);

	public static GitUri fromGitUri(URI gitUri) {
		final String user;
		final String host;
		final int port;
		final String path;
		user = Strings.nullToEmpty(gitUri.getUserInfo());
		host = Strings.nullToEmpty(gitUri.getHost());
		port = gitUri.getPort();
		path = Strings.nullToEmpty(gitUri.getPath());
		return new GitUri(GitScheme.valueOf(gitUri.getScheme().toUpperCase()), user, host, port, path);
	}

	public static GitUri fromGitUrl(String gitUrl) {
		final boolean scpLikeUrl;
		final boolean localPathUrl;
		if (gitUrl.contains(":")) {
			final int colonIndex = gitUrl.indexOf(":");
			final String beforeColon = gitUrl.substring(0, colonIndex);
			final boolean schemeBeforeColon = beforeColon.matches("ssh|git|https?|ftps?|file");
			scpLikeUrl = !schemeBeforeColon && !beforeColon.contains("/");
			localPathUrl = !schemeBeforeColon && beforeColon.contains("/");
		} else {
			scpLikeUrl = false;
			localPathUrl = true;
		}

		final GitUri uris;
		if (!scpLikeUrl && !localPathUrl) {
			uris = fromGitUri(URI.create(gitUrl));
		} else if (scpLikeUrl) {
			URIish uriish;
			try {
				uriish = new URIish(gitUrl);
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException(e);
			}
			uris = new GitUri(GitScheme.SSH, Strings.nullToEmpty(uriish.getUser()),
					Strings.nullToEmpty(uriish.getHost()), uriish.getPort(), uriish.getPath());
		} else {
			verify(localPathUrl);
			uris = new GitUri(GitScheme.FILE, "", "", -1, gitUrl);
		}

		return uris;
	}

	private final GitScheme gitScheme;
	private final String userInfo;
	private final String repoHost;
	private final int repoPort;
	private final String repoPath;
	private final String repoName;

	GitUri(GitScheme gitScheme, String userInfo, String host, int port, String repoPath) {
		this.gitScheme = checkNotNull(gitScheme);
		this.userInfo = checkNotNull(userInfo);
		this.repoHost = checkNotNull(host);
		this.repoPort = port;
		checkNotNull(repoPath);
		checkArgument((gitScheme == GitScheme.SSH) == !userInfo.isEmpty());
		checkArgument(!userInfo.startsWith("/"));
		checkArgument(port == -1 || port >= 1);
		checkArgument((gitScheme == GitScheme.FILE) == host.isEmpty());
		checkArgument(Utils.implies(gitScheme == GitScheme.FILE, port == -1));
		checkArgument(!host.endsWith("/"));
		checkArgument(Utils.implies(gitScheme != GitScheme.SSH, repoPath.startsWith("/")));
		checkArgument(Utils.implies(gitScheme == GitScheme.SSH, repoPath.length() >= 1),
				"Repository path can’t be empty.");
		if (gitScheme == GitScheme.SSH) {
			this.repoPath = repoPath;
		} else {
			try {
				/** URI accepts only an absolute path in an absolute URI. */
				this.repoPath = new URI(GitFileSystemProvider.SCHEME, null, repoPath, null).normalize().getPath();
				Verify.verify(repoPath.startsWith("/"));
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException(e);
			}
			/**
			 * Check must be after normalization: /./, for example, would be normalized to a
			 * single character.
			 */
			checkArgument(repoPath.length() >= 2, "Repository path can’t be just '/'.");
		}
		try {
			repoName = new URIish(gitScheme + "://" + host + repoPath).getHumanishName();
			Verify.verify(!repoName.isEmpty());
			Verify.verify(!repoName.endsWith("/"));
			LOGGER.debug("Repo name: {}.", repoName);
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

	/**
	 * Do not use with gitScheme being FILE: git expects "file://" then path
	 * (starting with /), whereas a URI contains "file:" then path (starting with
	 * slash). TODO check doc Java URI, accepts file:///.
	 */
	public URI getGitHierarchicalUri() {
		checkState(gitScheme != GitScheme.FILE);
		final String absolutePath;
		if (!repoPath.startsWith("/")) {
			Verify.verify(gitScheme == GitScheme.SSH);
			Verify.verify(!userInfo.isEmpty());
			Verify.verify(!repoPath.isEmpty());
			absolutePath = "/" + repoPath;
		} else {
			absolutePath = repoPath;
		}
		try {
			return new URI(gitScheme.toString().toLowerCase(), Strings.emptyToNull(userInfo),
					Strings.emptyToNull(repoHost), repoPort, absolutePath, null, null);
		} catch (URISyntaxException e) {
			throw new AssertionError(e);
		}
	}

	public String getGitString() {
		if (gitScheme == GitScheme.FILE) {
			return "file://" + repoPath;
		}
		if (!repoPath.startsWith("/")) {
			Verify.verify(gitScheme == GitScheme.SSH);
			Verify.verify(!userInfo.isEmpty());
			Verify.verify(!repoPath.isEmpty());
			return userInfo + "@" + repoHost + ":" + repoPath;
		}
		return getGitHierarchicalUri().toString();
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GitUri)) {
			return false;
		}
		final GitUri d2 = (GitUri) o2;
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
