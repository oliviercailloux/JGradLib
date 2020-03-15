package io.github.oliviercailloux.java_grade.utils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;

import io.github.oliviercailloux.email.Email;
import io.github.oliviercailloux.email.Emailer;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.format.HtmlGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.mycourse.json.JsonStudentOnGitHubKnown;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.utils.Utils;

public class SendEmails {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SendEmails.class);

	private static final Path WORK_DIR = Paths.get("../../Java L3/");
	private static final String PREFIX = "git-br";

	public static final String FILE_NAME = "data.json";

	public static final String MIME_SUBTYPE = "json";

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
		final Email email = Email.withDocumentAndFile(doc, FILE_NAME, JsonGrade.asJson(grade).toString(), MIME_SUBTYPE,
				address);
		return email;
	}

	public static String getLastEmailTo(InternetAddress recipient) {
		final ImmutableSet<Message> sent = Utils
				.getOrThrow(() -> Emailer.searchSentToIn(recipient, "Éléments envoyés"));

	}

	public static Optional<PrintableJsonObject> getJsonData(Message source) {
		try {
			if (!(source instanceof MimeMessage)) {
				return Optional.empty();
			}
			LOGGER.info("Content type: {}.", source.getContentType());
			LOGGER.info("Is mime type? {}.", source.isMimeType("multipart/mixed"));
			if (!source.isMimeType("multipart/mixed")) {
				return Optional.empty();
			}

			final MimeMessage mimeSource = (MimeMessage) source;
			final ImmutableList<MimeBodyPart> parts = getParts(mimeSource);
			final Optional<MimeBodyPart> matchingPart = parts.stream()
					.filter(p -> Utils.getOrThrow(() -> p.isMimeType("text/" + MIME_SUBTYPE)))
					.filter(p -> Utils.getOrThrow(() -> p.getFileName().equals(FILE_NAME)))
					.collect(MoreCollectors.toOptional());
			final Optional<Object> content = matchingPart.map(Utils.uncheck(MimeBodyPart::getContent));
			LOGGER.info("Content: {}.", content);
			return null;
		} catch (IOException | MessagingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static ImmutableList<MimeBodyPart> getParts(final MimeMessage source)
			throws IOException, MessagingException {
		final MimeMultipart multipart = (MimeMultipart) source.getContent();
		final ImmutableList.Builder<MimeBodyPart> partsBuilder = ImmutableList.<MimeBodyPart>builder();
		for (int i = 0; i < multipart.getCount(); ++i) {
			final MimeBodyPart part = (MimeBodyPart) multipart.getBodyPart(i);
			partsBuilder.add(part);
		}
		final ImmutableList<MimeBodyPart> parts = partsBuilder.build();
		return parts;
	}
}
