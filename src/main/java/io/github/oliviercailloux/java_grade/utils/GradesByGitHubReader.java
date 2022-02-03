package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.json.JsonbUtils;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GradesByGitHubReader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GradesByGitHubReader.class);

	private Path gradesInputPath;
	private ImmutableSet<GitHubUsername> restrictTo;

	GradesByGitHubReader(Path input) {
		gradesInputPath = checkNotNull(input);
		restrictTo = null;
	}

	public GradesByGitHubReader setPatched() {
		final String ext = com.google.common.io.Files.getFileExtension(gradesInputPath.toString());
		final String name = com.google.common.io.Files.getNameWithoutExtension(gradesInputPath.toString());
		final Path givenParent = gradesInputPath.getParent();
		final Path parent = givenParent == null ? Path.of("") : givenParent;
		gradesInputPath = parent.resolve(name + " patched" + "." + ext);
		return this;
	}

	public Path getGradesInputPath() {
		return gradesInputPath;
	}

	public GradesByGitHubReader setInputPath(Path gradesInputPath) {
		this.gradesInputPath = checkNotNull(gradesInputPath);
		return this;
	}

	public GradesByGitHubReader restrictTo(Set<GitHubUsername> restrict) {
		this.restrictTo = ImmutableSet.copyOf(restrict);
		return this;
	}

	public ImmutableMap<GitHubUsername, IGrade> readGrades() throws IOException {
		@SuppressWarnings("all")
		final Type type = new LinkedHashMap<RepositoryCoordinates, IGrade>() {
		}.getClass().getGenericSuperclass();

		LOGGER.debug("Reading grades.");
		final String sourceGrades = Files.readString(gradesInputPath);
		final Map<String, IGrade> grades = JsonbUtils.fromJson(sourceGrades, type, JsonGrade.asAdapter());
		LOGGER.debug("Read keys: {}.", grades.keySet());
		final ImmutableMap<GitHubUsername, IGrade> gradesByUsername = Maps.toMap(
				grades.keySet().stream().map(GitHubUsername::given).collect(ImmutableSet.toImmutableSet()),
				u -> grades.get(u.getUsername()));

		final ImmutableMap<GitHubUsername, IGrade> restricted;
		if (restrictTo == null) {
			restricted = gradesByUsername;
		} else {
			restricted = ImmutableMap.copyOf(Maps.filterKeys(gradesByUsername, restrictTo::contains));
		}
		return restricted;
	}

}
