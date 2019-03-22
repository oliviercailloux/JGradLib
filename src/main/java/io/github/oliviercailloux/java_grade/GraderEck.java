package io.github.oliviercailloux.java_grade;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.json.JsonGrade;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHub;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.mycourse.csv.MyCourseCsvWriter;
import io.github.oliviercailloux.grade.mycourse.json.StudentsReaderFromJson;
import io.github.oliviercailloux.java_grade.ex_eck.ExEckCriterion;

public class GraderEck {

	public GraderEck(String prefix) {
		this.prefix = prefix;
		usernames = new StudentsReaderFromJson();
		repositoriesByStudent = null;
	}

	public void writeJson(Set<Grade> grades) throws IOException {
		final String str = JsonGrade.asJsonArray(grades).toString();
		try (BufferedWriter fileWriter = Files.newBufferedWriter(Paths.get("out.json"), StandardCharsets.UTF_8)) {
			fileWriter.write(str);
		}
	}

	public ImmutableSet<Grade> readJson() throws IOException {
		final String filename = "manual - 12-08-23h.json";
//		final String filename = "out.json";
		final String jsonStr = new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
		return JsonGrade.asGrades(jsonStr);
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
		try (InputStream inputStream = Files.newInputStream(Paths.get("../../Java L3/usernamesGH-manual.json"))) {
			usernames.read(inputStream);
		}
	}

	public void writeCsv(Set<Grade> grades) throws IOException {
		final Path out = Paths.get("allgrades.csv");
		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.FRENCH);
		try (BufferedWriter fileWriter = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
			final CsvWriter writer = new CsvWriter(fileWriter, new CsvWriterSettings());
			final ImmutableSet<Criterion> allKeys = grades.stream().flatMap((g) -> g.getMarks().keySet().stream())
					.collect(ImmutableSet.toImmutableSet());
			writer.writeHeaders(Streams.concat(Stream.of("Name", "GitHub username"),
					allKeys.stream().map(Object::toString), Stream.of("Grade")).collect(Collectors.toList()));
			for (Grade grade : grades) {
				final StudentOnGitHub student = grade.getStudent();
				LOGGER.info("Writing {}.", student);
				writer.addValue("Name", student.getLastName().orElse("unknown"));
				writer.addValue("GitHub username", student.getGitHubUsername());

				for (Criterion criterion : grade.getMarks().keySet()) {
					final double mark = grade.getMarks().get(criterion).getPoints();
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

	private final StudentsReaderFromJson usernames;

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GraderEck.class);

	private ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositoriesByStudent;

	private final String prefix;

	public ImmutableMap<StudentOnGitHub, RepositoryCoordinates> getRepositoriesByStudent() {
		return repositoriesByStudent;
	}

	public ImmutableSet<Grade> gradeAll(ImmutableMap<StudentOnGitHubKnown, RepositoryCoordinates> repositories) {
		final ImmutableSet.Builder<Grade> gradesBuilder = ImmutableSet.builder();
		for (Map.Entry<StudentOnGitHubKnown, RepositoryCoordinates> entry : repositories.entrySet()) {
			final StudentOnGitHubKnown student = entry.getKey();
			final RepositoryCoordinates repo = entry.getValue();
			final Grade grade = grade(student, repo);
			gradesBuilder.add(grade);
			LOGGER.debug("Student {}, grades {}.", student, grade.getMarks().values());
			LOGGER.info("Evaluation: {}", grade.getAsMyCourseString());
		}
		return gradesBuilder.build();
	}

	private Grade grade(StudentOnGitHubKnown student, RepositoryCoordinates coord) {
		final GitFullContext context = FullContextInitializer.withPath(coord,
				Paths.get("/home/olivier/Professions/Enseignement/En cours", prefix));
		final Client client = context.getClient();
		final ImmutableSet.Builder<Mark> gradeBuilder = ImmutableSet.builder();
		final Instant deadline = ZonedDateTime.parse("2019-03-14T00:00:00+01:00").toInstant();

		final Mark contents;
		if (!client.existsCached()) {
			contents = Mark.min(ExEckCriterion.CONTENTS, "Repository not found");
		} else if (!client.hasContentCached()) {
			contents = Mark.min(ExEckCriterion.CONTENTS, "Repository found but is empty");
		} else if (!context.getMainCommit().isPresent()) {
			throw new IllegalStateException();
		} else if (context.getMainFilesReader().filter(
				(fc) -> fc.getPath().toString().endsWith("java") && fc.getContent().contains("static void main"))
				.asFileContents().isEmpty()) {
			throw new IllegalStateException("Repo but no java");
		} else {
			contents = Mark.max(ExEckCriterion.CONTENTS);
		}
		gradeBuilder.add(contents);
		gradeBuilder.add(Marks.timeMark(ExEckCriterion.ON_TIME, context, deadline, 1d, true));

		final Mark username;
		if (usernames.getIdsNotSubmitted().contains(student.getStudentId())) {
			username = Mark.min(ExEckCriterion.USERNAME, "Username not properly submitted.");
		} else {
			username = Mark.max(ExEckCriterion.USERNAME);
		}
		gradeBuilder.add(username);
		return Grade.of(student.asStudentOnGitHub(), gradeBuilder.build());
	}

	public static void main(String[] args) throws Exception {
		final String prefix = "eck1";
		final GraderEck orch = new GraderEck(prefix);
		orch.readUsernames();

		orch.readRepositories();
		final ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositories = orch.getRepositoriesByStudent();

		final ImmutableMap<StudentOnGitHubKnown, RepositoryCoordinates> repositoriesByKnown = repositories.entrySet()
				.stream().filter((e) -> e.getKey().hasStudentOnMyCourse())
				.collect(ImmutableMap.toImmutableMap((e) -> e.getKey().asStudentOnGitHubKnown(), (e) -> e.getValue()));

//		final ImmutableList<StudentOnGitHub> unknown = repositories.keySet().stream()
//				.filter((s) -> !s.hasStudentOnMyCourse()).collect(ImmutableList.toImmutableList());
//		checkState(unknown.isEmpty(), unknown);
//		final ImmutableMap<StudentOnGitHubKnown, RepositoryCoordinates> repositoriesByKnown = repositories.entrySet()
//				.stream()
//				.collect(ImmutableMap.toImmutableMap((e) -> e.getKey().asStudentOnGitHubKnown(), (e) -> e.getValue()));

		final ImmutableSet<Grade> grades = orch.gradeAll(repositoriesByKnown);
		orch.writeCsv(grades);
		orch.writeJson(grades);

		new MyCourseCsvWriter().writeCsv(prefix, 112144, grades);
	}

}
