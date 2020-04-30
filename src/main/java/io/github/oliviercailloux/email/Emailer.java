package io.github.oliviercailloux.email;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.mail.Address;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.grade.comm.Email;
import io.github.oliviercailloux.utils.Utils;
import io.github.oliviercailloux.xml.XmlUtils;

/**
 *
 * TODO Consider using https://protonmail.com/fr/ instead.
 *
 * @author Olivier Cailloux
 *
 */
public class Emailer {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Emailer.class);

	public static final String USERNAME_DAUPHINE = "ocailloux@dauphine.fr";

	public static final String USERNAME_OTHERS = "olivier.cailloux";

	private static final boolean DO_CHECK = true;

	public static Message send(Email email) {
		return send(ImmutableSet.of(email)).stream().collect(MoreCollectors.onlyElement());
	}

	public static ImmutableSet<Message> searchIn(SearchTerm term, Folder folder) {
		if (DO_CHECK) {
			getAllTerms(term).filter(s -> s instanceof RecipientTerm).map(s -> (RecipientTerm) s)
					.forEach(t -> getAndCheck(t, folder));
		}
		final Message[] asArray = Utils.getOrThrow(() -> folder.search(enlarge(term)));
		return ImmutableSet.copyOf(asArray);
	}

	private static Stream<SearchTerm> getAllTerms(SearchTerm term) {
		if (term instanceof AndTerm) {
			final AndTerm andTerm = (AndTerm) term;
			return Arrays.stream(andTerm.getTerms()).flatMap(Emailer::getAllTerms);
		} else if (term instanceof OrTerm) {
			final OrTerm orTerm = (OrTerm) term;
			return Arrays.stream(orTerm.getTerms()).flatMap(Emailer::getAllTerms);
		} else {
			return Stream.of(term);
		}
	}

	private static SearchTerm enlarge(SearchTerm term) {
		if (term instanceof FromTerm) {
			final FromTerm fromTerm = (FromTerm) term;
			final Address address = fromTerm.getAddress();
			final String stringAddress;
			if (address instanceof InternetAddress) {
				InternetAddress iAddress = (InternetAddress) address;
				stringAddress = iAddress.getAddress();
			} else {
				stringAddress = address.toString();
			}
			return new FromStringTerm(getPart(stringAddress));
		} else if (term instanceof RecipientTerm) {
			final RecipientTerm rTerm = (RecipientTerm) term;
			final RecipientType type = rTerm.getRecipientType();
			final Address address = rTerm.getAddress();
			final String stringAddress;
			if (address instanceof InternetAddress) {
				InternetAddress iAddress = (InternetAddress) address;
				stringAddress = iAddress.getAddress();
			} else {
				stringAddress = address.toString();
			}
			return new RecipientStringTerm(type, getPart(stringAddress));
		} else if (term instanceof FromStringTerm) {
			final FromStringTerm fromTerm = (FromStringTerm) term;
			final boolean ignoreCase = fromTerm.getIgnoreCase();
			verify(ignoreCase,
					"I don’t know how you created a FromStringTerm that considers case, and I can’t create one myself.");
			final String pattern = fromTerm.getPattern();
			return new FromStringTerm(getPart(pattern));
		} else if (term instanceof RecipientStringTerm) {
			final RecipientStringTerm rTerm = (RecipientStringTerm) term;
			final RecipientType type = rTerm.getRecipientType();
			final boolean ignoreCase = rTerm.getIgnoreCase();
			verify(ignoreCase,
					"I don’t know how you created a FromStringTerm that considers case, and I can’t create one myself.");
			final String pattern = rTerm.getPattern();
			return new RecipientStringTerm(type, getPart(pattern));
		} else if (term instanceof AndTerm) {
			final AndTerm andTerm = (AndTerm) term;
			final ImmutableList<SearchTerm> enlargedTerms = Arrays.stream(andTerm.getTerms()).map(Emailer::enlarge)
					.collect(ImmutableList.toImmutableList());
			return new AndTerm(enlargedTerms.toArray(new SearchTerm[enlargedTerms.size()]));
		} else if (term instanceof OrTerm) {
			final OrTerm orTerm = (OrTerm) term;
			final ImmutableList<SearchTerm> enlargedTerms = Arrays.stream(orTerm.getTerms()).map(Emailer::enlarge)
					.collect(ImmutableList.toImmutableList());
			return new OrTerm(enlargedTerms.toArray(new SearchTerm[enlargedTerms.size()]));
		} else {
			return term;
		}
	}

	/**
	 * Checks that enlarging the search term returns all possible matches by looking
	 * for other search queries and filtering manually. To be used by scanning the
	 * tree of criteria before querying effectively.
	 *
	 * Note that this logic (and hence this check) will hopefully work under
	 * Outlook, but might fail under Gmail, because Gmail (sometimes?) finds less
	 * results when searching for sub-strings, not more.
	 */
	private static ImmutableSet<Message> getAndCheck(RecipientTerm searchTerm, Folder folder) {
		final Address address = searchTerm.getAddress();
		checkArgument(address instanceof InternetAddress);
		final InternetAddress iAddress = (InternetAddress) address;

		final ImmutableSet<Message> partialAddressSearch = searchIn(
				new RecipientStringTerm(RecipientType.TO, getPart(iAddress.getAddress())), folder, false);
		final ImmutableSet<Integer> partialAddressNumbers = partialAddressSearch.stream().map(Message::getMessageNumber)
				.collect(ImmutableSet.toImmutableSet());
		verify(partialAddressSearch.size() == partialAddressNumbers.size());

		final ImmutableSet<Message> personalSearch = searchIn(
				new RecipientStringTerm(RecipientType.TO, iAddress.getPersonal()), folder, false);
		final ImmutableSet<Integer> personalNumbers = personalSearch.stream().map(Message::getMessageNumber)
				.collect(ImmutableSet.toImmutableSet());
		verify(personalSearch.size() == personalNumbers.size());

		final ImmutableSet<Integer> addressNumbers = searchMessageNumbers(
				new RecipientStringTerm(RecipientType.TO, iAddress.getAddress()), folder);
		final ImmutableSet<Integer> fullAddressNumbers = searchMessageNumbers(
				new RecipientTerm(RecipientType.TO, iAddress), folder);

		verify(personalNumbers.containsAll(fullAddressNumbers));
		verify(partialAddressNumbers.containsAll(addressNumbers));
		verify(addressNumbers.containsAll(fullAddressNumbers));

		final ImmutableSet<Message> matching = partialAddressSearch.stream().filter(searchTerm::match)
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<Integer> matchingNumbers = matching.stream().map(Message::getMessageNumber)
				.collect(ImmutableSet.toImmutableSet());

		final ImmutableSet<Message> matchingAmongPersonal = personalSearch.stream().filter(searchTerm::match)
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<Integer> matchingAmongPersonalNumbers = matchingAmongPersonal.stream()
				.map(Message::getMessageNumber).collect(ImmutableSet.toImmutableSet());
		verify(matchingAmongPersonal.size() == matchingAmongPersonalNumbers.size());

		verify(matchingNumbers.containsAll(matchingAmongPersonalNumbers));

		return matching;
	}

	private static ImmutableSet<Integer> searchMessageNumbers(SearchTerm term, Folder folder) {
		final Message[] asArray = Utils.getOrThrow(() -> folder.search(term));
		final ImmutableSet<Integer> messageNumbers = Arrays.stream(asArray).map(Message::getMessageNumber)
				.collect(ImmutableSet.toImmutableSet());
		verify(asArray.length == messageNumbers.size());
		return messageNumbers;
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
		final ImmutableSet<Integer> personalNumbers = personalSearch.stream().map(Message::getMessageNumber)
				.collect(ImmutableSet.toImmutableSet());

		addressSearch = searchIn(new RecipientStringTerm(RecipientType.TO, address.getAddress()), folder, true);
		fullAddressSearch = searchIn(new RecipientTerm(RecipientType.TO, address), folder, true);

		Verify.verify(partialAddressSearch.containsAll(addressSearch));
		Verify.verify(addressSearch.containsAll(fullAddressSearch));

		final ImmutableSet<Integer> partialNumbers = partialAddressSearch.stream().map(Message::getMessageNumber)
				.collect(ImmutableSet.toImmutableSet());
		Verify.verify(partialAddressSearch.size() == partialNumbers.size());
		final ImmutableSet<Integer> fullNumbers = fullAddressSearch.stream().map(Message::getMessageNumber)
				.collect(ImmutableSet.toImmutableSet());
		Verify.verify(fullAddressSearch.size() == fullNumbers.size());

		final ImmutableSet<Message> matching = partialAddressSearch.stream()
				.filter(m -> Stream.of(Utils.getOrThrow(() -> m.getRecipients(RecipientType.TO)))
						.map(a -> (InternetAddress) a).anyMatch(Predicates.equalTo(address)))
				.collect(ImmutableSet.toImmutableSet());

		final ImmutableSet<Integer> matchingNumbers = matching.stream().map(Message::getMessageNumber)
				.collect(ImmutableSet.toImmutableSet());

		{
			final ImmutableSet<Integer> candidatesNumbers = partialAddressSearch.stream().map(Message::getMessageNumber)
					.collect(ImmutableSet.toImmutableSet());
			Verify.verify(partialAddressSearch.size() == candidatesNumbers.size());
			final Set<Integer> candidatesNumbersByUnionOnNumbers = Sets.union(partialNumbers, personalNumbers);
			Verify.verify(candidatesNumbers.equals(candidatesNumbersByUnionOnNumbers));
			verify(matching.size() == matchingNumbers.size());
		}
		// personalSearch, filter manually then check that there’s no new result.

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

	private static Transport getTransport(Session session) {
		try {
			return session.getTransport();
		} catch (NoSuchProviderException e) {
			throw new AssertionError(e);
		}
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
