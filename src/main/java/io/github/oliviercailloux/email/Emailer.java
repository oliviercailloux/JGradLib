package io.github.oliviercailloux.email;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
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
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.RecipientTerm;
import javax.mail.search.SearchTerm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.utils.Utils;
import io.github.oliviercailloux.xml.XmlUtils;

public class Emailer {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Emailer.class);

	public static final String USERNAME_DAUPHINE = "ocailloux@dauphine.fr";

	public static final String USERNAME_GMAIL = "olivier.cailloux";

	public static final InternetAddress FROM = asInternetAddress("olivier.cailloux@dauphine.fr", "Olivier Cailloux");

	private static InternetAddress asInternetAddress(String address, String personal) {
		try {
			return new InternetAddress(address, personal);
		} catch (UnsupportedEncodingException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static Message send(Email email) {
		return send(ImmutableSet.of(email)).stream().collect(MoreCollectors.onlyElement());
	}

	public static ImmutableSet<Message> send(Collection<Email> emails) {
		final Session session = getSmtpSession();
		final ImmutableSet.Builder<Message> messagesBuilder = ImmutableSet.builder();
		try (Transport transport = getTransport(session)) {
			LOGGER.info("Connecting to transport.");
			transport.connect(USERNAME_DAUPHINE, getDauphineToken());
			LOGGER.info("Connected to transport.");
			for (Email email : emails) {
				final MimeMessage message = getMessage(session, email);

				final InternetAddress to = email.getTo();
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

	public static void saveInto(Set<Message> messages, String folderName)
			throws NoSuchProviderException, MessagingException {
		final Session session = getOutlookImapSession();
		try (Store store = session.getStore()) {
			LOGGER.info("Connecting to store.");
			store.connect(USERNAME_DAUPHINE, getDauphineToken());
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

	public static ImmutableSet<Message> searchIn(SearchTerm term, String folderName)
			throws NoSuchProviderException, MessagingException {
		final Session session = getOutlookImapSession();
		final ImmutableSet<Message> found;
		try (Store store = session.getStore()) {
			LOGGER.info("Connecting.");
			store.connect(USERNAME_DAUPHINE, getDauphineToken());
			try (Folder folder = store.getFolder(folderName)) {
				folder.open(Folder.READ_ONLY);
				found = searchIn(term, folder, true);
			}
		}
		return found;
	}

	public static ImmutableSet<Message> searchIn(SearchTerm term, Folder folder, boolean cache) {
		final ImmutableSet<Message> found;
		final Message[] asArray = Utils.getOrThrow(() -> folder.search(term));
		if (cache) {
			/**
			 * As message is lazily filled up, we need to retrieve it before the folder gets
			 * closed.
			 */
			final FetchProfile fp = new FetchProfile();
			fp.add(FetchProfile.Item.ENVELOPE);
			fp.add(FetchProfile.Item.CONTENT_INFO);
			fp.add(FetchProfile.Item.SIZE);
			fp.add(FetchProfile.Item.FLAGS);
			Utils.uncheck(() -> folder.fetch(asArray, fp));
			/**
			 * Asking for the addresses, even with this fetch order, fails. Bugs seem common
			 * in this area, see https://javaee.github.io/javamail/FAQ#imapserverbug, though
			 * this may not be a bug. Anyway, let’s force fetching headers to avoid this.
			 */
			for (Message message : asArray) {
				Utils.uncheck(() -> message.getAllHeaders());
			}
		}
		found = ImmutableSet.copyOf(asArray);
		return found;
	}

	public static ImmutableSet<Message> searchSentToIn(InternetAddress address, String folderName)
			throws NoSuchProviderException, MessagingException {
		final Session session = getOutlookImapSession();
		try (Store store = session.getStore()) {
			LOGGER.info("Connecting.");
			store.connect(USERNAME_DAUPHINE, getDauphineToken());
			try (Folder folder = store.getFolder(folderName)) {
				folder.open(Folder.READ_ONLY);
				return searchSentToIn(address, folder);
			}
		}
	}

	public static ImmutableSet<Message> searchSentToIn(InternetAddress address, Folder folder) {
		/**
		 * Because of bugs in some IMAP servers, we extend the search by searching on
		 * only parts of the addresses, then filter the results.
		 */
		final ImmutableSet<Message> partialAddressSearch;
		final ImmutableSet<Message> personalSearch;
		final ImmutableSet<Message> addressSearch;
		final ImmutableSet<Message> fullAddressSearch;
		partialAddressSearch = searchIn(new RecipientStringTerm(RecipientType.TO, getPart(address.getAddress())),
				folder, true);
		personalSearch = searchIn(new RecipientStringTerm(RecipientType.TO, address.getPersonal()), folder, true);

		addressSearch = searchIn(new RecipientStringTerm(RecipientType.TO, address.getAddress()), folder, true);
		fullAddressSearch = searchIn(new RecipientTerm(RecipientType.TO, address), folder, true);

		Verify.verify(partialAddressSearch.containsAll(addressSearch));
		Verify.verify(addressSearch.containsAll(fullAddressSearch));
		Verify.verify(personalSearch.containsAll(fullAddressSearch));

		final ImmutableSet<Integer> partialNumbers = partialAddressSearch.stream().map(Message::getMessageNumber)
				.collect(ImmutableSet.toImmutableSet());
		Verify.verify(partialAddressSearch.size() == partialNumbers.size());
		final ImmutableSet<Integer> fullNumbers = fullAddressSearch.stream().map(Message::getMessageNumber)
				.collect(ImmutableSet.toImmutableSet());
		Verify.verify(fullAddressSearch.size() == fullNumbers.size());

		final ImmutableSet.Builder<Message> candidatesBuilder = ImmutableSet.<Message>builder();
		candidatesBuilder.addAll(partialAddressSearch);
		personalSearch.stream().filter(m -> !partialNumbers.contains(m.getMessageNumber()))
				.forEachOrdered(candidatesBuilder::add);
		final ImmutableSet<Message> candidates = candidatesBuilder.build();

		final ImmutableSet<Message> matching = candidates.stream()
				.filter(m -> Stream.of(Utils.getOrThrow(() -> m.getRecipients(RecipientType.TO)))
						.map(a -> (InternetAddress) a).anyMatch(Predicates.equalTo(address)))
				.collect(ImmutableSet.toImmutableSet());

		final ImmutableSet<Integer> matchingNumbers = matching.stream().map(Message::getMessageNumber)
				.collect(ImmutableSet.toImmutableSet());

		{
			final ImmutableSet<Integer> personalNumbers = personalSearch.stream().map(Message::getMessageNumber)
					.collect(ImmutableSet.toImmutableSet());
			Verify.verify(personalSearch.size() == personalNumbers.size());
			final ImmutableSet<Integer> candidatesNumbers = candidates.stream().map(Message::getMessageNumber)
					.collect(ImmutableSet.toImmutableSet());
			Verify.verify(candidates.size() == candidatesNumbers.size());
			final Set<Integer> candidatesNumbersByUnionOnNumbers = Sets.union(partialNumbers, personalNumbers);
			Verify.verify(candidatesNumbers.equals(candidatesNumbersByUnionOnNumbers));
			verify(matching.size() == matchingNumbers.size());
		}

		final Set<Integer> missed = Sets.difference(matchingNumbers, fullNumbers);
		if (!missed.isEmpty()) {
			if (fullAddressSearch.isEmpty()) {
				LOGGER.warn("Search initially missed everything: {} (has been extended).", missed);
			} else {
				LOGGER.warn("Search initially missed {} (has been extended).", missed);
			}
		}

		return matching;
	}

	public static String getPart(String addressString) {
		final String[] split = addressString.split("@");
		checkArgument(split.length == 2);
		final String username = split[0];
		final String domain = split[1];
		final String[] domainComponents = domain.split("\\.");
		checkArgument(domainComponents.length >= 2, ImmutableList.copyOf(domainComponents) + "; " + domain);
		return username + "@" + domainComponents[0];
	}

	public static ImmutableList<Folder> getFolders() throws NoSuchProviderException, MessagingException {
		final Session session = getOutlookImapSession();
		try (Store store = session.getStore()) {
			LOGGER.info("Connecting.");
			store.connect(USERNAME_DAUPHINE, getDauphineToken());
			try (Folder root = store.getDefaultFolder()) {
				final ImmutableList<Folder> folders = ImmutableList.copyOf(root.list());
				return folders;
			}
		}
	}

	static Session getOutlookImapSession() {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imap");
		props.setProperty("mail.host", "outlook.office365.com");
		props.setProperty("mail.imap.connectiontimeout", "2000");
		props.setProperty("mail.imap.timeout", "60*1000");
		props.setProperty("mail.imap.connectionpooltimeout", "10");
		props.setProperty("mail.imap.ssl.enable", "true");
		props.setProperty("mail.imap.ssl.checkserveridentity", "true");
		final Session session = Session.getInstance(props);
		return session;
	}

	static Session getGmailImapSession() {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imap");
		props.setProperty("mail.host", "imap.gmail.com");
		props.setProperty("mail.imap.connectiontimeout", "2000");
		props.setProperty("mail.imap.timeout", "60*1000");
		props.setProperty("mail.imap.connectionpooltimeout", "10");
		props.setProperty("mail.imap.ssl.enable", "true");
		props.setProperty("mail.imap.ssl.checkserveridentity", "true");
//		props.setProperty("mail.debug", "true");
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
		props.setProperty("mail.user", USERNAME_DAUPHINE);
		final Session session = Session.getInstance(props);
		return session;
	}

	/**
	 * @param session
	 * @param subject     not encoded
	 * @param textContent not encoded
	 */
	private static MimeMessage getMessage(Session session, Email email) {
		final String subject = email.getSubject();
		final String textContent = XmlUtils.asString(email.getDocument());
		final String utf8 = StandardCharsets.UTF_8.toString();
		final MimeMessage message = new MimeMessage(session);
		try {
			message.setFrom(FROM);
			message.setSubject(subject, utf8);

			final MimeBodyPart textPart = new MimeBodyPart();
			textPart.setText(textContent, utf8, "html");

			final MimeMultipart multipartContent;
			if (email.hasFile()) {
				final String fileName = email.getFileName();
				final String fileContent = email.getFileContent();
				final String fileSubtype = email.getFileSubtype();

				final MimeBodyPart filePart = new MimeBodyPart();
				filePart.setFileName(fileName);
				filePart.setDisposition(Part.ATTACHMENT);
				filePart.setText(fileContent, utf8, fileSubtype);

				multipartContent = new MimeMultipart(textPart, filePart);
			} else {
				multipartContent = new MimeMultipart(textPart);
			}

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

	static String getDauphineToken() {
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

	static String getGmailToken() {
		{
			final String token = System.getenv("token_gmail");
			if (token != null) {
				return token;
			}
		}
		{
			final String token = System.getProperty("token_gmail");
			if (token != null) {
				return token;
			}
		}
		final Path path = Paths.get("token_gmail.txt");
		if (!Files.exists(path)) {
			throw new IllegalStateException();
		}
		final String content = Utils.getOrThrow(() -> Files.readString(path));
		return content.replaceAll("\n", "");
	}

	public static <T> T fromFolder(String folderName, Function<Folder, T> function)
			throws NoSuchProviderException, MessagingException {
		final Session session = getOutlookImapSession();
		try (Store store = session.getStore()) {
			LOGGER.info("Connecting.");
			store.connect(USERNAME_DAUPHINE, getDauphineToken());
			try (Folder folder = store.getFolder(folderName)) {
				folder.open(Folder.READ_ONLY);
				return function.apply(folder);
			}
		}
	}
}
