package io.github.oliviercailloux.git.git_hub.model;

import static com.google.common.base.Preconditions.checkNotNull;

import jakarta.json.JsonObject;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * When assignments get created using GitHub Classroom, they have the organization as owner and a
 * repository name formed by the name of the assignment (called here prefix) followed by a hyphen
 * followed by the GitHub username of the student accepting the assignment. For example:
 * oliviercailloux-org:dep-git-astudentname. Note that the prefix can’t be detected in general
 * because both a prefix and a username can contain hyphens.
 *
 */
public class RepositoryCoordinatesWithPrefix extends RepositoryCoordinates {
	public static Optional<String> getUsername(String prefix, String repositoryName) {
		final Pattern pattern = Pattern.compile(prefix + "-(.*)");
		final Matcher matcher = pattern.matcher(repositoryName);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return Optional.of(matcher.group(1));
	}

	public static RepositoryCoordinatesWithPrefix from(String owner, String prefix, String username) {
		return new RepositoryCoordinatesWithPrefix(owner, prefix, username);
	}

	public static RepositoryCoordinatesWithPrefix from(RepositoryCoordinates coordinates,
			String prefix) {
		final String username = getUsername(prefix, coordinates.getRepositoryName())
				.orElseThrow(IllegalArgumentException::new);
		return new RepositoryCoordinatesWithPrefix(coordinates.getOwner(), prefix, username);
	}

	public static RepositoryCoordinatesWithPrefix from(JsonObject json, String prefix) {
		return from(RepositoryCoordinates.from(json), prefix);
	}

	private final String prefix;
	private final String username;

	private RepositoryCoordinatesWithPrefix(String org, String prefix, String username) {
		super(org, prefix + "-" + username);
		this.prefix = checkNotNull(prefix);
		this.username = checkNotNull(username);
	}

	public String getPrefix() {
		return prefix;
	}

	public String getUsername() {
		return username;
	}
}
