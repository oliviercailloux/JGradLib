package io.github.oliviercailloux.email;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.VerifyException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.Objects;

public class EmailAddress {
	public static EmailAddress given(String address) {
		return new EmailAddress(address);
	}

	private final String address;

	private EmailAddress(String address) {
		this.address = checkNotNull(address);
		checkArgument(address.contains("@"), address);
		try {
			@SuppressWarnings("unused")
			final InternetAddress internetAddress = new InternetAddress(address, true);
		} catch (AddressException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public String getAddress() {
		return address;
	}

	public InternetAddress asInternetAddress() {
		/** Need defensive copy. */
		try {
			return new InternetAddress(address, true);
		} catch (AddressException e) {
			throw new VerifyException(e);
		}
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof EmailAddress)) {
			return false;
		}
		final EmailAddress e2 = (EmailAddress) o2;
		return address.equals(e2.address);
	}

	@Override
	public int hashCode() {
		return Objects.hash(address);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("address", address).toString();
	}
}
