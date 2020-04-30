package io.github.oliviercailloux.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;

import javax.json.Json;
import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.RecipientTerm;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.grade.GradeTestsHelper;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.BetterEmailer;
import io.github.oliviercailloux.grade.comm.BetterEmailer.ImapSearchPredicate;
import io.github.oliviercailloux.grade.comm.Email;
import io.github.oliviercailloux.grade.comm.EmailerDauphineHelper;
import io.github.oliviercailloux.grade.comm.GradesInEmails;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.xml.HtmlDocument;
import io.github.oliviercailloux.xml.XmlUtils;

public class EmailerTests {
	public static final String SENT_FOLDER = "Éléments envoyés";
	public static final String TRASH_FOLDER = "Éléments supprimés";
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(EmailerTests.class);

	public static void main(String[] args) throws Exception {
		sendDummyEmails();
	}

	static void sendDummyEmails() {
		final Document doc1 = getTestDocument("First document");
		LOGGER.info("Doc1: {}.", XmlUtils.asString(doc1));
		final EmailAddress to1 = EmailAddress.given("olivier.cailloux@gmail.com", "O.C");
		final Email email1 = Email.withDocumentAndFile(doc1, "data.json",
				Json.createObjectBuilder().add("jsonint", 1).build().toString(), "json", to1);

		final WeightingGrade grade = GradeTestsHelper.getComplexGradeWithPenalty();
		final Document doc2 = HtmlGrades.asHtml(grade, "Ze grade");
		final EmailAddress to2 = EmailAddress.given("oliviercailloux@gmail.com", "OC");
		final Email email2 = Email.withDocumentAndFile(doc2, "data.json", JsonGrade.asJson(grade).toString(), "json",
				to2);

		try (BetterEmailer emailer = BetterEmailer.newInstance()) {
			emailer.connectToStore(BetterEmailer.getOutlookImapSession(), Emailer.USERNAME_DAUPHINE,
					EmailerDauphineHelper.getDauphineToken());
			final ImmutableSet<Message> sent = emailer.send(ImmutableSet.of(email1, email2),
					EmailerDauphineHelper.FROM);
			LOGGER.info("Sent {} messages.", sent.size());

//		EMailer.saveInto(sent, SENT_FOLDER);
//		EMailer.saveInto(sent, TRASH_FOLDER);
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
		try (BetterEmailer emailer = BetterEmailer.newInstance()) {
			emailer.connectToStore(BetterEmailer.getOutlookImapSession(), Emailer.USERNAME_DAUPHINE,
					EmailerDauphineHelper.getDauphineToken());
			@SuppressWarnings("resource")
			final Folder folder = emailer.getFolder("Éléments envoyés");

			/**
			 * Shouldn’t throw. (Finds a message actually sent to
			 * olivier.cailloux@lamsade.dauphine.fr.)
			 */
			assertThrows(VerifyException.class, () -> emailer.searchIn(BetterEmailer.ImapSearchPredicate
					.recipientFullAddressContains(RecipientType.TO, "olivier.cailloux@dauphine."), folder));

			/**
			 * Shouldn’t throw. (Finds a message actually sent to
			 * bull-ia-subscribe-olivier.cailloux=dauphine.fr@gdria.fr.)
			 */
			assertThrows(VerifyException.class,
					() -> emailer
							.searchIn(BetterEmailer.ImapSearchPredicate
									.recipientFullAddressContains(RecipientType.TO, "olivier.cailloux@dauphine.")
									.orSatisfy(BetterEmailer.ImapSearchPredicate.recipientFullAddressContains(
											RecipientType.TO, "olivier.cailloux@lamsade.dauphine.")),
									folder)
							.isEmpty());

			final ImmutableSet<Message> toMyselfAndOthers = emailer.searchIn(BetterEmailer.ImapSearchPredicate
					.recipientFullAddressContains(RecipientType.TO, "olivier.cailloux@dauphine.")
					.orSatisfy(BetterEmailer.ImapSearchPredicate
							.recipientFullAddressContains(RecipientType.TO, "olivier.cailloux@lamsade.dauphine.")
							.orSatisfy(BetterEmailer.ImapSearchPredicate
									.recipientFullAddressContains(RecipientType.TO, "olivier.cailloux=dauphine.")
									.orSatisfy(BetterEmailer.ImapSearchPredicate.recipientFullAddressContains(
											RecipientType.TO, "olivier.cailloux=lamsade.dauphine.")))),
					folder);
			assertFalse(toMyselfAndOthers.isEmpty());

			final ImapSearchPredicate containsMyAddress = BetterEmailer.ImapSearchPredicate
					.recipientFullAddressContains(RecipientType.TO, "olivier.cailloux@dauphine.f");
			assertTrue(toMyselfAndOthers.stream().anyMatch(containsMyAddress));

			/** Shouldn’t be empty. */
			assertTrue(emailer.searchIn(containsMyAddress, folder).isEmpty());

			/** Shouldn’t be empty. */
			assertTrue(emailer.searchIn(BetterEmailer.ImapSearchPredicate.recipientAddressEquals(RecipientType.TO,
					"olivier.cailloux@dauphine.fr"), folder).isEmpty());
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
			store.connect(Emailer.USERNAME_DAUPHINE, EmailerDauphineHelper.getDauphineToken());
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
			store.connect(Emailer.USERNAME_OTHERS, EmailerDauphineHelper.getGmailToken());
			try (Folder folder = store.getFolder("Grades")) {
				folder.open(Folder.READ_ONLY);
				final Message[] messages31 = folder.getMessages(31, 31);
				assertEquals(1, messages31.length);
				final Message messageToCAILLOUX = messages31[0];

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
							.filter(m -> m.getMessageNumber() == 31).count());
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
							.filter(m -> m.getMessageNumber() == 31).count());
				}
				{
					final Message[] messagesToThatRecipientStringTerm = folder
							.search(new RecipientStringTerm(RecipientType.TO, "Olivier CAILLOUX <"));
					assertEquals(1, Arrays.stream(messagesToThatRecipientStringTerm)
							.filter(m -> m.getMessageNumber() == 31).count());
				}
				{
					final Message[] messagesToThatRecipientStringTerm = folder
							.search(new RecipientStringTerm(RecipientType.TO, "Olivier CAILLOUX <o"));
					assertEquals(0, messagesToThatRecipientStringTerm.length);
				}
				{
					final Message[] messagesToThatRecipientStringTerm = folder.search(new RecipientStringTerm(
							RecipientType.TO, "Olivier CAILLOUX <olivier.cailloux@lamsade.dauphine.fr>"));
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
							.filter(m -> m.getMessageNumber() == 31).count());
				}
				{
					final Message[] messagesToThatRecipientStringTerm = folder
							.search(new RecipientStringTerm(RecipientType.TO, "Olivier CAILLOUX"));
					assertEquals(1, Arrays.stream(messagesToThatRecipientStringTerm)
							.filter(m -> m.getMessageNumber() == 31).count());
				}
			}
		}
	}

	@Test
	void testZoho() throws Exception {
		try (BetterEmailer emailer = BetterEmailer.newInstance()) {
			emailer.connectToStore(BetterEmailer.getZohoImapSession(), Emailer.USERNAME_OTHERS,
					EmailerDauphineHelper.getZohoToken());
			@SuppressWarnings("resource")
			final Folder folder = emailer.getFolder("Grades");

			assertEquals(1, emailer.searchIn(BetterEmailer.ImapSearchPredicate.recipientAddressEquals(RecipientType.TO,
					"olivier.cailloux@lamsade.dauphine.fr"), folder).size());
			assertEquals(0, emailer.searchIn(BetterEmailer.ImapSearchPredicate.recipientAddressEquals(RecipientType.TO,
					"olivier.cailloux@lamsade.dauphine"), folder).size());
			assertEquals(1,
					emailer.searchIn(BetterEmailer.ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO,
							"olivier.cailloux@lamsade.dauphine.fr"), folder).size());
			assertEquals(1,
					emailer.searchIn(BetterEmailer.ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO,
							"olivier.cailloux@lamsade.dauphine."), folder).size());
			assertEquals(1,
					emailer.searchIn(BetterEmailer.ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO,
							" <olivier.cailloux@lamsade.dauphine.fr>"), folder).size());
			assertEquals(1,
					emailer.searchIn(BetterEmailer.ImapSearchPredicate.recipientFullAddressContains(RecipientType.TO,
							"olivier cailloux <olivier.cailloux@lamsade.dauphine.fr>"), folder).size());

			/**
			 * Should not throw. Finds an e-mail sent to "xxxx
			 * <olivier.cailloux@INVALIDdauphine.fr>" (real name hidden for privacy
			 * reasons).
			 */
			assertThrows(VerifyException.class, () -> emailer.searchIn(BetterEmailer.ImapSearchPredicate
					.recipientFullAddressContains(RecipientType.TO, "olivier cailloux"), folder).size());
			assertThrows(VerifyException.class, () -> emailer.searchIn(BetterEmailer.ImapSearchPredicate
					.recipientFullAddressContains(RecipientType.TO, "olivier cailloux <"), folder).size());
			/** Should equal 1. */
			assertEquals(0, emailer.searchIn(BetterEmailer.ImapSearchPredicate
					.recipientFullAddressContains(RecipientType.TO, "olivier cailloux <o"), folder).size());
		}
	}

	@Test
	void testRetrieve() throws Exception {
		try (GradesInEmails sendEmails = GradesInEmails.newInstance()) {
			EmailerDauphineHelper.connect(sendEmails.getEmailer());
			assertEquals(Optional.empty(), sendEmails.getLastGradeTo(
					EmailAddress.given("olivier.cailloux@INVALIDdauphine.fr", "Olivier Cailloux"), "git-br"));
			assertEquals(ImmutableMap.of(), sendEmails.getAllLatestGradesTo(
					EmailAddress.given("olivier.cailloux@INVALIDdauphine.fr", "Olivier Cailloux")));
//		assertEquals(ImmutableSet.of("commit", "git-br"), SendEmails
//				.getAllLatestGradesTo(new InternetAddress("…@dauphine.eu", "…")).keySet());
		}
	}

}
