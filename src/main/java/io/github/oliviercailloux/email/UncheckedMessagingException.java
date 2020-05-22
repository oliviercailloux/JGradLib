package io.github.oliviercailloux.email;

import javax.mail.MessagingException;

import io.github.oliviercailloux.exceptions.Unchecker;

@SuppressWarnings("serial")
public class UncheckedMessagingException extends RuntimeException {
	public static Unchecker<MessagingException, UncheckedMessagingException> MESSAGING_UNCHECKER = Unchecker
			.wrappingWith(UncheckedMessagingException::new);

	public UncheckedMessagingException(MessagingException cause) {
		super(cause);
	}
}