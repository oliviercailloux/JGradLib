package io.github.oliviercailloux.st_projects;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Streams;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.git.utils.JsonUtils;
import io.github.oliviercailloux.mycourse.MyCourseCsvWriter;
import io.github.oliviercailloux.st_projects.ex2.AsEx2Criterion;
import io.github.oliviercailloux.st_projects.ex2.GHAsJson;
import io.github.oliviercailloux.st_projects.model.Criterion;
import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.StudentGrade;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHubKnown;
import io.github.oliviercailloux.st_projects.services.read.UsernamesReader;

public class GraderOrchestrator {

	public GraderOrchestrator(String prefix) {
		this.prefix = prefix;
		usernames = new UsernamesReader();
		repositoriesByStudent = null;
	}

	private void jsonToMyCourse(Set<StudentGrade> grades) throws IOException {
		readJson();
		new MyCourseCsvWriter().writeCsv("Devoir servlet", 110565, grades);
	}

	private void writeJson(Set<StudentGrade> grades) throws IOException {
		final String str = JsonUtils.serializeWithJsonB(grades);
		try (BufferedWriter fileWriter = Files.newBufferedWriter(Paths.get("out.json"), StandardCharsets.UTF_8)) {
			fileWriter.write(str);
		}
	}

	private ImmutableSet<StudentGrade> readJson() throws IOException {
		final String filename = "manual - 12-08-23h.json";
//		final String filename = "out.json";
		final String jsonStr = new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
		final JsonArray json;
		try (JsonReader jr = Json.createReader(new StringReader(jsonStr))) {
			json = jr.readArray();
		}
		final Builder<StudentGrade> builder = ImmutableSet.builder();
		for (JsonValue jsonValue : json) {
			final StudentGrade grade = readGrade(jsonValue.asJsonObject());
			LOGGER.info("Grade read: {}.", grade);
			builder.add(grade);
		}
		return builder.build();
	}

	private StudentGrade readGrade(JsonObject json) {
		final GHAsJson ghAsJson = new GHAsJson();
		final JsonObject st = json.getJsonObject("student");
		final StudentOnGitHub student = ghAsJson.adaptFromJson(st);
		final JsonArray gradesJson = json.getJsonArray("gradeValues");
		final Builder<CriterionGrade> gradesBuilder = ImmutableSet.builder();
		for (JsonValue grade : gradesJson) {
			try (Jsonb jsonb = JsonbBuilder
					.create(new JsonbConfig().withAdapters(new AsEx2Criterion()).withFormatting(true))) {
				final CriterionGrade thisGrade = jsonb.fromJson(grade.toString(), CriterionGrade.class);
				gradesBuilder.add(thisGrade);
				LOGGER.info("Deserialized: {}.", thisGrade);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}
		final StudentGrade grade = StudentGrade.of(student, gradesBuilder.build());
		return grade;
	}

	public void setSingleRepo(String studentGitHubUsername) {
		final RepositoryCoordinates aRepo = RepositoryCoordinates.from("oliviercailloux-org",
				prefix + "-" + studentGitHubUsername);
		repositoriesByStudent = ImmutableMap.of(usernames.getStudentOnGitHub(studentGitHubUsername), aRepo);
	}

	public void readRepositories() throws IOException {
		final ImmutableList<RepositoryCoordinates> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositories("oliviercailloux-org", false);
		}
		final Pattern pattern = Pattern.compile(prefix + "-(.*)");
		ImmutableMap.Builder<StudentOnGitHub, RepositoryCoordinates> repoBuilder = ImmutableMap.builder();
		for (RepositoryCoordinates repo : repositories) {
			final Matcher matcher = pattern.matcher(repo.getRepositoryName());
			final boolean matches = matcher.matches();
			if (!matches) {
				continue;
			}
			final String gitHubUsername = matcher.group(1);
			repoBuilder.put(usernames.getStudentOnGitHub(gitHubUsername), repo);
		}
		repositoriesByStudent = repoBuilder.build();
		LOGGER.info("Repos: {}.", repositoriesByStudent);
	}

	/**
	 * The students must all be known.
	 */
	public ImmutableMap<StudentOnGitHubKnown, RepositoryCoordinates> getRepositoriesByStudentKnown() {
		return repositoriesByStudent.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap((e) -> e.getKey().asStudentOnGitHubKnown(), Map.Entry::getValue));
	}

	public void readUsernames() throws IOException {
		try (InputStream inputStream = Files.newInputStream(Paths.get("usernames.json"))) {
			usernames.read(inputStream);
		}
	}

	public void writeCsv(Set<StudentGrade> grades) throws IOException {
		final Path out = Paths.get("allgrades.csv");
		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.FRENCH);
		try (BufferedWriter fileWriter = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
			final CsvWriter writer = new CsvWriter(fileWriter, new CsvWriterSettings());
			final ImmutableSet<Criterion> allKeys = grades.stream().flatMap((g) -> g.getGrades().keySet().stream())
					.collect(ImmutableSet.toImmutableSet());
			writer.writeHeaders(Streams.concat(Stream.of("Name", "GitHub username"),
					allKeys.stream().map(Object::toString), Stream.of("Grade")).collect(Collectors.toList()));
			for (StudentGrade grade : grades) {
				final StudentOnGitHub student = grade.getStudent();
				LOGGER.info("Writing {}.", student);
				writer.addValue("Name", student.getLastName().orElse("unknown"));
				writer.addValue("GitHub username", student.getGitHubUsername());

				for (Criterion criterion : grade.getGrades().keySet()) {
					final double mark = grade.getGrades().get(criterion).getPoints();
					writer.addValue(criterion.toString(), formatter.format(mark));
				}

				writer.addValue("Grade", formatter.format(grade.getGrade()));
				writer.writeValuesToRow();
			}

			writer.addValue("Name", "Range");
			writer.addValue("GitHub username", "Range");
			for (Criterion criterion : allKeys) {
				writer.addValue(criterion.toString(),
						"[" + criterion.getMinPoints() + ", " + criterion.getMaxPoints() + "]");
			}
			final double minGrade = allKeys.stream().collect(Collectors.summingDouble(Criterion::getMinPoints));
			final double maxGrade = allKeys.stream().collect(Collectors.summingDouble(Criterion::getMaxPoints));
			writer.addValue("Grade", "[" + minGrade + "," + maxGrade + "]");
			writer.writeValuesToRow();

			writer.close();
		}
	}

	private final UsernamesReader usernames;

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GraderOrchestrator.class);

	private ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositoriesByStudent;

	private final String prefix;

	public UsernamesReader getUsernames() {
		return usernames;
	}

	public ImmutableMap<StudentOnGitHub, RepositoryCoordinates> getRepositoriesByStudent() {
		return repositoriesByStudent;
	}

}
