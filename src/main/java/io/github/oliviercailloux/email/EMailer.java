package io.github.oliviercailloux.email;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.SearchTerm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.diffplug.common.base.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;

import io.github.oliviercailloux.utils.Utils;
import io.github.oliviercailloux.xml.HtmlDocument;
import io.github.oliviercailloux.xml.XmlUtils;

public class EMailer {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(EMailer.class);

	public static final String USERNAME = "ocailloux@dauphine.fr";

	public static final InternetAddress FROM = asInternetAddress("olivier.cailloux@dauphine.fr", "Olivier Cailloux");

	private static InternetAddress asInternetAddress(String address, String personal) {
		try {
			return new InternetAddress(address, personal);
		} catch (UnsupportedEncodingException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static Message sendTo(Email email, InternetAddress to) {
		return send(ImmutableMap.of(email, to)).stream().collect(MoreCollectors.onlyElement());
	}

	public static ImmutableSet<Message> send(Map<Email, InternetAddress> toSend) {
		final Session session = getSmtpSession();
		final ImmutableSet.Builder<Message> messagesBuilder = ImmutableSet.builder();
		try (Transport transport = getTransport(session)) {
			LOGGER.info("Connecting to transport.");
			transport.connect(USERNAME, getTokenNoExc());
			LOGGER.info("Connected to transport.");
			for (Email email : toSend.keySet()) {
				final String subject = HtmlDocument.getOnlyElementWithLocalName(email.getDocument(), "title")
						.getTextContent();
				final MimeMessage message = getMessage(session, subject, XmlUtils.asString(email.getDocument()),
						email.getFileName(), email.getFileContent(), email.getFileSubtype());

				final InternetAddress to = toSend.get(email);
				final InternetAddress[] toSingleton = new InternetAddress[] { to };
				/**
				 * When the address is incorrect (e.g. WRONG@gmail.com), a message delivered
				 * event is still sent to registered TransportListeners. A new message in the
				 * INBOX indicates the error, but it seems hard to detect it on the spot.
				 */
				message.setRecipients(Message.RecipientType.TO, toSingleton);
				message.saveChanges();

				transport.sendMessage(message, toSingleton);
				messagesBuilder.add(message);
			}
			LOGGER.info("Messages sent.");
		} catch (MessagingException e) {
			throw new AssertionError(e);
		}
		return messagesBuilder.build();
	}

	public static void saveInto(Message message, String folderName) throws NoSuchProviderException, MessagingException {
		saveInto(ImmutableSet.of(message), folderName);
	}

	public static void saveInto(Set<Message> messages, String folderName)
			throws NoSuchProviderException, MessagingException {
		final Session session = getImapSession();
		try (Store store = session.getStore()) {
			LOGGER.info("Connecting to store.");
			store.connect(USERNAME, getTokenNoExc());
			LOGGER.info("Connected to store.");
			// final Folder root = store.getDefaultFolder();
			// final ImmutableList<Folder> folders = ImmutableList.copyOf(root.list());
			// LOGGER.info("Folders: {}.", folders);
			// final Folder folder = root.getFolder(folderName);
			try (Folder folder = store.getFolder(folderName)) {
				LOGGER.info("Message count: {}.", folder.getMessageCount());
				folder.open(Folder.READ_WRITE);
				folder.appendMessages(messages.toArray(new Message[] {}));
			}
		}
	}

	public static void searchIn(SearchTerm term, String folderName) throws NoSuchProviderException, MessagingException {
		final Session session = getImapSession();
		try (Store store = session.getStore()) {
			LOGGER.info("Connecting.");
			store.connect(USERNAME, getTokenNoExc());
			try (Folder folder = store.getFolder(folderName)) {
				// new FromStringTerm("cailloux")
				final ImmutableList<Message> found = ImmutableList.copyOf(folder.search(term));
				LOGGER.info("Nb: {}.", found.size());
				final Throwing.Function<Message, String> function = Message::getSubject;
				LOGGER.info("Found: {}.", found.stream().limit(1).map(Utils.wrapUsingIllegalStateException(function))
						.collect(ImmutableList.toImmutableList()));
				// final Message message = found.get(0);
			}
		}
	}

	private static Session getImapSession() {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imap");
		props.setProperty("mail.host", "outlook.office365.com");
		props.setProperty("mail.imap.connectiontimeout", "2000");
		props.setProperty("mail.imap.timeout", "60*1000");
		props.setProperty("mail.imap.connectionpooltimeout", "10");
		props.setProperty("mail.user", USERNAME);
		props.setProperty("mail.imap.ssl.enable", "true");
		props.setProperty("mail.imap.ssl.checkserveridentity", "true");
		final Session session = Session.getInstance(props);
		return session;
	}

	private static Session getSmtpSession() {
		final Properties props = new Properties();
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.port", "587");
		props.setProperty("mail.store.protocol", "smtp");
		props.setProperty("mail.host", "outlook.office365.com");
		props.setProperty("mail.user", USERNAME);
		final Session session = Session.getInstance(props);
		return session;
	}

	/**
	 * @param session
	 * @param subject     not encoded
	 * @param textContent not encoded
	 */
	private static MimeMessage getMessage(Session session, String subject, String textContent, String fileName,
			String fileContent, String fileSubtype) {
		final String utf8 = StandardCharsets.UTF_8.toString();
		final MimeMessage message = new MimeMessage(session);
		try {
			message.setFrom(FROM);
			message.setSubject(subject, utf8);

			final MimeBodyPart textPart = new MimeBodyPart();
			textPart.setText(textContent, utf8, "html");

			final MimeBodyPart filePart = new MimeBodyPart();
			filePart.setFileName(fileName);
			filePart.setDisposition(Part.ATTACHMENT);
			filePart.setText(fileContent, utf8, fileSubtype);

			final MimeMultipart multipartContent = new MimeMultipart(textPart, filePart);
			message.setContent(multipartContent);
		} catch (MessagingException e) {
			throw new AssertionError(e);
		}
		return message;
	}

	private static Transport getTransport(Session session) {
		try {
			return session.getTransport();
		} catch (NoSuchProviderException e) {
			throw new AssertionError(e);
		}
	}

	private static String getToken() throws IOException {
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
		final String content = Files.readString(path);
		return content.replaceAll("\n", "");
	}

	private static String getTokenNoExc() {
		try {
			return getToken();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
}