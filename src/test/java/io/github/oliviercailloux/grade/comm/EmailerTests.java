package io.github.oliviercailloux.grade.comm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Range;
import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.email.EmailAddressAndPersonal;
import io.github.oliviercailloux.email.ImapSearchPredicate;
import io.github.oliviercailloux.grade.GradeTestsHelper;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.xml.HtmlDocument;
import io.github.oliviercailloux.xml.XmlUtils;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import javax.json.Json;
import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.search.AndTerm;
import javax.mail.search.OrTerm;
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.RecipientTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SubjectTerm;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class EmailerTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(EmailerTests.class);

	public static final String SENT_FOLDER = "Éléments envoyés";
	public static final String TRASH_FOLDER = "Éléments supprimés";

	public static void main(String[] args) throws Exception {
		sendDummyEmails();
	}

	static void sendDummyEmails() {
		final Document doc1 = getTestDocument("First document");
		LOGGER.info("Doc1: {}.", XmlUtils.asString(doc1));
		final EmailAddressAndPersonal to1 = EmailAddressAndPersonal.given("olivier.cailloux@gmail.com", "O.C");
		final Email email1 = Email.withDocumentAndFile(doc1, "data.json",
				Json.createObjectBuilder().add("jsonint", 1).build().toString(), "json", to1);

		final WeightingGrade grade = GradeTestsHelper.getComplexGradeWithPenalty();
		final Document doc2 = HtmlGrades.asHtml(grade, "Ze grade");
		final EmailAddressAndPersonal to2 = EmailAddressAndPersonal.given("oliviercailloux@gmail.com", "OC");
		final Email email2 = Email.withDocumentAndFile(doc2, "data.json", JsonGrade.asJson(grade).toString(), "json",
				to2);

		try (Emailer emailer = Emailer.instance()) {
			emailer.connectToStore(Emailer.getOutlookImapSession(), EmailerDauphineHelper.USERNAME_DAUPHINE,
					EmailerDauphineHelper.getDauphineToken());
			emailer.send(ImmutableSet.of(email1, email2), EmailerDauphineHelper.FROM);

//		EMailer.saveInto(SENT_FOLDER);
//		EMailer.saveInto(TRASH_FOLDER);
		}
	}

	public static Document getTestDocument(String title) {
		final HtmlDocument doc = HtmlDocument.newInstance();
		doc.setTitle(title);
		final Element h1 = doc.createXhtmlElement("h1");
		doc.getBody().appendChild(h1);
		h1.appendChild(doc.getDocument().createTextNode("H One"));
		final Element p = doc.createXhtmlElement("p");
		doc.getBody().appendChild(p);
		p.appendChild(doc.getDocument().createTextNode("Content"));
		final Element ul = doc.createXhtmlElement("ul");
		doc.getBody().appendChild(ul);
		final Element li = doc.createXhtmlElement("li");
		ul.appendChild(li);
		li.appendChild(doc.getDocument().createTextNode("It1"));
		doc.validate();
		return doc.getDocument();
	}

	@Test
	void testOutlookBug() throws Exception {
		try (Emailer emailer = Emailer.instance()) {
			emailer.connectToStore(Emailer.getOutlookImapSession(), EmailerDauphineHelper.USERNAME_DAUPHINE,
					EmailerDauphineHelper.getDauphineToken());
			@SuppressWarnings("resource")
			final Folder folder = emailer.getFolder("Éléments envoyés");

			/**
			 * Shouldn’t throw. (Finds a message actually sent to
			 * olivier.cailloux@lamsade.dauphine.fr.)
			 */
			assertThrows(VerifyException.class, () -> emailer.searchIn(folder,
					ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO, "olivier.cailloux@dauphine.")));

			/**
			 * Shouldn’t throw. (Finds a message actually sent to
			 * bull-ia-subscribe-olivier.cailloux=dauphine.fr@gdria.fr.)
			 */
			assertThrows(VerifyException.class, () -> emailer.searchIn(folder,
					ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO, "olivier.cailloux@dauphine.")
							.orSatisfy(ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO,
									"olivier.cailloux@lamsade.dauphine.")))
					.isEmpty());

			final ImmutableSet<Message> toMyselfAndOthers = emailer.searchIn(folder,
					ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO, "olivier.cailloux@dauphine.")
							.orSatisfy(ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO,
									"olivier.cailloux@lamsade.dauphine."))
							.orSatisfy(ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO,
									"olivier.cailloux=dauphine."))
							.orSatisfy(ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO,
									"olivier.cailloux-dauphine."))
							.orSatisfy(ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO,
									"olivier.cailloux=lamsade.dauphine.")));
			assertFalse(toMyselfAndOthers.isEmpty());

			final ImapSearchPredicate containsMyAddress = ImapSearchPredicate
					.recipientFullAddressContains(RecipientType.TO, "olivier.cailloux@dauphine.f");
			assertTrue(toMyselfAndOthers.stream().anyMatch(containsMyAddress));

			/** Shouldn’t be empty. */
			assertTrue(emailer.searchIn(folder, containsMyAddress).isEmpty());

			/** Shouldn’t be empty. */
			assertTrue(emailer.searchIn(folder,
					ImapSearchPredicate.recipientAddressEquals(RecipientType.TO, "olivier.cailloux@dauphine.fr"))
					.isEmpty());
		}
	}

	/**
	 * https://github.com/eclipse-ee4j/mail/issues/425
	 */
	@Test
	void testOutlookBugDirect() throws Exception {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imap");
		props.setProperty("mail.host", "outlook.office365.com");
		props.setProperty("mail.imap.connectiontimeout", "2000");
		props.setProperty("mail.imap.timeout", "60*1000");
		props.setProperty("mail.imap.connectionpooltimeout", "10");
		props.setProperty("mail.imap.ssl.enable", "true");
		props.setProperty("mail.imap.ssl.checkserveridentity", "true");
//		props.setProperty("mail.debug", "true");
		final Session session = Session.getInstance(props);
		try (Store store = session.getStore()) {
			LOGGER.info("Connecting.");
			store.connect(EmailerDauphineHelper.USERNAME_DAUPHINE, EmailerDauphineHelper.getDauphineToken());
			try (Folder folder = store.getFolder("Éléments envoyés")) {
				folder.open(Folder.READ_ONLY);
				final Message[] messages6068 = folder.getMessages(6068, 6068);
				assertEquals(1, messages6068.length);
				final Message messageToDauphine = messages6068[0];
				messageToDauphine.getAllHeaders();

				final Address[] recipients = messageToDauphine.getRecipients(RecipientType.TO);
				assertEquals(1, recipients.length);
				final InternetAddress recipient = (InternetAddress) recipients[0];
				assertEquals("olivier.cailloux@dauphine.fr", recipient.getAddress());
				LOGGER.info(String.format("Message %s dated %s to %s.", messageToDauphine.getMessageNumber(),
						messageToDauphine.getSentDate(), recipient));
				{
					final Message[] messagesToPartialRecipientStringTerm = folder
							.search(new RecipientStringTerm(RecipientType.TO, "olivier.cailloux"));
					/** Succeeds */
					assertNotEquals(0, messagesToPartialRecipientStringTerm.length);
					assertEquals(1, Arrays.stream(messagesToPartialRecipientStringTerm)
							.filter(m -> m.getMessageNumber() == 6068).count());
				}
				{
					final Message[] messagesToPartialRecipientStringTerm = folder
							.search(new RecipientStringTerm(RecipientType.TO, "olivier.cailloux@dauphine."));
					/** Succeeds */
					assertNotEquals(0, messagesToPartialRecipientStringTerm.length);
					assertEquals(1, Arrays.stream(messagesToPartialRecipientStringTerm)
							.filter(m -> m.getMessageNumber() == 6068).count());
				}
				{
					final Message[] messagesToThatRecipientStringTerm = folder
							.search(new RecipientStringTerm(RecipientType.TO, "olivier.cailloux@dauphine.fr"));
					/** Should fail! */
					assertEquals(0, messagesToThatRecipientStringTerm.length);
				}
				{
					final Message[] messagesToThatRecipient = folder
							.search(new RecipientTerm(RecipientType.TO, recipient));
					/** Should fail! */
					assertEquals(0, messagesToThatRecipient.length);
				}
				{
					final Message[] messagesToThatRecipientStringTerm = folder
							.search(new RecipientStringTerm(RecipientType.TO, "olivier cailloux"));
					assertTrue(messagesToThatRecipientStringTerm.length > 10);
				}
			}
		}
	}

	@Test
	void testGmailBugDirect() throws Exception {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imap");
		props.setProperty("mail.host", "imap.gmail.com");
		props.setProperty("mail.imap.connectiontimeout", "2000");
		props.setProperty("mail.imap.timeout", "60*1000");
		props.setProperty("mail.imap.connectionpooltimeout", "10");
		props.setProperty("mail.imap.ssl.enable", "true");
		props.setProperty("mail.imap.ssl.checkserveridentity", "true");
		// props.setProperty("mail.debug", "true");
		final Session session = Session.getInstance(props);
		try (Store store = session.getStore()) {
			LOGGER.info("Connecting.");
			store.connect(EmailerDauphineHelper.USERNAME_OTHERS, EmailerDauphineHelper.getGmailToken());
//			try (Folder folder = store.getFolder("Grades")) {
			try (Folder folder = store.getFolder("[Gmail]/Tous les messages")) {
				final int no = 69245;
				folder.open(Folder.READ_ONLY);
//				final Message[] messagesFound = folder.search(new SubjectTerm("Test depuis Gmail vers LAMSADE"));
//				logMessagesRecipients(ImmutableSet.copyOf(messagesFound));
				final Message[] messagesFound = folder.getMessages(no, no);
				assertEquals(1, messagesFound.length);
				final Message messageToCAILLOUX = messagesFound[0];

				final Address[] recipients = messageToCAILLOUX.getRecipients(RecipientType.TO);
				assertEquals(1, recipients.length);
				final InternetAddress recipient = (InternetAddress) recipients[0];
				assertEquals("olivier.cailloux@lamsade.dauphine.fr", recipient.getAddress());
				LOGGER.info(String.format("Message %s dated %s to %s.", messageToCAILLOUX.getMessageNumber(),
						messageToCAILLOUX.getSentDate(), recipient));
				{
					final Message[] messagesToPartialRecipientStringTerm = folder
							.search(new RecipientStringTerm(RecipientType.TO, "olivier.cailloux"));
					assertNotEquals(0, messagesToPartialRecipientStringTerm.length);
					assertEquals(1, Arrays.stream(messagesToPartialRecipientStringTerm)
							.filter(m -> m.getMessageNumber() == no).count());
				}
				{
					final Message[] messagesToPartialRecipientStringTerm = folder
							.search(new RecipientStringTerm(RecipientType.TO, "olivier.cailloux@lamsade.dauphine."));
					/**
					 * Arguably the spec is unprecise here, so both match and miss seem acceptable
					 * (“Messages that contain the specified string in the envelope structure's TO
					 * field.”)
					 */
					assertEquals(0, messagesToPartialRecipientStringTerm.length);
				}
				{
					final Message[] messagesToThatRecipientStringTerm = folder.search(
							new RecipientStringTerm(RecipientType.TO, " <olivier.cailloux@lamsade.dauphine.fr>"));
					assertEquals(1, Arrays.stream(messagesToThatRecipientStringTerm)
							.filter(m -> m.getMessageNumber() == no).count());
				}
				{
					final Message[] messagesToThatRecipientStringTerm = folder
							.search(new RecipientStringTerm(RecipientType.TO, "Olivier Cailloux <"));
					assertEquals(1, Arrays.stream(messagesToThatRecipientStringTerm)
							.filter(m -> m.getMessageNumber() == no).count());
				}
				{
					final Message[] messagesToThatRecipientStringTerm = folder
							.search(new RecipientStringTerm(RecipientType.TO, "Olivier Cailloux <o"));
					assertEquals(0, messagesToThatRecipientStringTerm.length);
				}
				{
					final Message[] messagesToThatRecipientStringTerm = folder.search(new RecipientStringTerm(
							RecipientType.TO, "Olivier Cailloux <olivier.cailloux@lamsade.dauphine.fr>"));
					/** Should fail! */
					assertEquals(0, messagesToThatRecipientStringTerm.length);
				}
				{
					final Message[] messagesToThatRecipient = folder
							.search(new RecipientTerm(RecipientType.TO, recipient));
					/**
					 * Should fail! (This search actually has the same effect as the previous one.)
					 */
					assertEquals(0, messagesToThatRecipient.length);
				}
				{
					final Message[] messagesToThatRecipientStringTerm = folder
							.search(new RecipientStringTerm(RecipientType.TO, "olivier.cailloux@lamsade.dauphine.fr"));
					assertEquals(1, Arrays.stream(messagesToThatRecipientStringTerm)
							.filter(m -> m.getMessageNumber() == no).count());
				}
				{
					final Message[] messagesToThatRecipientStringTerm = folder
							.search(new RecipientStringTerm(RecipientType.TO, "Olivier Cailloux"));
					assertEquals(1, Arrays.stream(messagesToThatRecipientStringTerm)
							.filter(m -> m.getMessageNumber() == no).count());
				}
			}
		}
	}

	@Test
	void testZoho() throws Exception {
		try (Emailer emailer = Emailer.instance()) {
			emailer.connectToStore(Emailer.getZohoImapSession(), EmailerDauphineHelper.USERNAME_OTHERS,
					EmailerDauphineHelper.getZohoToken());
			@SuppressWarnings("resource")
			final Folder folder = emailer.getFolder("Tests");

			final ImmutableSet<Message> all = emailer.searchIn(folder, ImapSearchPredicate.TRUE);
			assertEquals(2, all.size());
			logMessagesRecipients(all);

			assertEquals(1, emailer.searchIn(folder, ImapSearchPredicate.recipientAddressEquals(RecipientType.TO,
					"olivier.cailloux@lamsade.dauphine.fr")).size());
			assertEquals(0, emailer.searchIn(folder,
					ImapSearchPredicate.recipientAddressEquals(RecipientType.TO, "olivier.cailloux@lamsade.dauphine"))
					.size());
			assertEquals(1, emailer.searchIn(folder, ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO,
					"olivier.cailloux@lamsade.dauphine.fr")).size());
			assertEquals(1, emailer.searchIn(folder, ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO,
					"olivier.cailloux@lamsade.dauphine.")).size());
			assertEquals(1, emailer.searchIn(folder, ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO,
					" <olivier.cailloux@lamsade.dauphine.fr>")).size());
			assertEquals(1, emailer.searchIn(folder, ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO,
					"olivier cailloux <olivier.cailloux@lamsade.dauphine.fr>")).size());

			/**
			 * Should not throw. Finds an e-mail sent to "xxxx
			 * <olivier.cailloux@INVALIDdauphine.fr>" (real name hidden for privacy
			 * reasons).
			 */
			assertThrows(VerifyException.class,
					() -> emailer.searchIn(folder,
							ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO, "olivier cailloux"))
							.size());
			assertThrows(VerifyException.class,
					() -> emailer.searchIn(folder,
							ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO, "olivier cailloux <"))
							.size());
			/** Should equal 1. */
			assertEquals(0,
					emailer.searchIn(folder,
							ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO, "olivier cailloux <o"))
							.size());
		}
	}

	private void logMessagesRecipients(Set<Message> messages) throws MessagingException {
		for (Message message : messages) {
			LOGGER.info("Message {} recipients: {}.", message.getMessageNumber(),
					ImmutableSet
							.copyOf(message.getAllRecipients()).stream().map(a -> (InternetAddress) a).map(a -> "Type: "
									+ a.getType() + "; Personal: " + a.getPersonal() + "; Address: " + a.getAddress())
							.collect(ImmutableSet.toImmutableSet()));
		}
	}

	@Test
	void testZohoBug() throws Exception {
		try (Emailer emailer = Emailer.instance()) {
			emailer.connectToStore(Emailer.getZohoImapSession(), EmailerDauphineHelper.USERNAME_OTHERS,
					EmailerDauphineHelper.getZohoToken());
			@SuppressWarnings("resource")
			final Folder folder = emailer.getFolder("Grades");

			final Message[] asArray = folder.search(new SubjectTerm("grades git-br"));
			final ImmutableSet<Message> found = ImmutableSet.copyOf(asArray);
			/** Should be empty. */
			assertFalse(found.isEmpty());
			LOGGER.info(Emailer.getDescription(found.iterator().next()));
		}
	}

	@Test
	@Disabled("Old grade format")
	void testZohoFetch() throws Exception {
		try (Emailer emailer = Emailer.instance()) {
			emailer.connectToStore(Emailer.getZohoImapSession(), EmailerDauphineHelper.USERNAME_OTHERS,
					EmailerDauphineHelper.getZohoToken());
			@SuppressWarnings("resource")
			final Folder folder = emailer.getFolder("Grades");

			final Message[] asArray = folder.search(new SubjectTerm("grades git-br"));
			final ImmutableSet<Message> found = ImmutableSet.copyOf(asArray);
			LOGGER.info("Found {} messages.", found.size());
			emailer.fetchMessages(folder, found);
			LOGGER.info("Fetched.");
			try (GradesInEmails gradesInEmails = GradesInEmails.newInstance()) {
				for (Message message : found) {
					LOGGER.info("Getting grade for {}.", message.getMessageNumber());
					final IGrade grade = gradesInEmails.toGrade(message).get();
					LOGGER.info("Grade: {}.", grade);
				}
			}

			/** Should be empty. */
			assertFalse(found.isEmpty());
			LOGGER.debug(Emailer.getDescription(found.iterator().next()));
		}
	}

	@Test
	void testZohoAndOr() throws Exception {
		try (Emailer emailer = Emailer.instance()) {
//			emailer.setDebug(true);
			emailer.connectToStore(Emailer.getZohoImapSession(), EmailerDauphineHelper.USERNAME_OTHERS,
					EmailerDauphineHelper.getZohoToken());
			@SuppressWarnings("resource")
			final Folder folder = emailer.getFolder("Grades");

			final SearchTerm r1 = new RecipientTerm(RecipientType.TO,
					new InternetAddress("olivier.cailloux@lamsade.dauphine.fr"));
			final SearchTerm r2 = new RecipientTerm(RecipientType.TO,
					new InternetAddress("olivier.cailloux@notexistdauphine.fr"));
			final SearchTerm s = new SubjectTerm("grade commit");
			{
				/**
				 * Searches for A3 SEARCH SUBJECT "grade commit" TO
				 * olivier.cailloux@lamsade.dauphine.fr ALL
				 */
				final Message[] asArray = folder.search(new AndTerm(s, r1));
				final ImmutableSet<Message> found = ImmutableSet.copyOf(asArray);
				assertEquals(1, found.size());
			}
			{
				final SearchTerm r12 = new OrTerm(r1, r2);
				final SearchTerm sAndR12 = new AndTerm(s, r12);
				final Message[] asArray = folder.search(sAndR12);
				final ImmutableSet<Message> found = ImmutableSet.copyOf(asArray);
				/**
				 * Searches for SEARCH SUBJECT "grade commit" OR TO
				 * olivier.cailloux@lamsade.dauphine.fr TO olivier.cailloux@notexistdauphine.fr
				 * ALL. Used to fail (return zero matches) but was corrected following
				 * discussions with Zoho team somewhere around July 2020.
				 */
				assertEquals(1, found.size());
			}
		}
	}

	@Test
	void testRetrieveInexistant() throws Exception {
		try (GradesInEmails sendEmails = GradesInEmails.newInstance()) {
			sendEmails.getEmailer().connectToStore(Emailer.getZohoImapSession(), EmailerDauphineHelper.USERNAME_OTHERS,
					EmailerDauphineHelper.getZohoToken());
			sendEmails.setFolder(sendEmails.getEmailer().getFolder("Grades"));
			final EmailAddress inexistant = EmailAddress.given("olivier.cailloux@inexistant.fr");
			assertEquals(Optional.empty(), sendEmails.getLastGradeTo(inexistant, "git-br"));
			sendEmails.filterRecipients(ImmutableSet.of(inexistant));
			assertEquals(ImmutableTable.of(), sendEmails.getLastGrades());
		}
	}

	@Test
	@Disabled("Old grade format")
	void testRetrieveExistant() throws Exception {
		try (GradesInEmails sendEmails = GradesInEmails.newInstance()) {
			sendEmails.getEmailer().connectToStore(Emailer.getZohoImapSession(), EmailerDauphineHelper.USERNAME_OTHERS,
					EmailerDauphineHelper.getZohoToken());
			sendEmails.setFolder(sendEmails.getEmailer().getFolder("Grades"));

			final EmailAddress existant = EmailAddress.given("olivier.cailloux@invaliddauphine.fr");
			final EmailAddress inexistant = EmailAddress.given("olivier.cailloux@inexistant.fr");

			sendEmails.filterRecipients(ImmutableSet.of(inexistant, existant));
			final ImmutableMap<EmailAddress, IGrade> gradeT = sendEmails.getLastGrades("git-br");
			assertEquals(8.88d / 20d, gradeT.get(existant).getPoints(), 0.001d);

			final Optional<IGrade> grade = sendEmails.getLastGradeTo(existant, "git-br");
			assertEquals(8.88d / 20d, grade.get().getPoints(), 0.001d);

			sendEmails.filterRecipients(ImmutableSet.of(existant));
			final ImmutableMap<EmailAddress, IGrade> grades = sendEmails.getLastGrades("git-br");
			assertEquals(8.88d / 20d, grades.get(existant).getPoints(), 0.001d);

			sendEmails.filterSent(
					Range.closed(Instant.parse("2001-01-01T00:00:00.00Z"), Instant.parse("2020-06-01T00:00:00.00Z")));
			final ImmutableMap<EmailAddress, IGrade> gradesInPeriod = sendEmails.getLastGrades("git-br");
			assertEquals(8.88d / 20d, gradesInPeriod.get(existant).getPoints(), 0.001d);

			sendEmails.filterSent(Range.atMost(Instant.parse("2001-01-01T00:00:00.00Z")));
			final ImmutableMap<EmailAddress, IGrade> gradesOld = sendEmails.getLastGrades("git-br");
			assertEquals(ImmutableMap.of(), gradesOld);
		}
	}

	@Test
	@Disabled("Old grade format")
	void testRetrieveMultOneMatch() throws Exception {
		try (GradesInEmails sendEmails = GradesInEmails.newInstance()) {
			sendEmails.getEmailer().connectToStore(Emailer.getZohoImapSession(), EmailerDauphineHelper.USERNAME_OTHERS,
					EmailerDauphineHelper.getZohoToken());
			sendEmails.setFolder(sendEmails.getEmailer().getFolder("Grades"));
			final EmailAddress inexistant = EmailAddress.given("olivier.cailloux@inexistant.fr");
			final EmailAddress existant = EmailAddress.given("olivier.cailloux@invaliddauphine.fr");
			sendEmails.filterRecipients(ImmutableSet.of(inexistant, existant));
			final ImmutableMap<EmailAddress, IGrade> grades = sendEmails.getLastGrades("git-br");
			assertEquals(ImmutableSet.of(existant), grades.keySet());
		}
	}

	@Test
	@Disabled("Old grade format")
	void testRetrieveMultTwoMatches() throws Exception {
		try (GradesInEmails sendEmails = GradesInEmails.newInstance()) {
			sendEmails.getEmailer().connectToStore(Emailer.getZohoImapSession(), EmailerDauphineHelper.USERNAME_OTHERS,
					EmailerDauphineHelper.getZohoToken());
			sendEmails.setFolder(sendEmails.getEmailer().getFolder("Grades"));
			final EmailAddress lam = EmailAddress.given("olivier.cailloux@lamsade.dauphine.fr");
			final EmailAddress dau = EmailAddress.given("olivier.cailloux@invaliddauphine.fr");
			final ImmutableSet<EmailAddress> lamSingleton = ImmutableSet.of(lam);
			sendEmails.filterRecipients(lamSingleton);
			assertEquals(lamSingleton, sendEmails.getLastGrades("commit").keySet());
			final ImmutableSet<EmailAddress> lamdau = ImmutableSet.of(lam, dau);
			sendEmails.filterRecipients(lamdau);
			assertEquals(lamdau, sendEmails.getLastGrades("commit").keySet());
		}
	}

	@Test
	void testAlwaysFalsePredicate() throws Exception {
		try (Emailer emailer = Emailer.instance()) {
			emailer.connectToStore(Emailer.getZohoImapSession(), EmailerDauphineHelper.USERNAME_OTHERS,
					EmailerDauphineHelper.getZohoToken());
			@SuppressWarnings("resource")
			final Folder folder = emailer.getFolder("Grades");

			final ImmutableSet<Message> none = emailer.searchIn(folder, ImapSearchPredicate.orList(ImmutableList.of()));
			assertEquals(ImmutableSet.of(), none);
		}
	}

}
