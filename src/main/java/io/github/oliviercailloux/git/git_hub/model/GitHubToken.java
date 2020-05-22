package io.github.oliviercailloux.git.git_hub.model;

import static com.google.common.base.Preconditions.checkState;
import static io.github.oliviercailloux.exceptions.Unchecker.IO_UNCHECKER;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.HttpHeaders;

public class GitHubToken {
	private static GitHubRealToken instance = null;
	private static GitHubToken none = null;
	private String token;

	public static GitHubRealToken getRealInstance() throws IllegalStateException {
		if (instance == null) {
			instance = new GitHubRealToken(IO_UNCHECKER.getUsing(GitHubToken::getTokenValue));
		}
		return instance;
	}

	public static GitHubRealToken getExistingRealInstance() throws IllegalStateException {
		checkState(instance != null);
		return instance;
	}

	public static GitHubToken getNone() {
		if (none == null) {
			none = new GitHubToken("");
		}
		return none;
	}

	protected GitHubToken(String token) {
		this.token = requireNonNull(token);
	}

	public String getToken() {
		return token;
	}

	public void addToRequest(Invocation.Builder request) {
		if (token != "") {
			request.header(HttpHeaders.AUTHORIZATION, String.format("token %s", token));
		}
	}

	private static String getTokenValue() throws IOException, IllegalStateException {
		final Optional<String> tokenOpt = getTokenOpt();
		return tokenOpt
				.orElseThrow(() -> new IllegalStateException("No token found in environment, in property or in file."));
	}

	private static Optional<String> getTokenOpt() throws IOException {
		{
			final String token = System.getenv("token_github_projects_review");
			if (token != null) {
				return Optional.of(token);
			}
		}
		{
			final String token = System.getProperty("token_github_projects_review");
			if (token != null) {
				return Optional.of(token);
			}
		}
		final Path path = Paths.get("token_github_projects_review.txt");
		if (!Files.exists(path)) {
			return Optional.empty();
		}
		final String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		return Optional.of(content.replaceAll("\n", ""));
	}
}
