package io.github.oliviercailloux.email;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import com.google.common.base.VerifyException;

public class EmailAddress {
	public static EmailAddress given(String address) {
		return new EmailAddress(address, Optional.empty());
	}

	public static EmailAddress given(String address, String personal) {
		return new EmailAddress(address, Optional.of(personal));
	}

	private String address;
	private Optional<String> personal;

	private EmailAddress(String address, Optional<String> personal) {
		this.address = checkNotNull(address);
		this.personal = checkNotNull(personal);
		checkArgument(personal.isEmpty() || !personal.get().isEmpty());
	}

	public String getAddress() {
		return address;
	}

	public Optional<String> getPersonal() {
		return personal;
	}

	public InternetAddress asInternetAddress() {
		try {
			final InternetAddress internetAddress = new InternetAddress(address, true);
			if (personal.isPresent()) {
				internetAddress.setPersonal(personal.get(), StandardCharsets.UTF_8.name());
			}
			return internetAddress;
		} catch (UnsupportedEncodingException e) {
			throw new VerifyException(e);
		} catch (AddressException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public String asRfcAddressString() {
		return personal.isPresent() ? (personal.get() + " <" + address + ">") : address;
	}
}
