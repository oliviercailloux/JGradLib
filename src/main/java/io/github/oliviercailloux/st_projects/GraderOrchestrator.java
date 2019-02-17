package io.github.oliviercailloux.st_projects;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.mycourse.MyCourseCsvWriter;
import io.github.oliviercailloux.mycourse.StudentOnGitHub;
import io.github.oliviercailloux.mycourse.StudentOnGitHubKnown;
import io.github.oliviercailloux.mycourse.UsernamesReader;
import io.github.oliviercailloux.st_projects.ex3.Ex3Grader;
import io.github.oliviercailloux.st_projects.model.Criterion;
import io.github.oliviercailloux.st_projects.model.Grade;
import io.github.oliviercailloux.st_projects.services.json.JsonGrade;

public class GraderOrchestrator {

	public GraderOrchestrator(String prefix) {
		this.prefix = prefix;
		usernames = new UsernamesReader();
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
		try (InputStream inputStream = Files
				.newInputStream(Paths.get("../../Java SITN, app, concept°/usernames.json"))) {
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

	private final UsernamesReader usernames;

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GraderOrchestrator.class);

	private ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositoriesByStudent;

	private final String prefix;

	public ImmutableMap<StudentOnGitHub, RepositoryCoordinates> getRepositoriesByStudent() {
		return repositoriesByStudent;
	}

	public ImmutableSet<Grade> gradeAll(Ex3Grader grader,
			ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositories) {
		final ImmutableSet.Builder<Grade> gradesBuilder = ImmutableSet.builder();
		for (Map.Entry<StudentOnGitHub, RepositoryCoordinates> entry : repositories.entrySet()) {
			final StudentOnGitHub student = entry.getKey();
			final RepositoryCoordinates repo = entry.getValue();
			final Grade grade = Grade.of(student, grader.grade(repo));
			gradesBuilder.add(grade);
			LOGGER.debug("Student {}, grades {}.", student, grade.getMarks().values());
			LOGGER.info("Evaluation: {}", grade.getAsMyCourseString());
		}
		return gradesBuilder.build();
	}

	public static void main(String[] args) throws Exception {
		final String prefix = "ci";
		final GraderOrchestrator orch = new GraderOrchestrator(prefix);
		orch.readUsernames();

		orch.readRepositories();
		// orch.setSingleRepo("guillaumerg7");
		final ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositories = orch.getRepositoriesByStudent();

		final Ex3Grader grader = new Ex3Grader();

		final ImmutableSet<Grade> grades = orch.gradeAll(grader, repositories);
//		final ImmutableSet<StudentGrade> grades = orch.readJson();
		orch.writeCsv(grades);
		orch.writeJson(grades);

		new MyCourseCsvWriter().writeCsv("Devoir " + prefix, 110774, grades);
	}

}
