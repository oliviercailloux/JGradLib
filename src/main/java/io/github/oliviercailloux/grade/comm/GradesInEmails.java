package io.github.oliviercailloux.grade.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.email.UncheckedMessagingException.MESSAGING_UNCHECKER;

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
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonSimpleGrade;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class GradesInEmails implements AutoCloseable {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GradesInEmails.class);

  public static final String FILE_NAME = "data.json";

  public static final String MIME_SUBTYPE = "json";

  private static ImmutableList<MimeBodyPart> getParts(MimeMessage source)
      throws IOException, MessagingException {
    final MimeMultipart multipart = (MimeMultipart) source.getContent();
    final ImmutableList.Builder<MimeBodyPart> partsBuilder = ImmutableList.<MimeBodyPart>builder();
    for (int i = 0; i < multipart.getCount(); ++i) {
      final MimeBodyPart part = (MimeBodyPart) multipart.getBodyPart(i);
      partsBuilder.add(part);
    }
    final ImmutableList<MimeBodyPart> parts = partsBuilder.build();
    return parts;
  }

  public static Email asEmail(EmailAddressAndPersonal studentAddress, String gradeName, Grade grade,
      Stats stats, Map<Integer, Double> quartiles) {
    final HtmlGrades htmler = HtmlGrades.newInstance();
    htmler.setTitle("Grade " + gradeName);
    if (stats.count() >= 20) {
      htmler.setStats(stats);
      htmler.setQuantiles(quartiles);
    }
    final Document doc = htmler.asHtmlDoc(grade);
    final Email email = Email.withDocumentAndFile(doc, FILE_NAME,
        JsonSimpleGrade.toJson(grade).toString(), MIME_SUBTYPE, studentAddress);
    return email;
  }

  public static GradesInEmails newInstance() {
    return new GradesInEmails();
  }

  private final Emailer emailer;

  private Folder folder;

  private ImmutableSet<EmailAddress> recipientsFilter;
  private Range<Instant> sentFilter;

  private GradesInEmails() {
    emailer = Emailer.instance();
    folder = null;
    recipientsFilter = null;
    sentFilter = null;
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
   * @param recipients {@code null} for no filter
   */
  public void filterRecipients(Set<EmailAddress> recipients) {
    recipientsFilter = recipients == null ? null : ImmutableSet.copyOf(recipients);
  }

  public void filterSent(Range<Instant> filter) {
    sentFilter = filter;
  }

  Optional<Grade> toGrade(Message source) {
    return getGradeMessage(source).map(GradeMessage::getGrade);
  }

  private Optional<GradeMessage> getGradeMessage(Message source) {
    try {
      LOGGER.debug("Getting GradeMessage from message {}, content type: {}.",
          source.getMessageNumber(), source.getContentType());
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
      final Optional<Grade> grade = matchingPart.map(this::getGrade);
      return grade.map(g -> GradeMessage.given(g, source));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (MessagingException e) {
      throw new IllegalStateException(e);
    }
  }

  private Grade getGrade(MimeBodyPart part) {
    final Grade grade;
    try (InputStreamReader reader =
        new InputStreamReader(part.getInputStream(), StandardCharsets.UTF_8)) {
      final String content = CharStreams.toString(reader);
      grade = JsonSimpleGrade.asGrade(content);
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
   * @param recipients {@code null} for no filter.
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
   * @param recipients {@code null} for no filter.
   */
  private ImmutableSet<Message> getMessagesTo(Set<EmailAddress> recipients,
      String subjectStartsWith) {
    final ImapSearchPredicate subjectContains =
        ImapSearchPredicate.subjectContains(subjectStartsWith);
    final ImapSearchPredicate matchesAddress = recipients == null ? ImapSearchPredicate.TRUE
        : ImapSearchPredicate.orList(recipients.stream()
            .map(r -> ImapSearchPredicate.recipientAddressEquals(RecipientType.TO, r.getAddress()))
            .collect(ImmutableSet.toImmutableSet()));
    /*
     * If too complex, we search everything, because Zoho (and, I suspect, many others) do not
     * implement this correctly.
     */
    final ImapSearchPredicate effectiveMatchesAddress =
        (recipients == null || recipients.size() >= 2) ? ImapSearchPredicate.TRUE : matchesAddress;
    final ImapSearchPredicate sentWithin =
        sentFilter == null ? ImapSearchPredicate.TRUE : ImapSearchPredicate.sentWithin(sentFilter);

    final ImapSearchPredicate searchTerm =
        subjectContains.andSatisfy(effectiveMatchesAddress).andSatisfy(sentWithin);

    /* We filter manually in all cases for simplicity of the code. */
    final ImmutableSet<Message> matchingWidened = emailer.searchIn(folder, searchTerm);
    LOGGER.debug("Got all '{}' messages ({}).", subjectStartsWith, matchingWidened.size());
    emailer.fetchHeaders(folder, matchingWidened);
    /*
     * We also need to filter for subjects really starting with the predicate, not just containing
     * it.
     */
    final ImmutableSet<Message> matching = matchingWidened.stream()
        .filter(matchesAddress.getPredicate()).filter(m -> MESSAGING_UNCHECKER
            .getUsing(() -> m.getSubject()).toLowerCase().startsWith(subjectStartsWith))
        .collect(ImmutableSet.toImmutableSet());
    LOGGER.debug("Got all '{}' matching messages ({}).", subjectStartsWith, matching.size());
    return matching;
  }

  public ImmutableTable<EmailAddress, String, Grade> getLastGrades() {
    checkState(folder != null);

    final ImmutableTable<EmailAddress, String, Grade> grades =
        getLastGradesToInternal(recipientsFilter, "Grade ".toLowerCase());
    return grades;
  }

  public ImmutableMap<EmailAddress, Grade> getLastGrades(String prefix) {
    checkState(folder != null);
    return getLastGradesToInternal(recipientsFilter, ("Grade " + prefix).toLowerCase())
        .column(prefix);
  }

  @Override
  public void close() {
    emailer.close();
  }

  /**
   * @param recipients {@code null} for no filter.
   */
  private ImmutableTable<EmailAddress, String, Grade>
      getLastGradesToInternal(Set<EmailAddress> recipients, String subjectPattern) {
    final ImmutableSet<Message> matching = getMessagesTo(recipients, subjectPattern);
    /*
     * First, reduce the number of messages to fetch (which takes about 30 seconds for 500
     * messages).
     */
    final Comparator<Message> comparing =
        Comparator.comparing(MESSAGING_UNCHECKER.wrapFunction(Message::getSentDate));
    final ImmutableTable<EmailAddress, String,
        Message> messages = matching.stream()
            .collect(ImmutableTable.toImmutableTable(m -> getUniqueRecipientAmong(m, recipients),
                m -> getGradeSubject(m), m -> m,
                (m1, m2) -> Stream.of(m1, m2).max(comparing).get()));

    emailer.fetchMessages(folder, ImmutableSet.copyOf(messages.values()));

    final Table<EmailAddress, String, Optional<Grade>> gradesOpt =
        Tables.transformValues(messages, this::toGrade);
    return gradesOpt.cellSet().stream().filter(c -> c.getValue().isPresent()).collect(ImmutableTable
        .toImmutableTable(c -> c.getRowKey(), c -> c.getColumnKey(), o -> o.getValue().get()));
  }

  public Optional<Grade> getLastGradeTo(EmailAddress recipient, String prefix) {
    checkArgument(recipientsFilter == null || recipientsFilter.contains(recipient));
    return Optional.ofNullable(
        getLastGradesToInternal(ImmutableSet.of(recipient), ("Grade " + prefix).toLowerCase())
            .column(prefix).get(recipient));
  }
}
