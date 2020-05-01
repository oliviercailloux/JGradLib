package io.github.oliviercailloux.grade.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Multimaps;
import com.google.common.io.CharStreams;
import com.google.common.math.Stats;

import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.comm.Emailer.ImapSearchPredicate;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.utils.Utils;

public class GradesInEmails implements AutoCloseable {
	public static final String FILE_NAME = "data.json";

	public static final String MIME_SUBTYPE = "json";
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GradesInEmails.class);

	public static Optional<IGrade> getGrade(Message source) {
		try {
			if (!(source instanceof MimeMessage)) {
				return Optional.empty();
			}
			LOGGER.debug("Content type: {}.", source.getContentType());
			if (!source.isMimeType("multipart/mixed")) {
				return Optional.empty();
			}

			final MimeMessage mimeSource = (MimeMessage) source;
			final ImmutableList<MimeBodyPart> parts = getParts(mimeSource);
			final Optional<MimeBodyPart> matchingPart = parts.stream()
					.filter(p -> Utils.getOrThrow(() -> p.isMimeType("text/" + MIME_SUBTYPE)))
					.filter(p -> Utils.getOrThrow(() -> p.getFileName().equals(FILE_NAME)))
					.collect(MoreCollectors.toOptional());
			final Optional<IGrade> grade = matchingPart.map(GradesInEmails::getGrade);
			return grade;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (MessagingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static IGrade getGrade(MimeBodyPart part) {
		final IGrade grade;
		try (InputStreamReader reader = new InputStreamReader((InputStream) part.getContent(),
				StandardCharsets.UTF_8)) {
			final String content = CharStreams.toString(reader);
			grade = JsonbUtils.fromJson(content, IGrade.class, JsonGrade.asAdapter());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (MessagingException e) {
			throw new IllegalStateException(e);
		}
		return grade;
	}

	private static ImmutableList<MimeBodyPart> getParts(MimeMessage source) throws IOException, MessagingException {
		final MimeMultipart multipart = (MimeMultipart) source.getContent();
		final ImmutableList.Builder<MimeBodyPart> partsBuilder = ImmutableList.<MimeBodyPart>builder();
		for (int i = 0; i < multipart.getCount(); ++i) {
			final MimeBodyPart part = (MimeBodyPart) multipart.getBodyPart(i);
			partsBuilder.add(part);
		}
		final ImmutableList<MimeBodyPart> parts = partsBuilder.build();
		return parts;
	}

	public static EmailAddress asAddress(StudentOnGitHubKnown student) {
		checkNotNull(student);
		final String emailStr = student.asStudentOnMyCourse().getEmail();
		// final String emailStr = "olivier.cailloux@INVALIDdauphine.fr";
		return EmailAddress.given(emailStr, student.getFirstName() + " " + student.getLastName());
	}

	public static Email asEmail(EmailAddress studentAddress, String gradeName, IGrade grade, Stats stats,
			Map<Integer, Double> quartiles) {
		final HtmlGrades htmler = HtmlGrades.newInstance();
		htmler.setTitle("Grade " + gradeName);
		if (stats.count() >= 20) {
			htmler.setStats(stats);
			htmler.setQuantiles(quartiles);
		}
		final Document doc = htmler.asHtml(grade);
		final Email email = Email.withDocumentAndFile(doc, FILE_NAME, JsonGrade.asJson(grade).toString(), MIME_SUBTYPE,
				studentAddress);
		return email;
	}

	public static GradesInEmails newInstance() {
		return new GradesInEmails();
	}

	private final Emailer emailer;

	private Folder folder;

	private GradesInEmails() {
		emailer = Emailer.newInstance();
		folder = null;
	}

	public Emailer getEmailer() {
		return emailer;
	}

	public Folder getFolder() {
		return folder;
	}

	/**
	 * @param folder must be open.
	 * @see Emailer#getFolder(String)
	 */
	public void setFolder(Folder folder) {
		checkArgument(folder.isOpen());
		this.folder = folder;
	}

	@SuppressWarnings("resource")
	public ImmutableSetMultimap<String, IGrade> getAllGradesTo(EmailAddress recipient) {
		checkState(folder != null);

		final ImapSearchPredicate subjectContains = Emailer.ImapSearchPredicate.subjectContains("Grade ");
		final ImapSearchPredicate recipientEqual = Emailer.ImapSearchPredicate
				.recipientAddressEquals(RecipientType.TO, recipient.getAddress());
		final ImmutableSet<Message> matching = emailer.searchIn(subjectContains.andSatisfy(recipientEqual), folder);
		final ImmutableSortedSet<Message> matchingSorted = matching.stream().collect(ImmutableSortedSet
				.toImmutableSortedSet(Comparator.comparing(Emailer.uncheck(Message::getSentDate))));

		final ImmutableSetMultimap.Builder<String, IGrade> gradesBuilder = ImmutableSetMultimap.builder();
		for (Message message : matchingSorted) {
			final Optional<IGrade> gradeOpt = getGrade(message);
			if (gradeOpt.isPresent()) {
				final String subject = Emailer.call(message::getSubject);
				verify(subject.startsWith("Grade "));
				gradesBuilder.put(subject.substring(6), gradeOpt.get());
			}
		}
		return gradesBuilder.build();
	}

	public ImmutableMap<String, IGrade> getAllLatestGradesTo(EmailAddress recipient) {
		return Multimaps.asMap(getAllGradesTo(recipient)).entrySet().stream().collect(ImmutableMap.toImmutableMap(
				Map.Entry::getKey, e -> ((ImmutableSet<IGrade>) e.getValue()).asList().reverse().get(0)));
	}

	public Optional<IGrade> getLastGradeTo(EmailAddress recipient, String prefix) {
		final ImmutableMap<String, IGrade> allGrades = getAllLatestGradesTo(recipient);
		return Optional.ofNullable(allGrades.get(prefix));
	}

	@Override
	public void close() {
		emailer.close();
	}
}
