package io.github.oliviercailloux.grade.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.email.UncheckedMessagingException.MESSAGING_UNCHECKER;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.json.JsonObject;
import javax.json.bind.adapter.JsonbAdapter;
import javax.mail.Address;
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
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Table.Cell;
import com.google.common.io.CharStreams;
import com.google.common.math.Stats;

import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.email.EmailAddressAndPersonal;
import io.github.oliviercailloux.email.ImapSearchPredicate;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.json.JsonbUtils;

public class GradesInEmails implements AutoCloseable {
	public static final String FILE_NAME = "data.json";

	public static final String MIME_SUBTYPE = "json";
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GradesInEmails.class);

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

	public static EmailAddressAndPersonal asAddress(StudentOnGitHubKnown student) {
		checkNotNull(student);
		final String emailStr = student.asStudentOnMyCourse().getEmail();
		// final String emailStr = "olivier.cailloux@INVALIDdauphine.fr";
		return EmailAddressAndPersonal.given(emailStr, student.getFirstName() + " " + student.getLastName());
	}

	public static Email asEmail(EmailAddressAndPersonal studentAddress, String gradeName, IGrade grade, Stats stats,
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
		/**
		 * Useful when a class has changed path and thus can’t be found anymore when
		 * reading the grade from the e-mail.
		 */
		return new GradesInEmails(JsonGrade.usingSimpleCriteria().instanceAsAdapter());
	}

	public static GradesInEmails usingSophisticatedCriteria() {
		return new GradesInEmails(JsonGrade.usingSophisticatedCriteria().instanceAsAdapter());
	}

	private final Emailer emailer;

	private Folder folder;

	private JsonbAdapter<IGrade, JsonObject> jsonGradeAdapter;

	private GradesInEmails(JsonbAdapter<IGrade, JsonObject> jsonGradeAdapter) {
		this.jsonGradeAdapter = checkNotNull(jsonGradeAdapter);
		emailer = Emailer.instance();
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

	private Optional<GradeMessage> getGrade(Message source) {
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
					.filter(MESSAGING_UNCHECKER.wrapPredicate(p -> p.isMimeType("text/" + MIME_SUBTYPE)))
					.filter(MESSAGING_UNCHECKER.wrapPredicate(p -> p.getFileName().equals(FILE_NAME)))
					.collect(MoreCollectors.toOptional());
			final Optional<IGrade> grade = matchingPart.map(this::getGrade);
			return grade.map(g -> GradeMessage.given(g, source));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (MessagingException e) {
			throw new IllegalStateException(e);
		}
	}

	private IGrade getGrade(MimeBodyPart part) {
		final IGrade grade;
		try (InputStreamReader reader = new InputStreamReader((InputStream) part.getContent(),
				StandardCharsets.UTF_8)) {
			final String content = CharStreams.toString(reader);
			grade = JsonbUtils.fromJson(content, IGrade.class, jsonGradeAdapter);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (MessagingException e) {
			throw new IllegalStateException(e);
		}
		return grade;
	}

	private String getGradeSubject(Message message) {
		final String subject = MESSAGING_UNCHECKER.getUsing(message::getSubject);
		verify(subject.startsWith("Grade "));
		final String gradeSubject = subject.substring(6);
		return gradeSubject;
	}

	private EmailAddress getUniqueRecipientAmong(Message message, Set<EmailAddress> recipients) {
		final ImmutableSet<Address> seen = ImmutableSet
				.copyOf(MESSAGING_UNCHECKER.getUsing(() -> message.getRecipients(RecipientType.TO)));
		final ImmutableSet<EmailAddress> intersection = recipients.stream()
				.filter(r -> seen.contains(r.asInternetAddress())).collect(ImmutableSet.toImmutableSet());
		verify(!intersection.isEmpty(), seen.toString());
		checkState(intersection.size() <= 1);
		return Iterables.getOnlyElement(intersection);
	}

	public ImmutableSetMultimap<String, IGrade> getGradesTo(EmailAddress recipient) {
		checkState(folder != null);

		final ImmutableSortedSet<GradeMessage> matchingSorted = getAllGradeMessagesTo(ImmutableSet.of(recipient),
				"Grade ".toLowerCase());

		final ImmutableSetMultimap<String, IGrade> grades = matchingSorted.stream().collect(ImmutableSetMultimap
				.toImmutableSetMultimap(g -> getGradeSubject(g.getMessage()), GradeMessage::getGrade));

		LOGGER.info("Got all grades to {} ({}).", recipient, grades.size());
		return grades;
	}

	private ImmutableSortedSet<GradeMessage> getAllGradeMessagesTo(Set<EmailAddress> recipients,
			String subjectPattern) {
		final ImapSearchPredicate subjectContains = ImapSearchPredicate.subjectContains(subjectPattern);
		final ImmutableSet<ImapSearchPredicate> predicates = recipients.stream()
				.map(r -> ImapSearchPredicate.recipientAddressEquals(RecipientType.TO, r.getAddress()))
				.collect(ImmutableSet.toImmutableSet());
		final ImapSearchPredicate matchesOneOfThose = ImapSearchPredicate.orList(predicates);
		final ImmutableSet<Message> matching;
		if (recipients.size() <= 1) {
			final ImapSearchPredicate searchTerm = subjectContains.andSatisfy(matchesOneOfThose);
			matching = emailer.searchIn(searchTerm, folder);
		} else {
			/**
			 * We search everything because Zoho (and, I suspect, many others) does not
			 * implement this correctly.
			 */
			final ImapSearchPredicate searchTerm = subjectContains;
			final ImmutableSet<Message> matchingWidened = emailer.searchIn(searchTerm, folder);
			LOGGER.debug("Got all '{}' messages ({}).", subjectPattern, matchingWidened.size());
			matching = matchingWidened.stream().filter(matchesOneOfThose.getPredicate())
					.collect(ImmutableSet.toImmutableSet());
			LOGGER.debug("Got all '{}' matching messages ({}).", subjectPattern, matching.size());
		}
		final ImmutableSet<GradeMessage> matchingGrades = matching.stream().map(this::getGrade)
				.filter(Optional::isPresent).map(Optional::get).collect(ImmutableSet.toImmutableSet());
		LOGGER.debug("Got all '{}' matching grades to {} ({}).", subjectPattern, recipients, matchingGrades.size());
		final Comparator<GradeMessage> comparing = Comparator.comparing(
				g -> MESSAGING_UNCHECKER.<Message, Date>wrapFunction(m -> m.getSentDate()).apply(g.getMessage()));
		final ImmutableSortedSet<GradeMessage> matchingSorted = matchingGrades.stream().collect(ImmutableSortedSet
				.toImmutableSortedSet(comparing.thenComparing(g -> g.getMessage().getMessageNumber())));
		LOGGER.debug("Got all '{}' messages to {} ({}).", subjectPattern, recipients, matchingSorted.size());
		return matchingSorted;
	}

	public ImmutableTable<EmailAddress, String, IGrade> getLastGradesTo(Set<EmailAddress> recipients) {
		checkState(folder != null);

		final ImmutableTable<EmailAddress, String, IGrade> grades = getLastGradesToInternal(recipients,
				"Grade ".toLowerCase());
		return grades;
	}

	private ImmutableTable<EmailAddress, String, IGrade> getLastGradesToInternal(Set<EmailAddress> recipients,
			String subjectPattern) {
		final ImmutableSortedSet<GradeMessage> matchingSorted = getAllGradeMessagesTo(recipients, subjectPattern);
		final ImmutableTable<EmailAddress, String, GradeMessage> gradeMessages = matchingSorted.stream()
				.collect(ImmutableTable.toImmutableTable(g -> getUniqueRecipientAmong(g.getMessage(), recipients),
						g -> getGradeSubject(g.getMessage()), g -> g,
						(g1, g2) -> Stream.of(g1, g2).max(matchingSorted.comparator()).get()));
		final ImmutableTable<EmailAddress, String, IGrade> grades = gradeMessages.cellSet().stream().collect(
				ImmutableTable.toImmutableTable(Cell::getRowKey, Cell::getColumnKey, c -> c.getValue().getGrade()));
		LOGGER.info("Got all '{}' grades to {} ({}).", subjectPattern, recipients, grades.size());
		return grades;
	}

	public Optional<IGrade> getLastGradeTo(EmailAddress recipient, String prefix) {
		return Optional.ofNullable(getLastGradesTo(ImmutableSet.of(recipient), prefix).get(recipient));
	}

	public ImmutableMap<EmailAddress, IGrade> getLastGradesTo(Set<EmailAddress> recipients, String prefix) {
		checkState(folder != null);
		return getLastGradesToInternal(recipients, ("Grade " + prefix).toLowerCase()).column(prefix);
	}

	@Override
	public void close() {
		emailer.close();
	}
}
