package io.github.oliviercailloux.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import io.github.oliviercailloux.grade.GradeTestsHelper;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.format.HtmlGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.utils.Utils;
import io.github.oliviercailloux.xml.HtmlDocument;
import io.github.oliviercailloux.xml.XmlUtils;

public class EmailerTests {
	public static final String SENT_FOLDER = "Éléments envoyés";
	public static final String TRASH_FOLDER = "Éléments supprimés";
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(EmailerTests.class);

	public static void main(String[] args) throws Exception {
		final Document doc1 = getTestDocument("First document");
		LOGGER.info("Doc1: {}.", XmlUtils.asString(doc1));
		final InternetAddress to1 = new InternetAddress("olivier.cailloux@gmail.com", "O.C");
		final Email email1 = Email.withDocumentAndFile(doc1, "data.json",
				Json.createObjectBuilder().add("jsonint", 1).build().toString(), "json", to1);

		final WeightingGrade grade = GradeTestsHelper.getComplexGradeWithPenalty();
		final Document doc2 = HtmlGrade.asHtml(grade, "Ze grade");
		final InternetAddress to2 = new InternetAddress("oliviercailloux@gmail.com", "OC");
		final Email email2 = Email.withDocumentAndFile(doc2, "data.json", JsonGrade.asJson(grade).toString(), "json",
				to2);

		final ImmutableSet<Message> sent = Emailer.send(ImmutableSet.of(email1, email2));
		LOGGER.info("Sent {} messages.", sent.size());

//		EMailer.saveInto(sent, SENT_FOLDER);
//		EMailer.saveInto(sent, TRASH_FOLDER);
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
	void testSearch() throws Exception {
		final ImmutableSet<Message> toCailGmailBroad = Emailer
				.searchIn(new RecipientStringTerm(RecipientType.TO, "olivier.cailloux@gmail.com"), "Éléments envoyés");
		assertTrue(20 < toCailGmailBroad.size(), "" + toCailGmailBroad.size());
		assertTrue(toCailGmailBroad.size() < 30);

		final ImmutableSet<Message> toCail = Emailer.searchIn(new RecipientStringTerm(RecipientType.TO, "cailloux"),
				"Éléments envoyés");
		assertTrue(50 < toCail.size(), "" + toCail.size());
		assertTrue(toCail.size() < 100);
		final ImmutableList<Integer> toCailList = toCail.stream().map(Utils.uncheck(Message::getMessageNumber))
				.collect(ImmutableList.toImmutableList());
		final ImmutableSet<Integer> toCailSet = ImmutableSet.copyOf(toCailList);
		assertEquals(toCailList.size(), toCailSet.size());

		final ImmutableSet<Message> toCailGmail = Emailer.searchIn(new RecipientTerm(RecipientType.TO,
				new InternetAddress("olivier.cailloux@gmail.com", "Olivier Cailloux")), "Éléments envoyés");
		assertTrue(20 < toCailGmail.size());
		assertTrue(toCailGmail.size() < 50);
		final ImmutableList<Integer> toCailGmailList = toCailGmail.stream()
				.map(Utils.uncheck(Message::getMessageNumber)).collect(ImmutableList.toImmutableList());
		final ImmutableSet<Integer> toCailGmailSet = ImmutableSet.copyOf(toCailGmailList);
		assertEquals(toCailGmailList.size(), toCailGmailSet.size());

		final ImmutableSet<Message> toCailDauphineBroad = Emailer.searchIn(
				new RecipientStringTerm(RecipientType.TO, "olivier.cailloux@dauphine.fr"), "Éléments envoyés");
		/** Seems like this is sometimes empty (and it shouldn’t). */
		assertTrue(toCailDauphineBroad.isEmpty());
//		assertTrue(20 < toCailDauphineBroad.size(), "" + toCailDauphineBroad.size());
//		assertTrue(toCailDauphineBroad.size() < 50);
		final ImmutableList<Integer> toCailDauphineBroadList = toCailDauphineBroad.stream()
				.map(Utils.uncheck(Message::getMessageNumber)).collect(ImmutableList.toImmutableList());
		final ImmutableSet<Integer> toCailDauphineBroadSet = ImmutableSet.copyOf(toCailDauphineBroadList);
		assertEquals(toCailDauphineBroadList.size(), toCailDauphineBroadSet.size());

		final ImmutableSet<Message> toCailDauphine = Emailer.searchIn(new RecipientTerm(RecipientType.TO,
				new InternetAddress("olivier.cailloux@dauphine.fr", "Olivier Cailloux")), "Éléments envoyés");
		/** Shouldn’t be empty. */
		assertTrue(toCailDauphine.isEmpty());
//		assertTrue(20 < toCailDauphine.size(), "" + toCailDauphine.size());
//		assertTrue(toCailDauphine.size() < 50);
		final ImmutableList<Integer> toCailDauphineList = toCailDauphine.stream()
				.map(Utils.uncheck(Message::getMessageNumber)).collect(ImmutableList.toImmutableList());
		final ImmutableSet<Integer> toCailDauphineSet = ImmutableSet.copyOf(toCailDauphineList);
		assertEquals(toCailDauphineList.size(), toCailDauphineSet.size());

		assertTrue(toCailSet.containsAll(toCailDauphineSet));

		final SetView<Integer> unclassified = Sets.difference(Sets.difference(toCailSet, toCailGmailSet),
				toCailDauphineSet);

		LOGGER.info("Nb: {}.", unclassified.size());

		for (int mailId : unclassified) {
			final Message message = toCail.stream().filter(m -> m.getMessageNumber() == mailId)
					.collect(MoreCollectors.onlyElement());
			final Address[] recipients = message.getRecipients(RecipientType.TO);
			final String asStr = InternetAddress.toUnicodeString(recipients);
			final InternetAddress adr0 = (InternetAddress) recipients[0];
			LOGGER.info("Found: {}, {}, {}, equals? {}, {}.", mailId, asStr, message.getSubject(),
					adr0.getAddress().equals("olivier.cailloux@dauphine.fr"),
					Objects.equals(adr0.getPersonal(), "Olivier Cailloux"));
		}
//		LOGGER.info("Found: {}.",
//				toCail.stream().filter(m -> notGmail.contains(m.getMessageNumber()))
//						.map(Utils.uncheck(Message::getAllRecipients)).map(InternetAddress::toUnicodeString)
//						.collect(ImmutableList.toImmutableList()));
	}

	@Test
	void testSearchSentTo() throws Exception {
		final ImmutableSet<Message> toMe = Emailer.searchSentToIn(
				new InternetAddress("olivier.cailloux@dauphine.fr", "Olivier Cailloux"), "Éléments envoyés");
		assertTrue(7 <= toMe.size());
		assertTrue(toMe.size() <= 30);
		final ImmutableSet<Integer> numbers = toMe.stream().map(Message::getMessageNumber)
				.collect(ImmutableSet.toImmutableSet());
		assertEquals(toMe.size(), numbers.size());
		LOGGER.debug("Numbers: {}.", numbers);
		assertTrue(toMe.stream().anyMatch(m -> m.getMessageNumber() == 6068), numbers.toString());
	}

	/**
	 * https://github.com/eclipse-ee4j/mail/issues/425
	 */
	@Test
	void testBug() throws Exception {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imap");
		props.setProperty("mail.host", "outlook.office365.com");
		props.setProperty("mail.imap.connectiontimeout", "2000");
		props.setProperty("mail.imap.timeout", "60*1000");
		props.setProperty("mail.imap.connectionpooltimeout", "10");
		props.setProperty("mail.user", "ocailloux@dauphine.fr");
		props.setProperty("mail.imap.ssl.enable", "true");
		props.setProperty("mail.imap.ssl.checkserveridentity", "true");
//		props.setProperty("mail.debug", "true");
		final Session session = Session.getInstance(props);
		try (Store store = session.getStore()) {
			LOGGER.info("Connecting.");
			store.connect(Emailer.USERNAME, getToken());
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
							.search(new RecipientStringTerm(RecipientType.TO, "Olivier Cailloux"));
					assertTrue(messagesToThatRecipientStringTerm.length > 10);
				}
			}
		}
	}

	private static String getToken() {
		{
			final String token = System.getenv("token_dauphine");
			if (token != null) {
				return token;
			}
		}
		{
			final String token = System.getProperty("token_dauphine");
			if (token != null) {
				return token;
			}
		}
		final Path path = Paths.get("token_dauphine.txt");
		if (!Files.exists(path)) {
			throw new IllegalStateException();
		}
		final String content = Utils.getOrThrow(() -> Files.readString(path));
		return content.replaceAll("\n", "");
	}

}
