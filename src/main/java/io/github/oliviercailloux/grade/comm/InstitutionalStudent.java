package io.github.oliviercailloux.grade.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbPropertyOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;

import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.email.EmailAddressAndPersonal;

@JsonbPropertyOrder({ "id", "username", "firstName", "lastName", "email" })
public class InstitutionalStudent {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(InstitutionalStudent.class);

	@JsonbCreator
	public static InstitutionalStudent withU(@JsonbProperty("id") int id, @JsonbProperty("username") String username,
			@JsonbProperty("firstName") String firstName, @JsonbProperty("lastName") String lastName,
			@JsonbProperty("email") String email) {
		final String personal = Joiner.on(" ").skipNulls().join(Strings.emptyToNull(firstName), lastName);
		return new InstitutionalStudent(id, username, firstName, lastName,
				EmailAddressAndPersonal.given(email, personal));
	}

	public static InstitutionalStudent withU(int id, String username, String firstName, String lastName,
			EmailAddress email) {
		final String personal = Joiner.on(" ").skipNulls().join(Strings.emptyToNull(firstName), lastName);
		return new InstitutionalStudent(id, username, firstName, lastName,
				EmailAddressAndPersonal.given(email.getAddress(), personal));
	}

	private final int id;
	private final String username;
	private final String firstName;
	private final String lastName;
	private final EmailAddressAndPersonal email;

	private InstitutionalStudent(int id, String username, String firstName, String lastName,
			EmailAddressAndPersonal email) {
		checkArgument(id > 0);
		checkArgument(!username.isEmpty());
		this.id = id;
		this.username = requireNonNull(username);
		this.firstName = requireNonNull(firstName);
		this.lastName = requireNonNull(lastName);
		this.email = requireNonNull(email);
	}

	public int getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public EmailAddressAndPersonal getEmail() {
		return email;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof InstitutionalStudent)) {
			return false;
		}
		final InstitutionalStudent s2 = (InstitutionalStudent) o2;
		return id == s2.id && Objects.equals(username, s2.username) && Objects.equals(firstName, s2.firstName)
				&& Objects.equals(lastName, s2.lastName) && Objects.equals(email, s2.email);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, username, firstName, lastName, email);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("id", id).add("username", username).add("first name", firstName)
				.add("last name", lastName).add("email", email).toString();
	}
}
