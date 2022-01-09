package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.primitives.Booleans;
import java.util.regex.Pattern;

public enum GitUrlKind {
	SCHEME, SCP, ABSOLUTE_PATH, RELATIVE_PATH;

	private static final Pattern SCHEME_PATTERN = Pattern.compile("((ssh|git|https?|ftps?)://[^/]+/.*)|(file:///.*)");
	private static final Pattern SCP_PATTERN = Pattern.compile("[^/:]+:/?([^/].*)?");
	private static final Pattern ABSOLUTE_PATH_PATTERN = Pattern.compile("/.*");
	private static final Pattern RELATIVE_PATH_PATTERN = Pattern.compile("[^/:](([^/:]*/[^:]*:.*)|[^:]*)");

	public static GitUrlKind given(String gitUrl) {
		checkArgument(!gitUrl.isEmpty());
		final boolean scheme = SCHEME_PATTERN.asMatchPredicate().test(gitUrl);
		final boolean scp = SCP_PATTERN.asMatchPredicate().test(gitUrl);
		final boolean absolutePath = ABSOLUTE_PATH_PATTERN.asMatchPredicate().test(gitUrl);
		final boolean relativePath = RELATIVE_PATH_PATTERN.asMatchPredicate().test(gitUrl);
		final int match = Booleans.countTrue(scheme, scp, absolutePath, relativePath);
		verify(match <= 1, String.format("Match: %b, %b, %b, %b.", scheme, scp, absolutePath, relativePath));
		checkArgument(match == 1);
		return scheme ? SCHEME : (scp ? SCP : (absolutePath ? ABSOLUTE_PATH : RELATIVE_PATH));
	}
}