package io.github.oliviercailloux.email;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.mail.internet.InternetAddress;

import com.google.common.base.MoreObjects;
import com.google.common.base.VerifyException;

public class EmailAddressAndPersonal {
	public static EmailAddressAndPersonal given(String address) {
		return new EmailAddressAndPersonal(EmailAddress.given(address), "");
	}

	public static EmailAddressAndPersonal given(String address, String personal) {
		return new EmailAddressAndPersonal(EmailAddress.given(address), personal);
	}

	private EmailAddress address;
	private String personal;

	private EmailAddressAndPersonal(EmailAddress address, String personal) {
		this.address = checkNotNull(address);
		this.personal = checkNotNull(personal);
	}

	public EmailAddress getAddress() {
		return address;
	}

	/**
	 * @return empty for no personal.
	 */
	public String getPersonal() {
		return personal;
	}

	public InternetAddress asInternetAddress() {
		try {
			final InternetAddress internetAddress = address.asInternetAddress();
			if (!personal.isEmpty()) {
				internetAddress.setPersonal(personal, StandardCharsets.UTF_8.name());
			}
			return internetAddress;
		} catch (UnsupportedEncodingException e) {
			throw new VerifyException(e);
		}
	}

	public String asRfcAddressString() {
		return personal.isEmpty() ? address.getAddress() : (personal + " <" + address + ">");
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof EmailAddressAndPersonal)) {
			return false;
		}
		final EmailAddressAndPersonal e2 = (EmailAddressAndPersonal) o2;
		return address.equals(e2.address) && personal.equals(e2.personal);
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, personal);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("address", address).add("personal", personal).toString();
	}
}
