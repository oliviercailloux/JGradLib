package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.exceptions.Unchecker.URI_UNCHECKER;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.base.VerifyException;

/**
 * An immutable location of a (local or remote) git repository.
 *
 * <h1>Recommended usage</h1>
 * <ol>
 * <li>Obtain an absolute, hierarchical URI that represents your git
 * repository.</li>
 * <li>Use the {@link #fromUri(URI)} factory method.</li>
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
 * <h1>Obtaining an absolute hierarchical URI</h1>
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
 * <h1>Other possible uses</h1>
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
 * <h1>Rationale</h1>
 *
 * <dl>
 * <dt>Rejecting relative path Git URLs</dt>
 * <dd>Rejecting relative paths permits to ensure that instances of this class
 * can be transformed (without loss of information) to absolute hierarchical
 * URIs (thus with an absolute path), thereby permitting a more systematic
 * usage.</dd>
 * <dt>Accepting no-authority URIs</dt>
 * <dd>According to the Git URL definition, any string starting with
 * <code>file:/</code> not followed by a slash (for example,
 * <code>file:/something/</code>) is a SCP URL designating some path (in this
 * example, <code>/something</code>) on the host named <code>file</code>. The
 * factory method {@link #fromGitUrl(String)} will indeed treat it as an SCP Git
 * URL. However, such a string can also be interpreted, following RFC 2396, as
 * an absolute hierarchical URIs using the file scheme with no authority (in
 * this example, designating the absolute local path <code>/something</code>).
 * The (recommended) {@link #fromUri(URI)} factory method of this class indeed
 * interprets it (IMHO, more reasonably) in such a way. This is the only kind of
 * input that is treated differently than Git URLs. This makes this method
 * accept any hierarchical absolute URI with a scheme known to Git, thereby
 * leveraging the relevant standards and (hopefully) easing usage.</dd>
 * </dl>
 *
 * <h1>Some definitions</h1>
 *
 * To understand precisely the syntaxes accepted by this class, some definitions
 * are useful.
 * <dl>
 * <dt><a href="https://git-scm.com/docs/git-clone#_git_urls">Git URL</a></dt>
 * <dd>A Git URL is a non-empty string that is accepted by the git command line
 * and conforms to the definition given in the official manual. Any Git URL
 * corresponds to exactly one of the following kinds (these definitions are
 * mine).
 * <dl>
 * <dt>Scheme</dt>
 * <dd>A “scheme” Git URL starts with the string “ssh://”, “git://”, “http://”,
 * “https://”, “ftp://” or “ftps://”, followed by at least one non-slash
 * character, followed by a slash character; or starts with the string
 * “file:///”.</dd>
 * <dt>SCP</dt>
 * <dd>A “SCP” Git URL contains a colon, no slash before the first colon, at
 * least one character before the first colon, and does not contain two slashes
 * just after the first colon.</dd>
 * <dt>Absolute path</dt>
 * <dd>An “absolute path” Git URL starts with a slash.</dd>
 * <dt>Relative path</dt>
 * <dd>A “relative path” Git URL does not start with a slash and, if it contains
 * a colon, contains a slash before the first colon.</dd>
 * </ul>
 * </dl>
 * These cases are exclusive, but some string match none of them, for example:
 * the empty string, <code>ssh://</code>, <code>https:///host/</code>,
 * <code>https://host</code>, <code>unknownscheme://host</code>… See
 * {@link GitUrlKind} to detect which kind you face.
 *
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
 * <h1>Comparison</h1>
 *
 * Compared to {@link URIish}, this class is immutable, rejects relative path
 * Git URLs, guarantees convertibility to an absolute hierarchical URI, accepts
 * URIs as input, will always return a scheme when asked for one, provides clear
 * round-trip guarantees (and possibly more differences).
 */
public class GitUri {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitUri.class);

	private static final Pattern SCP_CUT = Pattern.compile("(?<userAtHost>[^:]+):(?<path>.*)");

	private static final Pattern FILE_WITH_NO_AUTHORITY = Pattern.compile("file:/([^/].*|)");

	private static final Pattern FILE_WITH_AUTHORITY = Pattern.compile("file:///.*");

	/**
	 * @return the same uri but with a string form that includes an empty authority
	 *         (file:///) if it is an absolute uri with file scheme whose string
	 *         form includes no authority (file:/ not followed by a slash).
	 */
	private static URI withAuthority(URI uri) {
		if (FILE_WITH_NO_AUTHORITY.matcher(uri.toString()).matches()) {
			return URI.create(uri.toString().replaceFirst("file:/", "file:///"));
		}
		return uri;
	}

	public static GitUri fromUri(URI uri) {
		return new GitUri(withAuthority(uri));
	}

	public static GitUri fromGitUrl(String gitUrl) {
		final URI uri;

		final GitUrlKind kind = GitUrlKind.given(gitUrl);
		switch (kind) {
		case SCHEME:
			uri = withAuthority(URI.create(gitUrl));
			break;
		case SCP:
			final Matcher matcher = SCP_CUT.matcher(gitUrl);
			verify(matcher.matches());
			final String userAtHost = matcher.group("userAtHost");
			verify(!userAtHost.isEmpty());
			verify(!userAtHost.contains("/"));
			final String path = matcher.group("path");
			final String absolutePath = path.startsWith("/") ? path : "/" + path;
			uri = URI_UNCHECKER.getUsing(() -> new URI(GitScheme.SSH.toString(), userAtHost, absolutePath, null, null));
			break;
		case ABSOLUTE_PATH:
			uri = withAuthority(Path.of(gitUrl).toUri());
			break;
		case RELATIVE_PATH:
			throw new IllegalArgumentException("Relative paths not accepted");
		default:
			throw new VerifyException();
		}

		return new GitUri(uri);
	}

	final private URI uri;

	GitUri(URI uri) {
		checkArgument(uri.isAbsolute());
		checkArgument(!uri.isOpaque());
		/** This throws an IllegalArgumentException if the scheme is not permitted. */
		final GitScheme scheme = GitScheme.valueOf(uri.getScheme().toUpperCase());

		if (scheme == GitScheme.FILE) {
			checkArgument(FILE_WITH_AUTHORITY.matcher(uri.toString()).matches());
			checkArgument(Strings.isNullOrEmpty(uri.getAuthority()));
			checkArgument(uri.getUserInfo() == null);
			checkArgument(uri.getHost() == null);
			checkArgument(uri.getPort() == -1);
		} else {
			checkArgument(!Strings.isNullOrEmpty(uri.getAuthority()));
			checkArgument(!Strings.isNullOrEmpty(uri.getHost()));
		}

		verify(!Strings.isNullOrEmpty(uri.getPath()));
		verify(uri.getPath().startsWith("/"));

		this.uri = uri;
	}

	public GitScheme getGitScheme() {
		return GitScheme.valueOf(uri.getScheme().toUpperCase());
	}

	/**
	 * @return is [<code>null</code> or empty] iff the scheme is
	 *         {@link GitScheme#FILE}.
	 */
	public String getAuthority() {
		return uri.getAuthority();
	}

	/**
	 * @return starting with /
	 */
	public String getPath() {
		return uri.getPath();
	}

	/**
	 * Returns this location as an absolute hierarchical URI with a scheme known to
	 * Git and a string form that includes an authority component. The authority is
	 * empty iff the scheme is {@link GitScheme#FILE}. It is guaranteed that giving
	 * the returned uri back to {@link #fromUri(URI)} will yield an instance
	 * equivalent to this one. There is no guarantee that the returned
	 * representation is the same as the one that was given when creating this
	 * instance.
	 *
	 * @return this location as a uri.
	 */
	public URI asUri() {
		return uri;
	}

	/**
	 * Returns this location as a string representing an absolute hierarchical URI
	 * with a scheme known to Git and an authority component. The authority is empty
	 * iff the scheme is {@link GitScheme#FILE}. It is guaranteed that giving this
	 * string back to {@link #fromGitUrl(String)} will yield an instance equivalent
	 * to this one. There is no guarantee that the returned representation is the
	 * same as the one that was given when creating this instance.
	 *
	 * @return this location as a string.
	 */
	public String asString() {
		return uri.toString();
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GitUri)) {
			return false;
		}
		final GitUri g2 = (GitUri) o2;
		return uri.equals(g2.uri);
	}

	@Override
	public int hashCode() {
		return Objects.hash(uri);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Uri", uri).toString();
	}
}
