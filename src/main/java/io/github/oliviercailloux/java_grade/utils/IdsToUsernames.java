package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import ebx.ebx_dataservices.StandardException;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.comm.InstitutionalStudent;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.comm.json.JsonStudentOnGitHubKnown;
import io.github.oliviercailloux.jaris.exceptions.Unchecker;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.supann.QueriesHelper;
import io.github.oliviercailloux.supann.SupannQuerier;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.JAXBElement;
import schemas.ebx.dataservices_1.StudentType.Root.Student;

public class IdsToUsernames {
	public static final Unchecker<StandardException, IllegalStateException> SUPANN_UNCHECKER = Unchecker
			.wrappingWith(IllegalStateException::new);

	/** Broken (I think) since API change in Supann. */
	public static void main(String[] args) throws Exception {
		QueriesHelper.setDefaultAuthenticator();
		final SupannQuerier supannQuerier = new SupannQuerier();
		@SuppressWarnings("all")
		final Type superclass = new HashMap<String, Integer>() {
		}.getClass().getGenericSuperclass();

		final Map<String, Integer> idsByGitHubUsername = JsonbUtils.fromJson(Files.readString(Path.of("gh-id.json")),
				superclass);

		final ImmutableSet<StudentOnGitHubKnown> known = idsByGitHubUsername.entrySet().stream()
				.map(SUPANN_UNCHECKER.wrapFunction(e -> StudentOnGitHubKnown.with(GitHubUsername.given(e.getKey()),
						toInstitutional(supannQuerier.getStudent(e.getValue().toString())))))
				.collect(ImmutableSet.toImmutableSet());

		final PrintableJsonObject asJson = JsonbUtils.toJsonObject(known, JsonStudentOnGitHubKnown.asAdapter());
		Files.writeString(Path.of("usernames.json"), asJson.toString());
	}

	private static InstitutionalStudent toInstitutional(Student student) {
		checkNotNull(student);
		final String id = student.getUuid();
		checkArgument(id != null);
		final JAXBElement<String> firstname = student.getSurname();
		checkArgument(firstname != null && !firstname.isNil());
		final JAXBElement<String> lastname = student.getGivenName();
		checkArgument(lastname != null && !lastname.isNil());
		final JAXBElement<String> login = student.getSupannAliasLogin();
		checkArgument(login != null && !login.isNil());
		return InstitutionalStudent.withU(Integer.parseInt(id), login.getValue(), firstname.getValue(),
				lastname.getValue(), student.getMail().getValue());
	}
}
