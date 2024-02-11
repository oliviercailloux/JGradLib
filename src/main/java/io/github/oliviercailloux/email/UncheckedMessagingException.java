package io.github.oliviercailloux.email;

import io.github.oliviercailloux.jaris.exceptions.Unchecker;
import jakarta.mail.MessagingException;

@SuppressWarnings("serial")
public class UncheckedMessagingException extends RuntimeException {
  public static Unchecker<MessagingException, UncheckedMessagingException> MESSAGING_UNCHECKER =
      Unchecker.wrappingWith(UncheckedMessagingException::new);

  public UncheckedMessagingException(MessagingException cause) {
    super(cause);
  }
}
