package io.github.oliviercailloux.java_grade.utils;

import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.mycourse.json.JsonStudentOnGitHubKnown;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;

public class HarvestIds {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(HarvestIds.class);

	public static Optional<Integer> getId(GitFileSystem fs) {
		final GitPath idPath = fs.getRelativePath("id.txt");
		if (!Files.exists(idPath)) {
			return Optional.empty();
		}
		final List<String> lines = IO_UNCHECKER.getUsing(() -> Files.readAllLines(idPath));
		final ImmutableList<String> nonEmptyLines = lines.stream().filter(l -> !l.isBlank())
				.collect(ImmutableList.toImmutableList());
		LOGGER.info("File content: {}.", nonEmptyLines);
		if (nonEmptyLines.size() != 1) {
			return Optional.empty();
		}
		final String id = nonEmptyLines.get(0);
		try {
			return Optional.of(Integer.parseInt(id.strip()));
		} catch (@SuppressWarnings("unused") NumberFormatException e) {
			return Optional.empty();
		}
	}

	public static void main(String[] args) throws Exception {
		new HarvestIds().proceed();
	}

	private final String prefix;
	private final Pattern pattern;

	public HarvestIds() {
		prefix = "commit";
		pattern = Pattern.compile(prefix + "-(.*)");
	}

	public void proceed() throws IOException {
		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", prefix);
		}
		final ImmutableList<RepositoryCoordinatesWithPrefix> effectiveRepositories = repositories;
		final ImmutableList<RepositoryCoordinatesWithPrefix> matching = effectiveRepositories.stream()
				.filter(r -> pattern.matcher(r.getRepositoryName()).matches()).collect(ImmutableList.toImmutableList());

		final ImmutableMap<RepositoryCoordinatesWithPrefix, Optional<Integer>> idsOpt = matching.stream()
				.collect(ImmutableMap.toImmutableMap(Function.identity(), this::getId));

		final ImmutableSet<RepositoryCoordinatesWithPrefix> missing = idsOpt.entrySet().stream()
				.filter(e -> e.getValue().isEmpty()).map(Entry::getKey).collect(ImmutableSet.toImmutableSet());
		LOGGER.warn("Missing: {}.", missing);

		final ImmutableMap<RepositoryCoordinatesWithPrefix, Integer> ids = idsOpt.entrySet().stream()
				.filter(e -> e.getValue().isPresent())
				.collect(ImmutableMap.toImmutableMap(Entry::getKey, e -> e.getValue().get()));

		final ImmutableMap<String, Integer> idsByGitHubUsername = ids.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(e -> getGitHubName(e.getKey()), Entry::getValue));

		final PrintableJsonObject asJson = JsonbUtils.toJsonObject(idsByGitHubUsername,
				JsonStudentOnGitHubKnown.asAdapter());
		Files.writeString(Path.of("gh-id.json"), asJson.toString());
	}

	private Optional<Integer> getId(RepositoryCoordinates coord) {
		LOGGER.info("Proceeding with {}.", coord);
		final Path projectsBaseDir = Paths.get("../../Java L3/En cours").resolve(prefix);
		final Path projectDir = projectsBaseDir.resolve(coord.getRepositoryName());
		new GitCloner().download(GitUri.fromUri(coord.asURI()), projectDir).close();
		try (GitFileSystem fs = IO_UNCHECKER.getUsing(
				() -> GitFileSystemProvider.getInstance().newFileSystemFromGitDir(projectDir.resolve(".git")))) {
			return getId(fs);
		}
	}

	private String getGitHubName(RepositoryCoordinates coord) {
		final Matcher matcher = pattern.matcher(coord.getRepositoryName());
		matcher.matches();
		return matcher.group(1);
	}
}
