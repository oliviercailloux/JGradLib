package io.github.oliviercailloux.grade.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

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
import javax.mail.search.AndTerm;
import javax.mail.search.FromStringTerm;
import javax.mail.search.FromTerm;
import javax.mail.search.OrTerm;
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.RecipientTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SubjectTerm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.xml.XmlUtils;

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

	@SuppressWarnings("serial")
	public static class UncheckedMessagingException extends RuntimeException {
		private UncheckedMessagingException(Throwable cause) {
			super(cause);
		}
	}

	public static class ImapSearchPredicate implements Predicate<Message> {
		public static ImapSearchPredicate recipientAddressEquals(RecipientType recipientType, String address) {
			checkArgument(address.toLowerCase(Locale.ROOT).equals(address));
			final EmailAddress emailAddress = EmailAddress.given(address);
			return new ImapSearchPredicate(new RecipientTerm(recipientType, emailAddress.asInternetAddress()),
					(m) -> recipientAddressEquals(recipientType, address, m));
		}

		public static ImapSearchPredicate fromAddressEquals(String address) {
			checkArgument(address.toLowerCase(Locale.ROOT).equals(address));
			final EmailAddress emailAddress = EmailAddress.given(address);
			return new ImapSearchPredicate(new FromTerm(emailAddress.asInternetAddress()),
					(m) -> fromAddressEquals(address, m));
		}

		public static ImapSearchPredicate recipientFullAddressContains(RecipientType recipientType, String subString) {
			/**
			 * “In all search keys that use strings, a message matches the key if the string
			 * is a substring of the field. The matching is case-insensitive.” --
			 * https://tools.ietf.org/html/rfc3501
			 */
			checkArgument(subString.toLowerCase(Locale.ROOT).equals(subString));
			return new ImapSearchPredicate(new RecipientStringTerm(recipientType, subString),
					(m) -> recipientFullAddressContains(recipientType, subString, m));
		}

		public static ImapSearchPredicate fromFullAddressContains(String subString) {
			checkArgument(subString.toLowerCase(Locale.ROOT).equals(subString));
			return new ImapSearchPredicate(new FromStringTerm(subString), (m) -> fromFullAddressContains(subString, m));
		}

		public static ImapSearchPredicate subjectContains(String subString) {
			checkArgument(subString.toLowerCase(Locale.ROOT).equals(subString));
			return new ImapSearchPredicate(new SubjectTerm(subString), (m) -> subjectContains(subString, m));
		}

		private static boolean recipientAddressEquals(RecipientType recipientType, String address, Message m) {
			return Arrays.stream(call(() -> m.getRecipients(recipientType))).map(a -> (InternetAddress) a)
					.anyMatch(a -> a.getAddress().toLowerCase(Locale.ROOT).equals(address));
		}

		private static boolean fromAddressEquals(String address, Message m) {
			return Arrays.stream(call(() -> m.getFrom())).map(a -> (InternetAddress) a)
					.anyMatch(a -> a.getAddress().toLowerCase(Locale.ROOT).equals(address));
		}

		private static boolean recipientFullAddressContains(RecipientType recipientType, String subString, Message m) {
			return Arrays.stream(call(() -> m.getRecipients(recipientType))).map(a -> (InternetAddress) a)
					.map(InternetAddress::toUnicodeString)
					.anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(subString));
		}

		private static boolean fromFullAddressContains(String subString, Message m) {
			return Arrays.stream(call(() -> m.getFrom())).map(a -> (InternetAddress) a)
					.anyMatch(a -> a.toUnicodeString().toLowerCase(Locale.ROOT).contains(subString));
		}

		private static boolean subjectContains(String subString, Message m) {
			return call(() -> m.getSubject()).toLowerCase(Locale.ROOT).contains(subString);
		}

		private SearchTerm term;
		private Predicate<Message> predicate;

		public ImapSearchPredicate(SearchTerm term, Predicate<Message> predicate) {
			this.term = checkNotNull(term);
			this.predicate = checkNotNull(predicate);
		}

		@Override
		public boolean test(Message m) {
			final boolean test = predicate.test(m);
			verify(term.match(m) == test);
			return test;
		}

		public ImapSearchPredicate andSatisfy(ImapSearchPredicate other) {
			final ImmutableSet<SearchTerm> theseTerms;
			if (term instanceof AndTerm) {
				final AndTerm thisAndTerm = (AndTerm) term;
				theseTerms = ImmutableSet.copyOf(thisAndTerm.getTerms());
			} else {
				theseTerms = ImmutableSet.of(term);
			}

			final ImmutableSet<SearchTerm> otherTerms;
			if (other.term instanceof AndTerm) {
				final AndTerm otherAndTerm = (AndTerm) other.term;
				otherTerms = ImmutableSet.copyOf(otherAndTerm.getTerms());
			} else {
				otherTerms = ImmutableSet.of(other.term);
			}

			final ImmutableSet<SearchTerm> allTerms = Sets.union(theseTerms, otherTerms).immutableCopy();

			final AndTerm andTerm = new AndTerm(allTerms.toArray(new SearchTerm[allTerms.size()]));
			return new ImapSearchPredicate(andTerm, predicate.and(other.predicate));
		}

		public ImapSearchPredicate orSatisfy(ImapSearchPredicate other) {
			return new ImapSearchPredicate(new OrTerm(term, other.term), predicate.or(other.predicate));
		}
	}

	public static Emailer newInstance() {
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
		props.setProperty("mail.imap.timeout", "60*1000");
		props.setProperty("mail.imap.connectionpooltimeout", "10");
		props.setProperty("mail.imap.ssl.enable", "true");
		props.setProperty("mail.imap.ssl.checkserveridentity", "true");
		// props.setProperty("mail.debug", "true");
		final Session session = Session.getInstance(props);
		return session;
	}

	public static Session getZohoImapSession() {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imap");
		props.setProperty("mail.host", "imap.zoho.eu");
		props.setProperty("mail.imap.connectiontimeout", "2000");
		props.setProperty("mail.imap.timeout", "60*1000");
		props.setProperty("mail.imap.connectionpooltimeout", "10");
		props.setProperty("mail.imap.ssl.enable", "true");
		props.setProperty("mail.imap.ssl.checkserveridentity", "true");
		// props.setProperty("mail.debug", "true");
		final Session session = Session.getInstance(props);
		return session;
	}

	public static Session getOutlookSmtpSession() {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "smtp");
		props.setProperty("mail.host", "outlook.office365.com");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.port", "587");
		final Session session = Session.getInstance(props);
		return session;
	}

	public static ImmutableMap<Integer, Message> byMessageNumber(Iterable<Message> messages) {
		return StreamSupport.stream(messages.spliterator(), false)
				.collect(ImmutableMap.toImmutableMap(uncheck(Message::getMessageNumber), m -> m));
	}

	public static String getDescription(Message message) {
		return String.format("Message number %s sent %s to %s with subject '%s'.", message.getMessageNumber(),
				call(message::getSentDate), ImmutableList.copyOf(call(() -> message.getRecipients(RecipientType.TO))),
				call(message::getSubject));
	}

	@FunctionalInterface
	public static interface MessagingRunnable {
		public void run() throws MessagingException;
	}

	@FunctionalInterface
	public static interface MessagingFunction<F, T> {
		public T apply(F from) throws MessagingException;
	}

	@FunctionalInterface
	public static interface MessagingSupplier<T> {
		public T get() throws MessagingException;
	}

	public static void call(MessagingRunnable runnable) {
		try {
			runnable.run();
		} catch (MessagingException e) {
			throw new UncheckedMessagingException(e);
		}
	}

	public static <T> T call(MessagingSupplier<T> supplier) {
		try {
			return supplier.get();
		} catch (MessagingException e) {
			throw new UncheckedMessagingException(e);
		}
	}

	public static <F, T> Function<F, T> uncheck(MessagingFunction<F, T> function) {
		return (f -> {
			try {
				return function.apply(f);
			} catch (MessagingException e) {
				throw new UncheckedMessagingException(e);
			}
		});
	}

	@SuppressWarnings("unused")
	public static <T> Supplier<T> uncheck(MessagingSupplier<T> supplier) {
		return (() -> {
			try {
				return supplier.get();
			} catch (MessagingException e) {
				throw new UncheckedMessagingException(e);
			}
		});
	}

	private Store store;
	private final LinkedHashMap<String, Folder> openReadFolders;
	private final LinkedHashMap<String, Folder> openRWFolders;

	private Session transportSession;
	private Transport transport;

	private Emailer() {
		store = null;
		openReadFolders = Maps.newLinkedHashMap();
		openRWFolders = Maps.newLinkedHashMap();
		transportSession = null;
		transport = null;
	}

	public void connectToStore(Session session, String username, String password) {
		try {
			store = session.getStore();
			LOGGER.info("Connecting to store.");
			call(() -> store.connect(username, password));
			LOGGER.info("Connected to store.");
		} catch (NoSuchProviderException e) {
			throw new IllegalStateException(e);
		}
	}

	public void connectToTransport(Session session, String username, String password) {
		transportSession = checkNotNull(session);
		try {
			transport = transportSession.getTransport();
			LOGGER.info("Connecting to transport.");
			call(() -> transport.connect(username, password));
			LOGGER.info("Connected to transport.");
		} catch (NoSuchProviderException e) {
			throw new IllegalStateException(e);
		}
	}

	public ImmutableList<String> getFolderNames() {
		checkState(store != null);

		try (Folder root = store.getDefaultFolder()) {
			/**
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
			final Folder folder = call(() -> store.getFolder(folderName));
			if (andWrite) {
				call(() -> folder.open(Folder.READ_WRITE));
				openRWFolders.put(folderName, folder);
			} else {
				call(() -> folder.open(Folder.READ_ONLY));
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

	@SuppressWarnings("resource")
	public ImmutableSet<Message> searchIn(ImapSearchPredicate term, Folder folder) {
		checkState(folder.isOpen());

		final Message[] asArray = call(() -> folder.search(term.term));
		final ImmutableSet<Message> found = ImmutableSet.copyOf(asArray);
		final Optional<Message> notMatching = found.stream().filter(term.predicate.negate()).limit(1)
				.collect(MoreCollectors.toOptional());
		if (notMatching.isPresent()) {
			throw new VerifyException(getDescription(notMatching.get()));
		}
		return found;
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

			final EmailAddress to = email.getTo();
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

	public ImmutableSet<Message> send(Collection<Email> emails, EmailAddress fromAddress) {
		checkState(transport != null);

		final ImmutableSet.Builder<Message> messagesBuilder = ImmutableSet.builder();
		for (Email email : emails) {
			final MimeMessage message = getMessage(email, fromAddress.asInternetAddress());
			call(() -> transport.sendMessage(message, message.getAllRecipients()));

			messagesBuilder.add(message);
		}
		LOGGER.info("Messages sent.");
		return messagesBuilder.build();
	}

	public void saveInto(Collection<Message> messages, Folder folder) {
		checkState(messages.isEmpty() || folder.isOpen());
		checkState(messages.isEmpty() || (folder.getMode() == Folder.READ_WRITE));

		final Message[] asArray = messages.toArray(new Message[messages.size()]);
		call(() -> folder.appendMessages(asArray));
	}

	@Override
	public void close() throws UncheckedMessagingException {
		if (transportSession != null) {
			call(() -> transport.close());
		}

		for (Folder folder : openRWFolders.values()) {
			call(() -> folder.close());
		}
		openRWFolders.clear();

		for (Folder folder : openReadFolders.values()) {
			call(() -> folder.close());
		}
		openReadFolders.clear();

		if (store != null) {
			call(() -> store.close());
		}
	}

}
