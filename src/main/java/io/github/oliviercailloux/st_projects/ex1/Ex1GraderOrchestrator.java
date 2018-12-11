package io.github.oliviercailloux.st_projects.ex1;

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
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Streams;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.mycourse.MyCourseCsvWriter;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHubKnown;
import io.github.oliviercailloux.st_projects.model.StudentOnMyCourse;
import io.github.oliviercailloux.st_projects.services.read.UsernamesReader;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class Ex1GraderOrchestrator {

	public Ex1GraderOrchestrator() {
		usernames = new UsernamesReader();
	}

	public static void main(String[] args) throws Exception {
		new Ex1GraderOrchestrator().proceed();
	}

	public void proceed() throws Exception {

		try (InputStream inputStream = Files.newInputStream(Paths.get("usernames.json"))) {
			usernames.read(inputStream);
		}

		final ImmutableList<RepositoryCoordinates> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(Utils.getToken())) {
			repositories = fetcher.getRepositories("oliviercailloux-org");
		}
		LOGGER.debug("Repos: {}.", repositories);

//		final RepositoryCoordinates aRepo = RepositoryCoordinates.from("oliviercailloux-org",
//				"git-and-curl-jannani-lahsen");
//		final ImmutableList<RepositoryCoordinates> repositories2 = ImmutableList.of(aRepo);
		final Builder<StudentOnGitHub, Double> gradesBuilder = ImmutableMap.builder();
		final Builder<StudentOnGitHub, String> feedbackBuilder = ImmutableMap.builder();
		final Builder<StudentOnGitHubKnown, Ex1Grader> gradersBuilder = ImmutableMap.builder();
		final Instant deadline = ZonedDateTime.parse("2018-11-30T23:59:59+01:00").toInstant();
		for (RepositoryCoordinates repo : repositories) {
			final Pattern pattern = Pattern.compile("git-and-curl-(.*)");
			final Matcher matcher = pattern.matcher(repo.getRepositoryName());
			final boolean matches = matcher.matches();
			if (!matches) {
				continue;
			}
			final String gitHubUsername = matcher.group(1);
			final StudentOnGitHub student = usernames.getStudentOnGitHub(gitHubUsername);
			final Ex1Grader grader = new Ex1Grader();
			grader.setDeadline(deadline);
			if (student.hasStudentOnMyCourse()) {
				gradersBuilder.put(student.asStudentOnGitHubKnown(), grader);
			}
			grader.grade(repo, student, student.hasStudentOnMyCourse()
					&& !usernames.getIdsNotSubmitted().contains(student.getStudentOnMyCourse().get().getStudentId()));
			LOGGER.debug("Pass: {}.", grader.getPass());
			LOGGER.info("Evaluation: {}", grader.getEvaluation());
			LOGGER.info("Grade: {}", grader.getGrade());
			gradesBuilder.put(student, grader.getGrade());
			feedbackBuilder.put(student, grader.getEvaluation());
		}
		final ImmutableMap<StudentOnGitHub, Double> grades = gradesBuilder.build();
		final ImmutableMap<StudentOnGitHub, String> feedbacks = feedbackBuilder.build();
		final ImmutableMap<StudentOnGitHubKnown, Ex1Grader> graders = gradersBuilder.build();

		final Map<StudentOnMyCourse, Double> myCourseToGrades = grades.keySet().stream()
				.filter((s) -> s.hasStudentOnMyCourse())
				.collect(Utils.toLinkedMap((s) -> s.getStudentOnMyCourse().get(), grades::get));
		final Map<StudentOnMyCourse, String> myCourseToFeedbacks = feedbacks.keySet().stream()
				.filter((s) -> s.hasStudentOnMyCourse())
				.collect(Utils.toLinkedMap((s) -> s.getStudentOnMyCourse().get(), feedbacks::get));

		new MyCourseCsvWriter().writeCsv("Devoir git-and-curl corrigé", 110559, myCourseToGrades, myCourseToFeedbacks);
		writeCsv(graders);
	}

	public void writeCsv(Map<StudentOnGitHubKnown, Ex1Grader> graders) throws IOException {
		final Path out = Paths.get("allgrades.csv");
		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.FRENCH);
		try (BufferedWriter fileWriter = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
			final CsvWriter writer = new CsvWriter(fileWriter, new CsvWriterSettings());
			writer.writeHeaders(Streams
					.concat(Stream.of("Name"), Arrays.asList(Ex1Criterion.values()).stream().map(Object::toString))
					.collect(Collectors.toList()));
			for (StudentOnGitHubKnown student : graders.keySet()) {
				LOGGER.info("Writing {}.", student);
				final Ex1Grader grader = graders.get(student);
				writer.addValue("Name", student.getLastName());
				for (Ex1Criterion criterion : Ex1Criterion.values()) {
					final double mark;
					if (grader.getPass().contains(criterion)) {
						mark = criterion.getPoints();
					} else if (grader.getPenalties().containsKey(criterion)) {
						mark = -1d * grader.getPenalties().get(criterion);
					} else {
						mark = 0d;
					}
					writer.addValue(criterion.toString(), formatter.format(mark));
				}
				writer.writeValuesToRow();
			}
			writer.close();
		}
	}

	private final UsernamesReader usernames;

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Ex1GraderOrchestrator.class);

}
