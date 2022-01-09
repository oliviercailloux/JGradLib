package io.github.oliviercailloux.grade.comm;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.github.oliviercailloux.email.UncheckedMessagingException.MESSAGING_UNCHECKER;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.sun.mail.imap.IMAPFolder;
import io.github.oliviercailloux.email.EmailAddressAndPersonal;
import io.github.oliviercailloux.email.ImapSearchPredicate;
import io.github.oliviercailloux.email.UncheckedMessagingException;
import io.github.oliviercailloux.xml.XmlUtils;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.StreamSupport;
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
import javax.mail.URLName;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The time from connectToStore to close is supposed to be short. Meanwhile,
 * this object might keep open folders or other resources.
 *
 * @author Olivier Cailloux
 *
 */
public class Emailer implements AutoCloseable {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Emailer.class);

	@SuppressWarnings("unused")
	private static final Logger LOGGER_JAVAMAIL = LoggerFactory
			.getLogger(Emailer.class.getCanonicalName() + ".Javamail");

	private static final PrintStream JAVAMAIL_LOGGING_OUTPUT_STREAM = LoggingOutputStream.newInstance(LOGGER_JAVAMAIL);

	public static Emailer instance() {
		return new Emailer();
	}

	public static Session getOutlookImapSession() {
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

	public static Session getGmailImapSession() {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imap");
		props.setProperty("mail.host", "imap.gmail.com");
		props.setProperty("mail.imap.connectiontimeout", "2000");
		props.setProperty("mail.imap.timeout", "60000");
		props.setProperty("mail.imap.connectionpooltimeout", "10");
		props.setProperty("mail.imap.ssl.enable", "true");
		props.setProperty("mail.imap.ssl.checkserveridentity", "true");
		final Session session = Session.getInstance(props);
		return session;
	}

	public static Session getZohoImapSession() {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imap");
		props.setProperty("mail.host", "imap.zoho.eu");
		props.setProperty("mail.imap.connectiontimeout", "2000");
		props.setProperty("mail.imap.timeout", "60000");
		props.setProperty("mail.imap.connectionpooltimeout", "10");
		props.setProperty("mail.imap.ssl.enable", "true");
		props.setProperty("mail.imap.ssl.checkserveridentity", "true");
		final Session session = Session.getInstance(props);
		return session;
	}

	public static Session getOutlookSmtpSession() {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "smtp");
		props.setProperty("mail.host", "outlook.office365.com");
		props.setProperty("mail.smtp.auth", "true");
		props.setProperty("mail.smtp.starttls.enable", "true");
		props.setProperty("mail.smtp.port", "587");
		final Session session = Session.getInstance(props);
		return session;
	}

	public static ImmutableMap<Integer, Message> byMessageNumber(Iterable<Message> messages) {
		return StreamSupport.stream(messages.spliterator(), false).collect(
				ImmutableMap.toImmutableMap(MESSAGING_UNCHECKER.wrapFunction(Message::getMessageNumber), m -> m));
	}

	public static String getDescription(Message message) {
		return String.format("Message number %s sent %s to %s with subject '%s'.", message.getMessageNumber(),
				MESSAGING_UNCHECKER.getUsing(message::getSentDate),
				ImmutableList.copyOf(MESSAGING_UNCHECKER.getUsing(() -> message.getRecipients(RecipientType.TO))),
				MESSAGING_UNCHECKER.getUsing(message::getSubject));
	}

	private Store store;
	private final LinkedHashMap<String, Folder> openReadFolders;
	private final LinkedHashMap<String, Folder> openRWFolders;

	private Session transportSession;
	private Transport transport;
	private Folder saveInto;

	private Emailer() {
		store = null;
		openReadFolders = Maps.newLinkedHashMap();
		openRWFolders = Maps.newLinkedHashMap();
		transportSession = null;
		transport = null;
		saveInto = null;
	}

	public void connectToStore(Session session, String username, String password) {
		try {
			session.setDebugOut(JAVAMAIL_LOGGING_OUTPUT_STREAM);
			session.setDebug(true);
			store = session.getStore();
			LOGGER.info("Connecting to store with properties {}.", session.getProperties());
			MESSAGING_UNCHECKER.call(() -> store.connect(username, password));
			LOGGER.info("Connected to store.");
		} catch (NoSuchProviderException e) {
			throw new IllegalStateException(e);
		}
	}

	public URLName getUrlName() {
		checkState(store != null);
		return store.getURLName();
	}

	public void connectToTransport(Session session, String username, String password) {
		transportSession = checkNotNull(session);
		try {
			transport = transportSession.getTransport();
			LOGGER.info("Connecting to transport.");
			MESSAGING_UNCHECKER.call(() -> transport.connect(username, password));
			LOGGER.info("Connected to transport.");
		} catch (NoSuchProviderException e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("resource")
	public ImmutableList<String> getFolderNames() {
		checkState(store != null);

		try {
			/* We should NOT close this folder as it has (rightly) not been opened. */
			final Folder root = store.getDefaultFolder();
			/*
			 * Let’s return strings instead of Folders to avoid tempting the user in opening
			 * the folder themselves (we want to control this).
			 */
			return Arrays.stream(root.list()).map(Folder::getName).collect(ImmutableList.toImmutableList());
		} catch (MessagingException e) {
			throw new UncheckedMessagingException(e);
		}
	}

	/**
	 * <p>
	 * Returns the given folder, if it exists, after having opened it in READ mode
	 * if this object had not opened it already. (If this object had already opened
	 * the folder in READ_WRITE mode, then it is returned open in that state.)
	 * </p>
	 * <p>
	 * This object will take care of closing the folder.
	 * </p>
	 *
	 * @return an opened folder.
	 */
	public Folder getFolder(String name) {
		return lazyGetFolder(name, false);
	}

	/**
	 * <p>
	 * Returns the given folder, if it exists, after having opened it in READ_WRITE
	 * mode if this object had not opened it already in READ_WRITE mode.
	 * </p>
	 * <p>
	 * This object will take care of closing the folder.
	 * </p>
	 *
	 * @throws IllegalStateException if the given folder is already opened in READ
	 *                               mode.
	 */
	public Folder getFolderReadWrite(String name) throws IllegalStateException {
		return lazyGetFolder(name, true);
	}

	private Folder lazyGetFolder(String folderName, boolean andWrite) throws IllegalStateException {
		checkState(store != null);

		if (!openReadFolders.containsKey(folderName) && !openRWFolders.containsKey(folderName)) {
			@SuppressWarnings("resource")
			final Folder folder = MESSAGING_UNCHECKER.getUsing(() -> store.getFolder(folderName));
			if (andWrite) {
				MESSAGING_UNCHECKER.call(() -> folder.open(Folder.READ_WRITE));
				openRWFolders.put(folderName, folder);
			} else {
				MESSAGING_UNCHECKER.call(() -> folder.open(Folder.READ_ONLY));
				openReadFolders.put(folderName, folder);
			}
		}

		final Folder folderRead = openReadFolders.get(folderName);
		if (folderRead != null) {
			/**
			 * NB this means that opening a folder read-only for searching prevents from
			 * storing in that folder: planning is required.
			 */
			checkState(!andWrite, "A given folder can be opened only once.");
			return folderRead;
		}
		return openRWFolders.get(folderName);
	}

	public ImmutableSet<Message> searchIn(Folder folder, ImapSearchPredicate term) {
		if (term.equals(ImapSearchPredicate.FALSE)) {
			return ImmutableSet.of();
		}
		checkState(folder.isOpen());

		final Message[] asArray = MESSAGING_UNCHECKER.getUsing(() -> folder.search(term.getTerm()));
		final ImmutableSet<Message> found = ImmutableSet.copyOf(asArray);
		LOGGER.info("Searched, got: {}.", found.size());
		fetchHeaders(folder, found);

		/* TODO Searching for "Grade Java" finds "Grade Projet Java". */
		final boolean filter = false;
		if (filter) {
			return found.stream().filter(term.getPredicate()).collect(ImmutableSet.toImmutableSet());
		}

		final Optional<Message> notMatching = found.stream().filter(term.getPredicate().negate()).limit(1)
				.collect(MoreCollectors.toOptional());
		if (notMatching.isPresent()) {
			throw new VerifyException(getDescription(notMatching.get()));
		}
		LOGGER.debug("Verified {}.", found.size());
		return found;
	}

	public void fetchHeaders(Folder folder, Set<Message> messages) {
		fetch(folder, messages, false);
	}

	public void fetchMessages(Folder folder, Set<Message> messages) {
		fetch(folder, messages, true);
	}

	private void fetch(Folder folder, Set<Message> messages, boolean whole) {
		final FetchProfile fp = new FetchProfile();
		fp.add(FetchProfile.Item.CONTENT_INFO);
		fp.add(FetchProfile.Item.ENVELOPE);
		fp.add(FetchProfile.Item.FLAGS);
		fp.add(FetchProfile.Item.SIZE);
		fp.add(IMAPFolder.FetchProfileItem.HEADERS);
		fp.add(IMAPFolder.FetchProfileItem.INTERNALDATE);
		if (whole) {
			fp.add(IMAPFolder.FetchProfileItem.MESSAGE);
		}
		try {
			folder.fetch(messages.toArray(new Message[messages.size()]), fp);
			LOGGER.debug("Fetched {} " + (whole ? "messages" : "headers") + ".", messages.size());
		} catch (MessagingException e) {
			throw new IllegalStateException(e);
		}
	}

	private MimeMessage getMessage(Email email, InternetAddress fromAddress) {
		checkNotNull(transportSession);
		final String subject = email.getSubject();
		final String textContent = XmlUtils.asString(email.getDocument());
		final String utf8 = StandardCharsets.UTF_8.name();
		final MimeMessage message = new MimeMessage(transportSession);
		try {
			message.setFrom(fromAddress);
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

			final EmailAddressAndPersonal to = email.getTo();
			final InternetAddress[] toSingleton = new InternetAddress[] { to.asInternetAddress() };
			/**
			 * When the address is incorrect (e.g. WRONG@gmail.com), a message delivered
			 * event is still sent to registered TransportListeners. A new message in the
			 * INBOX indicates the error, but it seems hard to detect it on the spot.
			 */
			message.setRecipients(Message.RecipientType.TO, toSingleton);
			message.saveChanges();
		} catch (MessagingException e) {
			throw new UncheckedMessagingException(e);
		}
		return message;
	}

	public void saveInto(Folder folder) {
		this.saveInto = folder;
	}

	public void send(Collection<Email> emails, EmailAddressAndPersonal fromAddress) {
		if (saveInto != null && !emails.isEmpty()) {
			checkState(transport != null);
			checkState(saveInto.isOpen());
			checkState(saveInto.getMode() == Folder.READ_WRITE);
		}

		for (Email email : emails) {
			final MimeMessage message = getMessage(email, fromAddress.asInternetAddress());
			MESSAGING_UNCHECKER.call(() -> transport.sendMessage(message, message.getAllRecipients()));

			MESSAGING_UNCHECKER.call(() -> saveInto.appendMessages(new Message[] { message }));
		}
		LOGGER.info("Messages sent: {}.", emails.size());
	}

	@Override
	public void close() throws UncheckedMessagingException {
		if (transportSession != null) {
			MESSAGING_UNCHECKER.call(() -> transport.close());
		}

		for (Folder folder : openRWFolders.values()) {
			MESSAGING_UNCHECKER.call(() -> folder.close());
		}
		openRWFolders.clear();

		for (Folder folder : openReadFolders.values()) {
			MESSAGING_UNCHECKER.call(() -> folder.close());
		}
		openReadFolders.clear();

		if (store != null) {
			MESSAGING_UNCHECKER.call(() -> store.close());
		}
	}

}
