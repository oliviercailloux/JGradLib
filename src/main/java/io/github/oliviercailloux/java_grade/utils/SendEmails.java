package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.AndTerm;
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.RecipientTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SubjectTerm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Multimaps;
import com.google.common.io.CharStreams;

import io.github.oliviercailloux.email.Email;
import io.github.oliviercailloux.email.Emailer;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.mycourse.json.JsonStudentOnGitHubKnown;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.utils.Utils;

public class SendEmails {
	@SuppressWarnings("serial")
	private static class PreciseSubjectTerm extends SearchTerm {
		private final String subject;

		private PreciseSubjectTerm(String subject) {
			this.subject = checkNotNull(subject);
		}

		@Override
		public boolean match(Message msg) {
			return Utils.getOrThrow(msg::getSubject).equals(subject);
		}

	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SendEmails.class);

	private static final Path WORK_DIR = Paths.get("../../Java L3/");
	private static final String PREFIX = "print-exec";

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

//		final Map<String, IGrade> gradesFiltered = grades.entrySet().stream().limit(5)
//				.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
//		final Map<String, IGrade> gradesFiltered = grades.entrySet().stream().filter(e -> e.getKey().equals("…"))
//				.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
		final Map<String, IGrade> gradesFiltered = grades;

		final Map<String, Optional<IGrade>> lastGrades = gradesFiltered.keySet().stream()
				.collect(ImmutableMap.toImmutableMap(l -> l, l -> getLastGradeTo(asAddress(usernames.get(l)), PREFIX)));

		for (String login : lastGrades.keySet()) {
			final Optional<IGrade> lastGrade = lastGrades.get(login);
			if (lastGrade.isPresent()) {
				LOGGER.info("Diff {}: {}.", login, getDiff(lastGrade.get(), gradesFiltered.get(login)));
			} else {
				LOGGER.info("Not found {}.", login);
			}
		}

		final Map<String, IGrade> gradesDiffering = gradesFiltered.entrySet().stream()
				.filter(e -> lastGrades.get(e.getKey()).filter(g -> g.equals(e.getValue())).isEmpty())
				.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

		final ImmutableSet<Email> emails = gradesDiffering.entrySet().stream()
				.map(e -> asEmail(usernames.get(e.getKey()), e.getValue())).collect(ImmutableSet.toImmutableSet());

		final ImmutableSet<Email> effectiveEmails = emails;
//		final ImmutableSet<Email> effectiveEmails = emails.stream().limit(1).collect(ImmutableSet.toImmutableSet());
		LOGGER.info("Prepared {}.", effectiveEmails);

		final ImmutableSet<Message> sent = Emailer.send(effectiveEmails);
		LOGGER.info("Sent {} messages.", sent.size());
	}

	private static String getDiff(IGrade grade1, IGrade grade2) {
		if (grade1.equals(grade2)) {
			return "";
		}
		if (ImmutableSet.of(grade1.getClass(), grade2.getClass())
				.equals(ImmutableSet.of(Mark.class, WeightingGrade.class))) {
			return "Different types.";
		}

		String diff = "";
		if (!grade1.getComment().equals(grade2.getComment())) {
			diff += "First: '" + grade1.getComment() + "'; Second: '" + grade2.getComment() + "'. ";
		}
		if (grade1 instanceof Mark) {
			if (grade1.getPoints() != grade2.getPoints()) {
				diff += "First: " + grade1.getPoints() + "; Second: " + grade2.getPoints() + ". ";
			}
		} else if (grade1 instanceof WeightingGrade) {
			final ImmutableMap<Criterion, IGrade> subGrades1 = grade1.getSubGrades();
			final ImmutableMap<Criterion, IGrade> subGrades2 = grade2.getSubGrades();
			checkArgument(subGrades1.keySet().equals(subGrades2.keySet()));
			for (Criterion criterion : subGrades1.keySet()) {
				final IGrade subGrade1 = subGrades1.get(criterion);
				final IGrade subGrade2 = subGrades2.get(criterion);
				final String subDiff = getDiff(subGrade1, subGrade2);
				if (!subDiff.isEmpty()) {
					diff += criterion + ": [" + subDiff + "] ";
				}
			}
		} else {
			throw new IllegalArgumentException("Unsupported type.");
		}

		Verify.verify(!diff.isEmpty(), String.format("Grade1: %s, Grade2: %s.", grade1, grade2));
		return diff;
	}

	private static Email asEmail(StudentOnGitHubKnown student, IGrade grade) {
		final Document doc = HtmlGrades.asHtml(grade, "Grade " + PREFIX, 20);
		final InternetAddress address = asAddress(student);
		final Email email = Email.withDocumentAndFile(doc, FILE_NAME, JsonGrade.asJson(grade).toString(), MIME_SUBTYPE,
				address);
		return email;
	}

	private static InternetAddress asAddress(StudentOnGitHubKnown student) {
		checkNotNull(student);
		final String emailStr = student.asStudentOnMyCourse().getEmail();
//		final String emailStr = "olivier.cailloux@INVALIDdauphine.fr";
		final InternetAddress address = Utils
				.getOrThrow(() -> new InternetAddress(emailStr, student.getFirstName() + " " + student.getLastName()));
		return address;
	}

	public static Optional<IGrade> getLastGradeTo(InternetAddress recipient, String prefix) {
		final ImmutableMap<String, IGrade> allGrades = getAllLatestGradesTo(recipient);
		return Optional.ofNullable(allGrades.get(prefix));
		/**
		 * The version below is more efficient as it filters on the server. But I don’t
		 * think it will change much, and in any case we’d better reuse the logic of
		 * #getAll… by adding optional search term filters.
		 */
//		try {
//			final SearchTerm fullAddressTerm = new RecipientTerm(RecipientType.TO, recipient);
//			/**
//			 * Because of bugs in the IMAP server, we have to relax the search on the
//			 * address part.
//			 */
//			final SearchTerm partialAddressTerm = new RecipientStringTerm(RecipientType.TO,
//					Emailer.getPart(recipient.getAddress()));
//			final SearchTerm personalTerm = new RecipientStringTerm(RecipientType.TO, recipient.getPersonal());
//			final String subject = "Grade " + prefix;
//			final SearchTerm preciseSubjectTerm = new PreciseSubjectTerm(subject);
//			final SearchTerm subjectTerm = new SubjectTerm(subject);
//			final AndTerm fullTerm = new AndTerm(
//					new SearchTerm[] { fullAddressTerm, personalTerm, preciseSubjectTerm });
//			final AndTerm truncatedTerm = new AndTerm(
//					new SearchTerm[] { partialAddressTerm, personalTerm, subjectTerm });
//
//			return Emailer.fromFolder("Éléments envoyés", f -> {
//				final ImmutableSet<Message> candidates = Emailer.searchIn(truncatedTerm, f, false);
//				/**
//				 * Because of bugs in the IMAP server, we have to filter again on the personal;
//				 * and because the subject pattern is only a pattern, it could match "Re: …".
//				 */
//				final ImmutableSortedSet<Message> matching = candidates.stream()
//						.filter(m -> Utils.getOrThrow(() -> m.match(fullTerm))).collect(ImmutableSortedSet
//								.toImmutableSortedSet(Comparator.comparing(Utils.uncheck(Message::getSentDate))));
//				final Optional<Message> message = matching.descendingSet().stream().findFirst();
//				if (message.isPresent()) {
//					try {
//						LOGGER.info("Searching for messages sent to {}; found message to {}, sent {}.",
//								recipient.getPersonal(), message.get().getAllRecipients(), message.get().getSentDate());
//					} catch (MessagingException e) {
//						throw new IllegalStateException(e);
//					}
//				}
//				return message.map(m -> getGrade(m).get());
//			});
//		} catch (MessagingException e) {
//			throw new IllegalStateException(e);
//		}
	}

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
			final Optional<IGrade> grade = matchingPart.map(SendEmails::getGrade);
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

	public static ImmutableSetMultimap<String, IGrade> getAllGradesTo(InternetAddress recipient) {
		try {
			final SearchTerm fullAddressTerm = new RecipientTerm(RecipientType.TO, recipient);
			/**
			 * Because of bugs in the IMAP server, we have to relax the search on the
			 * address part.
			 */
			final SearchTerm partialAddressTerm = new RecipientStringTerm(RecipientType.TO,
					Emailer.getPart(recipient.getAddress()));
			final SearchTerm personalTerm = new RecipientStringTerm(RecipientType.TO, recipient.getPersonal());
			final SearchTerm subjectTerm = new SubjectTerm("Grade ");
			final AndTerm fullTerm = new AndTerm(new SearchTerm[] { fullAddressTerm, personalTerm, subjectTerm });
			final AndTerm truncatedTerm = new AndTerm(
					new SearchTerm[] { partialAddressTerm, personalTerm, subjectTerm });

			return Emailer.fromFolder("Éléments envoyés", f -> {
				final ImmutableSet<Message> candidates = Emailer.searchIn(truncatedTerm, f, false);
				final ImmutableSortedSet<Message> matching = candidates.stream()
						.filter(m -> Utils.getOrThrow(() -> m.match(fullTerm))).collect(ImmutableSortedSet
								.toImmutableSortedSet(Comparator.comparing(Utils.uncheck(Message::getSentDate))));
				final ImmutableSetMultimap.Builder<String, IGrade> gradesBuilder = ImmutableSetMultimap.builder();
				for (Message message : matching) {
					final Optional<IGrade> gradeOpt = getGrade(message);
					if (gradeOpt.isPresent()) {
						final String subject = Utils.getOrThrow(() -> message.getSubject());
						checkState(subject.startsWith("Grade "));
						gradesBuilder.put(subject.substring(6), gradeOpt.get());
					}
				}
				return gradesBuilder.build();
			});
		} catch (MessagingException e) {
			throw new IllegalStateException(e);
		}
	}

	public static ImmutableMap<String, IGrade> getAllLatestGradesTo(InternetAddress recipient) {
		return Multimaps.asMap(getAllGradesTo(recipient)).entrySet().stream().collect(ImmutableMap.toImmutableMap(
				Map.Entry::getKey, e -> ((ImmutableSet<IGrade>) e.getValue()).asList().reverse().get(0)));
	}
}
