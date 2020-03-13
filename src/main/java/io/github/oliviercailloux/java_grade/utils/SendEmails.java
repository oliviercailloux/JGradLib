package io.github.oliviercailloux.java_grade.utils;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.mail.Message;
import javax.mail.internet.InternetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.email.Email;
import io.github.oliviercailloux.email.Emailer;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.format.HtmlGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.mycourse.json.JsonStudentOnGitHubKnown;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.utils.Utils;

public class SendEmails {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SendEmails.class);

	private static final Path WORK_DIR = Paths.get("../../Java L3/");
	private static final String PREFIX = "git-br";

	public static void main(String[] args) throws Exception {
		@SuppressWarnings("all")
		final Type typeSet = new HashSet<StudentOnGitHubKnown>() {
		}.getClass().getGenericSuperclass();
		final Set<StudentOnGitHubKnown> usernamesAsSet = JsonbUtils.fromJson(
				Files.readString(WORK_DIR.resolve("usernames.json")), typeSet, JsonStudentOnGitHubKnown.asAdapter());

		final ImmutableMap<String, StudentOnGitHubKnown> usernames = usernamesAsSet.stream()
				.collect(ImmutableMap.toImmutableMap(s -> s.getGitHubUsername(), s -> s));

		@SuppressWarnings("all")
		final Type type = new HashMap<RepositoryCoordinates, IGrade>() {
		}.getClass().getGenericSuperclass();
		final Map<String, IGrade> grades = JsonbUtils.fromJson(
				Files.readString(WORK_DIR.resolve("all grades " + PREFIX + ".json")), type, JsonGrade.asAdapter());

		final Map<String, IGrade> gradesFiltered = grades.entrySet().stream().filter(e -> e.getKey().equals("…"))
				.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
//		final Map<String, IGrade> gradesFiltered = grades;

		final ImmutableSet<Email> emails = gradesFiltered.entrySet().stream()
				.map(e -> asEmail(usernames.get(e.getKey()), e.getValue())).collect(ImmutableSet.toImmutableSet());
//
		final ImmutableSet<Email> effectiveEmails = emails;
//		final ImmutableSet<Email> effectiveEmails = emails.stream().limit(1).collect(ImmutableSet.toImmutableSet());
		LOGGER.info("Prepared {}.", effectiveEmails);

		final ImmutableSet<Message> sent = Emailer.send(effectiveEmails);
		LOGGER.info("Sent {} messages.", sent.size());
	}

	private static Email asEmail(StudentOnGitHubKnown student, IGrade grade) {
		final Document doc = HtmlGrade.asHtml(grade, "Grade " + PREFIX, 20);
//		final String emailStr = student.asStudentOnMyCourse().getEmail();
		final String emailStr = "olivier.cailloux@INVALIDdauphine.fr";
		final InternetAddress address = Utils
				.getOrThrow(() -> new InternetAddress(emailStr, student.getFirstName() + " " + student.getLastName()));
		final Email email = Email.withDocumentAndFile(doc, "data.json", JsonGrade.asJson(grade).toString(), "json",
				address);
		return email;
	}
}
