package io.github.oliviercailloux.st_projects.ex2;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
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
import io.github.oliviercailloux.git.git_hub.utils.JsonUtils;
import io.github.oliviercailloux.mycourse.MyCourseCsvWriter;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHubKnown;
import io.github.oliviercailloux.st_projects.services.read.UsernamesReader;

public class GraderOrchestrator {

	public GraderOrchestrator() {
		usernames = new UsernamesReader();
		repositoriesByStudent = null;
		grades = null;
	}

	public static void main(String[] args) throws Exception {
		final GraderOrchestrator orch = new GraderOrchestrator();
		orch.proceed();
//		orch.jsonToMyCourse();
//		orch.writeCsv();
	}

	public void proceed() throws Exception {
		readUsernames();

		readRepositories();
//		setSingleRepo("edoreld");

		final Instant deadline = ZonedDateTime.parse("2018-12-05T23:59:59+01:00").toInstant();
		final Instant cap = ZonedDateTime.parse("2018-12-06T07:00:00+01:00").toInstant();
		final Ex2Grader grader = new Ex2Grader();
		grader.setDeadline(deadline);
		grader.setIgnoreAfter(cap);

		final ImmutableSet.Builder<Grade> gradesBuilder = ImmutableSet.builder();
		for (Entry<StudentOnGitHubKnown, RepositoryCoordinates> entry : getRepositoriesByStudentKnown().entrySet()) {
			final StudentOnGitHubKnown student = entry.getKey();
			final RepositoryCoordinates repo = entry.getValue();
			final Grade grade = grader.grade(repo, student.asStudentOnGitHub());
			gradesBuilder.add(grade);
			LOGGER.debug("Student {}, grades {}.", student, grade.getGrades().values());
			LOGGER.info("Evaluation: {}", grade.getAsMyCourseString());
		}
		grades = gradesBuilder.build();

		writeCsv();
		writeJson();
	}

	private void jsonToMyCourse() throws IOException {
		readJson();
		new MyCourseCsvWriter().writeCsv("Devoir servlet", 110565, grades);
	}

	private void writeJson() throws IOException {
		final ImmutableSet<Grade> gradesExt = grades.stream().map((g) -> extend(g))
				.collect(ImmutableSet.toImmutableSet());
		final String str = JsonUtils.serializeWithJsonB(gradesExt);
		try (BufferedWriter fileWriter = Files.newBufferedWriter(Paths.get("out.json"), StandardCharsets.UTF_8)) {
			fileWriter.write(str);
		}
	}

	private void readJson() throws IOException {
		final String filename = "manual - 12-08-23h.json";
//		final String filename = "out.json";
		final String jsonStr = new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
		final JsonArray json;
		try (JsonReader jr = Json.createReader(new StringReader(jsonStr))) {
			json = jr.readArray();
		}
		final Builder<Grade> builder = ImmutableSet.builder();
		for (JsonValue jsonValue : json) {
			final Grade grade = readGrade(jsonValue.asJsonObject());
			LOGGER.info("Grade read: {}.", grade);
			builder.add(grade);
		}
		grades = builder.build();
	}

	private Grade readGrade(JsonObject json) {
		final GHAsJson ghAsJson = new GHAsJson();
		final JsonObject st = json.getJsonObject("student");
		final StudentOnGitHub student = ghAsJson.adaptFromJson(st);
		final JsonArray gradesJson = json.getJsonArray("gradeValues");
		final Builder<SingleGrade> gradesBuilder = ImmutableSet.builder();
		for (JsonValue grade : gradesJson) {
			try (Jsonb jsonb = JsonbBuilder
					.create(new JsonbConfig().withAdapters(new AsEx2Criterion()).withFormatting(true))) {
				final SingleGrade thisGrade = jsonb.fromJson(grade.toString(), SingleGrade.class);
				gradesBuilder.add(thisGrade);
				LOGGER.info("Deserialized: {}.", thisGrade);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}
		final Grade grade = Grade.of(student, gradesBuilder.build());
		return grade;
	}

	private Grade extend(Grade g) {
		final Stream<SingleGrade> toAdd = Stream.of(SingleGrade.zero(Ex2Criterion.GET_SIMPLE, "Todo"),
				SingleGrade.zero(Ex2Criterion.DEFAULT_PARAM, "Todo"));
		final ImmutableSet<SingleGrade> ext = Stream.concat(g.getGradeValues().stream(), toAdd)
				.collect(ImmutableSet.toImmutableSet());
		return Grade.of(g.getStudent(), ext);
	}

	public void setSingleRepo(String studentGitHubUsername) {
		final RepositoryCoordinates aRepo = RepositoryCoordinates.from("oliviercailloux-org",
				PREFIX + "-" + studentGitHubUsername);
		repositoriesByStudent = ImmutableMap.of(usernames.getStudentOnGitHub(studentGitHubUsername), aRepo);
	}

	public void readRepositories() throws IOException {
		final ImmutableList<RepositoryCoordinates> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositories("oliviercailloux-org", false);
		}
		final Pattern pattern = Pattern.compile(PREFIX + "-(.*)");
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

	public void writeCsv() throws IOException {
		final Path out = Paths.get("allgrades.csv");
		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.FRENCH);
		try (BufferedWriter fileWriter = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
			final CsvWriter writer = new CsvWriter(fileWriter, new CsvWriterSettings());
			final ImmutableSet<GradeCriterion> allKeys = grades.stream().flatMap((g) -> g.getGrades().keySet().stream())
					.collect(ImmutableSet.toImmutableSet());
			writer.writeHeaders(Streams.concat(Stream.of("Name", "GitHub username"),
					allKeys.stream().map(Object::toString), Stream.of("Grade")).collect(Collectors.toList()));
			for (Grade grade : grades) {
				final StudentOnGitHub student = grade.getStudent();
				LOGGER.info("Writing {}.", student);
				writer.addValue("Name", student.getLastName().orElse("unknown"));
				writer.addValue("GitHub username", student.getGitHubUsername());

				for (GradeCriterion criterion : grade.getGrades().keySet()) {
					final double mark = grade.getGrades().get(criterion).getPoints();
					writer.addValue(criterion.toString(), formatter.format(mark));
				}

				writer.addValue("Grade", formatter.format(grade.getGrade()));
				writer.writeValuesToRow();
			}
			writer.close();
		}
	}

	private final UsernamesReader usernames;

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GraderOrchestrator.class);

	private ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositoriesByStudent;

	private static final String PREFIX = "servlet";

	private ImmutableSet<Grade> grades;

	public UsernamesReader getUsernames() {
		return usernames;
	}

	public ImmutableMap<StudentOnGitHub, RepositoryCoordinates> getRepositoriesByStudent() {
		return repositoriesByStudent;
	}

}
