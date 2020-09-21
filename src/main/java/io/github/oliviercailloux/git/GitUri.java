package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.base.Verify;
import com.google.common.base.VerifyException;
import com.google.common.primitives.Booleans;

import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitScheme;
import io.github.oliviercailloux.utils.Utils;

/**
 * A location of a (local or remote) git repository.
 *
 * <h2>Recommended usage</h2>
 * <ol>
 * <li>Obtain an absolute, hierarchical URI that represents your git
 * repository.</li>
 * <li>Use the {@link #fromGitUri(URI)} factory method.</li>
 * </ol>
 * Typical examples:
 * <ul>
 * <li><code>https://github.com/oliviercailloux/JARiS.git</code>,</li>
 * <li><code>ssh://git@github.com/oliviercailloux/JARiS.git</code>,</li>
 * <li><code>file:/some/absolute/path/</code> (with no authority), or
 * <code>file:///some/absolute/path/</code> (with an empty authority).</li>
 * </ul>
 * This is (IMHO) the cleanest usage as it relies on a RFC compliant URI which
 * is furthermore also a valid Git URL (except for the version with no
 * authority). You can obtain a URI to a local path with {@link Path#toUri()}.
 *
 * <h2>Obtaining an absolute hierarchical URI</h2>
 *
 * Any instance created by this class represents a Git repository location that
 * can be converted to an absolute hierarchical URI, even if the instance was
 * not created using such a URI.
 *
 * Examples:
 * <ul>
 * <li>creating an instance using the SCP Git URL
 * <code>git@github.com:oliviercailloux/JARiS.git</code> results in the
 * <code>ssh://git@github.com/oliviercailloux/JARiS.git</code> absolute
 * hierarchical URI;</li>
 * <li>creating an instance using the absolute path <code>/absolute/path</code>
 * results either in the <code>file:/absolute/path</code> or in the
 * <code>file:///absolute/path</code> absolute hierarchical URI.</li>
 * </ul>
 *
 * <h2>Other possible uses</h2>
 *
 * You may create an instance on the basis of:
 * <ul>
 * <li>any Git URL except for “relative path” Git URLs (thus GitHub-popular URLs
 * such as <code>git@github.com:oliviercailloux/JARiS.git</code> are accepted),
 * or</li>
 * <li>any absolute hierarchical URI using a {@link GitScheme scheme} known to
 * Git (see examples above).</li>
 * </ul>
 *
 * <h2>Rationale</h2>
 *
 * <dl>
 * <dt>Rejecting relative path Git URLs</dt>
 * <dd>According to the Git URL definition, the string “file:/example/” is a
 * relative path designating the sub-folder named “example” situated in the
 * folder named “file:” (itself situated in the current folder). This is calling
 * for trouble as it is also a valid URI designating the absolute path
 * “/example”. Rejecting relative paths avoids this possible confusion. Also,
 * rejecting relative paths permits to ensure that instances of this class can
 * be transformed (without loss of information) to absolute hierarchical URIs
 * (thus with an absolute path).</dd>
 * <dt>Accepting no-authority URIs</dt>
 * <dd>This class accepts a string such as “file:/example/” and interprets it as
 * an absolute hierarchical URIs using the file scheme with no authority
 * (following RFC 2396). This is the only kind of input that is treated
 * differently than Git URLs. The reason for not treating it like a relative
 * path Git URL is indicated here above. The reason to accept it anyway and
 * treat it differently is that this makes this class accept any hierarchical
 * absolute URI using a scheme known to Git, thereby (hopefully) easing usage
 * and leveraging the relevant standards.</dd>
 * </dl>
 *
 * <h2>Some definitions</h2>
 *
 * To understand precisely the syntaxes accepted by this class, some definitions
 * are useful.
 * <dl>
 * <dt><a href="https://git-scm.com/docs/git-clone#_git_urls">Git URL</a></dt>
 * <dd>A Git URL is a string that represents a (local or remote) git repository
 * and that is accepted by the git command line, as defined in the official
 * manual. Any Git URL corresponds to exactly one of following kinds.
 * <dl>
 * <dt>Scheme</dt>
 * <dd>A “scheme” Git URL starts with the string “ssh://”, “git://”, “http://”,
 * “https://”, “ftp://”, “ftps://” or “file://”.</dd>
 * <dt>SCP</dt>
 * <dd>A “SCP” Git URL contains a colon and no slash before the first
 * colon.</dd>
 * <dt>Absolute path</dt>
 * <dd>An “absolute path” Git URL starts with a slash.</dd>
 * <dt>Relative path</dt>
 * <dd>A “relative path” Git URL does not start with a slash and, if it contains
 * a colon, contains a slash before the first colon.</dd>
 * </ul>
 * </dl>
 * <dt>Java {@link URI}</dt>
 * <dd>A URI as per the Java-interpretation of RFC 2396 (including the
 * interpretation that an empty authority is permitted). This class is
 * particularly interested in absolute, hierarchical URIs. Two sub-cases are
 * relevant.
 * <dl>
 * <dt>No authority</dt>
 * <dd>An absolute hierarchical URI with no authority has the form
 * <code><i>scheme</i>:/<i>xyz</i>[?<i>query</i>][#<i>fragment</i>]</code>,
 * where <code><i>xyz</i></code> may not start with a slash.</dd>
 * <dt>With a (possibly empty) authority</dt>
 * <dd>An absolute hierarchical URI with a (possibly empty) authority has the
 * form
 * <code><i>scheme</i>://<i>authority</i>/<i>xyz</i>[?<i>query</i>][#<i>fragment</i>]</code>.</dd>
 * In both cases, the path is <code>/<i>xyz</i></code>. Note that the
 * <code><i>xyz</i></code> part may be empty, but the path may not: the RFC
 * mandates that an absolute hierarchical URI has an absolute path.</dd>
 * </dl>
 *
 */
public class GitUri {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitUri.class);

	public static GitUri fromGitUri(URI gitUri) {
		checkArgument(gitUri.isAbsolute());
		checkArgument(!gitUri.isOpaque());
		final String user;
		final String host;
		final int port;
		final String path;
		user = Strings.nullToEmpty(gitUri.getUserInfo());
		host = Strings.nullToEmpty(gitUri.getHost());
		port = gitUri.getPort();
		path = gitUri.getPath();
		assert path != null;
		verify(path.startsWith("/"));
		return new GitUri(GitScheme.valueOf(gitUri.getScheme().toUpperCase()), user, host, port, path);
	}

	private static enum GitUrlKind {
		SCHEME, SCP, PATH;

		private static final Pattern SCHEME_PATTERN = Pattern.compile("(ssh|git|https?|ftps?|file)://.*");
		private static final Pattern SCP_PATTERN = Pattern.compile("[^/]*:/?[^/].*");
		private static final Pattern ABSOLUTE_PATH_PATTERN = Pattern.compile("/.*");

		public static GitUrlKind given(String gitUrl) {
			final boolean scheme = SCHEME_PATTERN.asMatchPredicate().test(gitUrl);
			final boolean scp = SCP_PATTERN.asMatchPredicate().test(gitUrl);
			final boolean path = ABSOLUTE_PATH_PATTERN.asMatchPredicate().test(gitUrl);
			final int match = Booleans.countTrue(scheme, scp, path);
			verify(match <= 1);
			checkArgument(match == 1);
			return scheme ? SCHEME : (scp ? SCP : PATH);
		}
	}

	public static GitUri fromGitUrl(String gitUrl) {
		final GitUri uri;

		final GitUrlKind kind = GitUrlKind.given(gitUrl);
		switch (kind) {
		case SCHEME:
			uri = fromGitUri(URI.create(gitUrl));
			break;
		case SCP:
			URIish uriish;
			try {
				uriish = new URIish(gitUrl);
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException(e);
			}
			checkArgument(uriish.getPass().isEmpty(), "Password not supported yet.");
			uri = new GitUri(GitScheme.SSH, Strings.nullToEmpty(uriish.getUser()),
					Strings.nullToEmpty(uriish.getHost()), uriish.getPort(), "/" + uriish.getPath());
			break;
		case PATH:
			uri = new GitUri(GitScheme.FILE, "", "", -1, gitUrl);
			break;
		default:
			throw new VerifyException();
		}

		return uri;
	}

	private final GitScheme gitScheme;
	private final String userInfo;
	private final String repoHost;
	private final int repoPort;
	private final String repoPath;
	private final String repoName;

	GitUri(GitScheme gitScheme, String userInfo, String host, int port, String repoPath) {
		/** This can probably be very much simplified. */
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
