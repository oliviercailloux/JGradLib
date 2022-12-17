package io.github.oliviercailloux.java_grade.utils;

import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemImpl;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemProviderImpl;
import io.github.oliviercailloux.grade.comm.json.JsonStudentOnGitHubKnown;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.utils.Utils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HarvestIds {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(HarvestIds.class);

	public static Optional<Integer> getId(GitFileSystemImpl fs) throws IOException {
		final Pattern digitPattern = Marks.extendWhite("\\d+");
		final ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
		for (Path root : fs.getRootDirectories()) {
			final Path path = root.resolve("myid.txt");
			final String content;
			if (Files.exists(path)) {
				content = Files.readString(path);
			} else {
				content = "";
			}
			final Matcher matcher = digitPattern.matcher(content);
			final boolean found = matcher.find();
			if (found) {
				final String digits = matcher.group("basis");
				final int id = Integer.parseInt(digits);
				builder.add(id);
			}
		}
		final ImmutableSet<Integer> ids = builder.build();
		if (ids.size() >= 2) {
			LOGGER.warn("Multiple ids: {}.", ids);
		}
		return ids.stream().collect(MoreCollectors.toOptional());
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
				.collect(ImmutableMap.toImmutableMap(Function.identity(), IO_UNCHECKER.wrapFunction(this::getId)));

		final ImmutableMap<RepositoryCoordinatesWithPrefix, Integer> ids = idsOpt.entrySet().stream()
				.filter(e -> e.getValue().isPresent())
				.collect(ImmutableMap.toImmutableMap(Entry::getKey, e -> e.getValue().get()));

		final ImmutableMap<String, Integer> idsByGitHubUsername = ids.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(e -> e.getKey().getUsername(), Entry::getValue));

		final PrintableJsonObject asJson = JsonbUtils.toJsonObject(idsByGitHubUsername,
				JsonStudentOnGitHubKnown.asAdapter());
		Files.writeString(Path.of("gh-id.json"), asJson.toString());
	}

	private Optional<Integer> getId(RepositoryCoordinates coordinates) throws IOException {
		LOGGER.info("Proceeding with {}.", coordinates);
		try (FileRepository repository = GitCloner.create().download(coordinates.asGitUri(),
				Utils.getTempDirectory().resolve(coordinates.getRepositoryName()));
				GitFileSystemImpl fs = GitFileSystemProviderImpl.getInstance().newFileSystemFromFileRepository(repository)) {
			return getId(fs);
		}
	}
}
