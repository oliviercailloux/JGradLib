package io.github.oliviercailloux.email;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.email.UncheckedMessagingException.MESSAGING_UNCHECKER;

import com.google.common.base.Predicates;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.function.Predicate;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;
import javax.mail.search.AndTerm;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.FromStringTerm;
import javax.mail.search.FromTerm;
import javax.mail.search.OrTerm;
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.RecipientTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SentDateTerm;
import javax.mail.search.SubjectTerm;

/**
 *
 * Either is a {@link ImapSearchPredicate#FALSE}, or contains no FALSE. This
 * permits to ensure that the Javamail implementation is not at a loss with such
 * thing.
 *
 */
public class ImapSearchPredicate implements Predicate<Message> {
	@SuppressWarnings("serial")
	private static class TrueSearchTerm extends SearchTerm {
		@Override
		public boolean match(Message msg) {
			return true;
		}
	}

	@SuppressWarnings("serial")
	private static class FalseSearchTerm extends SearchTerm {
		@Override
		public boolean match(Message msg) {
			return false;
		}
	}

	public static ImapSearchPredicate TRUE = new ImapSearchPredicate(new TrueSearchTerm(), Predicates.alwaysTrue());
	public static ImapSearchPredicate FALSE = new ImapSearchPredicate(new FalseSearchTerm(), Predicates.alwaysFalse());

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
		return new ImapSearchPredicate(new SubjectTerm(subString), m -> subjectContains(subString, m));
	}

	public static ImapSearchPredicate sentWithin(Range<Instant> range) {
		final ImapSearchPredicate lowerPredicate;
		if (range.hasLowerBound()) {
			final Instant min = range.lowerEndpoint();
			switch (range.lowerBoundType()) {
			case OPEN:
				lowerPredicate = new ImapSearchPredicate(new SentDateTerm(ComparisonTerm.GT, Date.from(min)),
						m -> getSentDate(m).isAfter(min));
				break;
			case CLOSED:
				lowerPredicate = new ImapSearchPredicate(new SentDateTerm(ComparisonTerm.GE, Date.from(min)),
						m -> !getSentDate(m).isBefore(min));
				break;
			default:
				throw new VerifyException();
			}
		} else {
			lowerPredicate = TRUE;
		}

		final ImapSearchPredicate upperPredicate;
		if (range.hasUpperBound()) {
			final Instant max = range.upperEndpoint();
			switch (range.upperBoundType()) {
			case OPEN:
				upperPredicate = new ImapSearchPredicate(new SentDateTerm(ComparisonTerm.LT, Date.from(max)),
						m -> getSentDate(m).isBefore(max));
				break;
			case CLOSED:
				upperPredicate = new ImapSearchPredicate(new SentDateTerm(ComparisonTerm.LE, Date.from(max)),
						m -> !getSentDate(m).isAfter(max));
				break;
			default:
				throw new VerifyException();
			}
		} else {
			upperPredicate = TRUE;
		}

		return lowerPredicate.andSatisfy(upperPredicate);
	}

	public static ImapSearchPredicate orList(Iterable<ImapSearchPredicate> predicates) {
		final Iterator<ImapSearchPredicate> iterator = predicates.iterator();
		if (!iterator.hasNext()) {
			return FALSE;
		}

		final ImmutableList<ImapSearchPredicate> realPredicates = Streams.stream(predicates)
				.filter(p -> !p.equals(FALSE)).collect(ImmutableList.toImmutableList());
		if (realPredicates.contains(TRUE)) {
			return TRUE;
		}

		final ImmutableList<SearchTerm> terms = realPredicates.stream().map(p -> p.term)
				.collect(ImmutableList.toImmutableList());

		final OrTerm orTerm = new OrTerm(terms.toArray(new SearchTerm[terms.size()]));

		final Predicate<Message> predicate = Streams.stream(predicates).map(p -> p.predicate)
				.reduce(Predicates.alwaysFalse(), Predicate::or);

		return new ImapSearchPredicate(orTerm, predicate);
	}

	private static boolean recipientAddressEquals(RecipientType recipientType, String address, Message m) {
		return Arrays.stream(MESSAGING_UNCHECKER.getUsing(() -> m.getRecipients(recipientType)))
				.map(a -> (InternetAddress) a).anyMatch(a -> a.getAddress().toLowerCase(Locale.ROOT).equals(address));
	}

	private static boolean fromAddressEquals(String address, Message m) {
		return Arrays.stream(MESSAGING_UNCHECKER.getUsing(m::getFrom)).map(a -> (InternetAddress) a)
				.anyMatch(a -> a.getAddress().toLowerCase(Locale.ROOT).equals(address));
	}

	private static boolean recipientFullAddressContains(RecipientType recipientType, String subString, Message m) {
		return Arrays.stream(MESSAGING_UNCHECKER.getUsing(() -> m.getRecipients(recipientType)))
				.map(a -> (InternetAddress) a).map(InternetAddress::toUnicodeString)
				.anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(subString));
	}

	private static boolean fromFullAddressContains(String subString, Message m) {
		return Arrays.stream(MESSAGING_UNCHECKER.getUsing(() -> m.getFrom())).map(a -> (InternetAddress) a)
				.anyMatch(a -> a.toUnicodeString().toLowerCase(Locale.ROOT).contains(subString));
	}

	private static boolean subjectContains(String subString, Message m) {
		return MESSAGING_UNCHECKER.getUsing(() -> m.getSubject()).toLowerCase(Locale.ROOT).contains(subString);
	}

	private static Instant getSentDate(Message m) {
		return MESSAGING_UNCHECKER.getUsing(() -> m.getSentDate().toInstant());
	}

	private final SearchTerm term;
	private final Predicate<Message> predicate;

	private ImapSearchPredicate(SearchTerm term, Predicate<Message> predicate) {
		this.term = checkNotNull(term);
		this.predicate = checkNotNull(predicate);
	}

	public SearchTerm getTerm() {
		return term;
	}

	public Predicate<Message> getPredicate() {
		return predicate;
	}

	@Override
	public boolean test(Message m) {
		final boolean test = predicate.test(m);
		verify(term.match(m) == test);
		return test;
	}

	public ImapSearchPredicate andSatisfy(ImapSearchPredicate other) {
		if (this.equals(FALSE) || other.equals(FALSE)) {
			return FALSE;
		}
		if (this.equals(TRUE)) {
			return other;
		}
		if (other.equals(TRUE)) {
			return this;
		}

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
		if (this.equals(FALSE)) {
			return other;
		}
		if (other.equals(FALSE)) {
			return this;
		}
		if (this.equals(TRUE) || other.equals(TRUE)) {
			return TRUE;
		}

		return new ImapSearchPredicate(new OrTerm(term, other.term), predicate.or(other.predicate));
	}
}