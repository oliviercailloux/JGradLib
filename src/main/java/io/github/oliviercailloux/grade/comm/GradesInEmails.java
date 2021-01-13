package io.github.oliviercailloux.grade.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.email.UncheckedMessagingException.MESSAGING_UNCHECKER;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.adapter.JsonbAdapter;
import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
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
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Range;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.common.io.CharStreams;
import com.google.common.math.Stats;

import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.email.EmailAddressAndPersonal;
import io.github.oliviercailloux.email.ImapSearchPredicate;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;

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
//		final String emailStr = student.asStudentOnMyCourse().getEmail();
		final String emailStr = "olivier.cailloux@invalid-dauphine.fr";
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
		return new GradesInEmails(JsonGrade.create().instanceAsAdapter());
	}

	private final Emailer emailer;

	private Folder folder;

	private ImmutableSet<EmailAddress> recipientsFilter;
	private Range<Instant> sentFilter;

	private final Jsonb jsonb;

	private GradesInEmails(JsonbAdapter<IGrade, JsonObject> jsonGradeAdapter) {
		emailer = Emailer.instance();
		folder = null;
		recipientsFilter = null;
		sentFilter = null;
		jsonb = JsonbBuilder
				.create(new JsonbConfig().withAdapters(checkNotNull(jsonGradeAdapter)).withFormatting(true));
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

	/**
	 * @param recipients <code>null</code> for no filter
	 */
	public void filterRecipients(Set<EmailAddress> recipients) {
		recipientsFilter = recipients == null ? null : ImmutableSet.copyOf(recipients);
	}

	public void filterSent(Range<Instant> filter) {
		sentFilter = filter;
	}

	Optional<IGrade> toGrade(Message source) {
		return getGradeMessage(source).map(GradeMessage::getGrade);
	}

	private Optional<GradeMessage> getGradeMessage(Message source) {
		try {
			LOGGER.debug("Getting GradeMessage from message {}, content type: {}.", source.getMessageNumber(),
					source.getContentType());
			if (!(source instanceof MimeMessage)) {
				return Optional.empty();
			}
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
		try (InputStreamReader reader = new InputStreamReader(part.getInputStream(), StandardCharsets.UTF_8)) {
			final String content = CharStreams.toString(reader);
			grade = jsonb.fromJson(content, IGrade.class);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (MessagingException e) {
			throw new IllegalStateException(e);
		}
		return grade;
	}

	private String getGradeSubject(Message message) {
		final String subject = MESSAGING_UNCHECKER.getUsing(message::getSubject);
		verify(subject.startsWith("Grade "), subject);
		final String gradeSubject = subject.substring(6);
		return gradeSubject;
	}

	/**
	 * @param recipients <code>null</code> for no filter.
	 */
	private EmailAddress getUniqueRecipientAmong(Message message, Set<EmailAddress> recipients) {
		final ImmutableSet<Address> seen = ImmutableSet
				.copyOf(MESSAGING_UNCHECKER.getUsing(() -> message.getRecipients(RecipientType.TO)));
		final ImmutableSet<EmailAddress> intersection = (recipients == null
				? seen.stream().map(a -> (InternetAddress) a).map(a -> EmailAddress.given(a.getAddress()))
				: recipients.stream().filter(r -> seen.contains(r.asInternetAddress())))
						.collect(ImmutableSet.toImmutableSet());
		verify(!intersection.isEmpty(), seen.toString());
		checkState(intersection.size() <= 1);
		return Iterables.getOnlyElement(intersection);
	}

	/**
	 * @param recipients <code>null</code> for no filter.
	 */
	private ImmutableSet<Message> getMessagesTo(Set<EmailAddress> recipients, String subjectStartsWith) {
		final ImapSearchPredicate subjectContains = ImapSearchPredicate.subjectContains(subjectStartsWith);
		final ImapSearchPredicate matchesAddress = recipients == null ? ImapSearchPredicate.TRUE
				: ImapSearchPredicate.orList(recipients.stream()
						.map(r -> ImapSearchPredicate.recipientAddressEquals(RecipientType.TO, r.getAddress()))
						.collect(ImmutableSet.toImmutableSet()));
		/**
		 * If too complex, we search everything, because Zoho (and, I suspect, many
		 * others) do not implement this correctly.
		 */
		final ImapSearchPredicate effectiveMatchesAddress = (recipients == null || recipients.size() >= 2)
				? ImapSearchPredicate.TRUE
				: matchesAddress;
		final ImapSearchPredicate sentWithin = sentFilter == null ? ImapSearchPredicate.TRUE
				: ImapSearchPredicate.sentWithin(sentFilter);

		final ImapSearchPredicate searchTerm = subjectContains.andSatisfy(effectiveMatchesAddress)
				.andSatisfy(sentWithin);

		/** We filter manually in all cases for simplicity of the code. */
		final ImmutableSet<Message> matchingWidened = emailer.searchIn(folder, searchTerm);
		LOGGER.debug("Got all '{}' messages ({}).", subjectStartsWith, matchingWidened.size());
		emailer.fetchHeaders(folder, matchingWidened);
		/**
		 * We also need to filter for subjects really starting with the predicate, not
		 * just containing it.
		 */
		final ImmutableSet<Message> matching = matchingWidened.stream().filter(matchesAddress.getPredicate()).filter(
				m -> MESSAGING_UNCHECKER.getUsing(() -> m.getSubject()).toLowerCase().startsWith(subjectStartsWith))
				.collect(ImmutableSet.toImmutableSet());
		LOGGER.debug("Got all '{}' matching messages ({}).", subjectStartsWith, matching.size());
		return matching;
	}

	public ImmutableTable<EmailAddress, String, IGrade> getLastGrades() {
		checkState(folder != null);

		final ImmutableTable<EmailAddress, String, IGrade> grades = getLastGradesToInternal(recipientsFilter,
				"Grade ".toLowerCase());
		return grades;
	}

	/**
	 * @param recipients <code>null</code> for no filter.
	 */
	private ImmutableTable<EmailAddress, String, IGrade> getLastGradesToInternal(Set<EmailAddress> recipients,
			String subjectPattern) {
		final ImmutableSet<Message> matching = getMessagesTo(recipients, subjectPattern);
		/**
		 * First, reduce the number of messages to fetch (which takes about 30 seconds
		 * for 500 messages).
		 */
		final Comparator<Message> comparing = Comparator
				.comparing(MESSAGING_UNCHECKER.wrapFunction(Message::getSentDate));
		final ImmutableTable<EmailAddress, String, Message> messages = matching.stream()
				.collect(ImmutableTable.toImmutableTable(m -> getUniqueRecipientAmong(m, recipients),
						m -> getGradeSubject(m), m -> m, (m1, m2) -> Stream.of(m1, m2).max(comparing).get()));

		emailer.fetchMessages(folder, ImmutableSet.copyOf(messages.values()));

		final Table<EmailAddress, String, Optional<IGrade>> gradesOpt = Tables.transformValues(messages, this::toGrade);
		return gradesOpt.cellSet().stream().filter(c -> c.getValue().isPresent()).collect(
				ImmutableTable.toImmutableTable(c -> c.getRowKey(), c -> c.getColumnKey(), o -> o.getValue().get()));
	}

	public Optional<IGrade> getLastGradeTo(EmailAddress recipient, String prefix) {
		checkArgument(recipientsFilter == null || recipientsFilter.contains(recipient));
		return Optional
				.ofNullable(getLastGradesToInternal(ImmutableSet.of(recipient), ("Grade " + prefix).toLowerCase())
						.column(prefix).get(recipient));
	}

	public ImmutableMap<EmailAddress, IGrade> getLastGrades(String prefix) {
		checkState(folder != null);
		return getLastGradesToInternal(recipientsFilter, ("Grade " + prefix).toLowerCase()).column(prefix);
	}

	@Override
	public void close() {
		emailer.close();
	}
}
