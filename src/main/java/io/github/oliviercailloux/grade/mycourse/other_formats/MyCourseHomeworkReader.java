package io.github.oliviercailloux.grade.mycourse.other_formats;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.mycourse.StudentOnMyCourse;

public class MyCourseHomeworkReader {
	private String homeworkName;
	private Path homeworksPath;
	private List<StudentOnMyCourse> students;

	public MyCourseHomeworkReader(String homeworkName, Path homeworksPath, List<StudentOnMyCourse> students) {
		this.homeworkName = homeworkName;
		this.homeworksPath = homeworksPath;
		this.students = students;
		lastNameRead = null;
		lastUsernameRead = null;
	}

	public static ImmutableList<StudentOnGitHubKnown> readGitHubUsernames(String homeworkName, Path homeworksPath,
			List<StudentOnMyCourse> students) throws GradingException {
		final MyCourseHomeworkReader reader = new MyCourseHomeworkReader(homeworkName, homeworksPath, students);
		final ImmutableMap<StudentOnMyCourse, String> contentByStudent = reader.getContentByStudent();
		LOGGER.info("Missing: {}.", Sets.difference(ImmutableSet.copyOf(students), contentByStudent.keySet()));
		return contentByStudent.entrySet().stream().map((e) -> StudentOnGitHubKnown.with(e.getKey(), e.getValue()))
				.collect(ImmutableList.toImmutableList());
	}

	private ImmutableMap<StudentOnMyCourse, String> getContentByStudent() {
		final ImmutableList<List<String>> homeworks;
		try {
			homeworks = Files.list(homeworksPath)
					.filter((p) -> p.getFileName().toString().startsWith(homeworkName)
							&& p.getFileName().toString().endsWith(".txt"))
					.map((p) -> chunkHomework(p)).collect(ImmutableList.toImmutableList());
		} catch (IOException e) {
			throw new GradingException(e);
		}
		LOGGER.debug("Got homeworks from '{}' using '{}': {}.", homeworksPath, homeworkName, homeworks);

		final Builder<StudentOnMyCourse, String> contentByStudent = ImmutableMap.builder();
		for (List<String> homework : homeworks) {
			final StudentOnMyCourse student = getAuthor(homework);
			final String content = getContent(homework);
			contentByStudent.put(student, content);
		}
		return contentByStudent.build();
	}

	String getContent(List<String> homework) {
		final String collected = homework.stream().collect(Collectors.joining("\n"));
		final Matcher matcher = CONTENT_PATTERN.matcher(collected);
		final boolean found = matcher.find();
		checkArgument(found, "Problem with " + lastUsernameRead + ".");
		return matcher.group("CONTENT");
	}

	StudentOnMyCourse getAuthor(List<String> homework) {
		readAuthor(homework);
		return students.stream().filter((s) -> s.getMyCourseUsername().equals(lastUsernameRead))
				.collect(MoreCollectors.onlyElement());
	}

	void readAuthor(List<String> homework) {
		final String nameLine = homework.stream().filter((l) -> NAME_PATTERN.matcher(l).matches())
				.collect(MoreCollectors.onlyElement());
		final Matcher nameMatcher = NAME_PATTERN.matcher(nameLine);
		nameMatcher.matches();
		lastNameRead = nameMatcher.group("NAME");
		LOGGER.info("Found name: {}.", lastNameRead);
		lastUsernameRead = nameMatcher.group("USERNAME");
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(MyCourseHomeworkReader.class);
	private String lastNameRead;
	private String lastUsernameRead;
	private static final Pattern NAME_PATTERN = Pattern
			.compile("Nom\u00A0: " + "(?<NAME>[^\\(]+)" + " " + "\\(" + "(?<USERNAME>[^\\)]+)" + "\\)");
	private static final Pattern CONTENT_PATTERN = Pattern.compile("Champ d'envoi\u00A0:\n<p>(?<CONTENT>.*)</p>\n\n");

	private List<String> chunkHomework(Path p) {
		/** Should rather wrap inline */
		try {
			return Files.readAllLines(p);
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}

	String getLastNameRead() {
		return lastNameRead;
	}

	String getLastUsernameRead() {
		return lastUsernameRead;
	}
}
