package io.github.oliviercailloux.st_projects.ex2;

import static io.github.oliviercailloux.st_projects.ex2.Ex2Criterion.ANNOT;
import static io.github.oliviercailloux.st_projects.ex2.Ex2Criterion.ENC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.io.Resources;

import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHubKnown;
import io.github.oliviercailloux.st_projects.model.StudentOnMyCourse;

class GradeJsonTest {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GradeJsonTest.class);

	@Test
	public void singleWriteJson() throws Exception {
		final String expectedFormatted = Resources.toString(this.getClass().getResource("Single grade.json"),
				StandardCharsets.UTF_8);
		final String expected = expectedFormatted.replace("\n", "").replace(" ", "");

		final SingleGrade grade = SingleGrade.max(ENC);
		final String written;
		try (Jsonb jsonb = JsonbBuilder.create()) {
			written = jsonb.toJson(grade);
			LOGGER.info("Serialized pretty json: {}.", written);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		assertEquals(expected, written);
	}

	@Test
	public void studentGitHubWriteJson() throws Exception {
		final String expectedFormatted = Resources.toString(this.getClass().getResource("Student GitHub.json"),
				StandardCharsets.UTF_8);
		final String expected = expectedFormatted.replace("\n", "").replace(" ", "");

		final StudentOnGitHubKnown studentGH = getStudentOnGitHubKnown();
		final String written;
		try (Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withAdapters(new KnownAsJson()))) {
			written = jsonb.toJson(studentGH);
			LOGGER.info("Serialized pretty json: {}.", written);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		assertEquals(expected, written);
	}

	@Test
	public void studentMyCourseReadJson() throws Exception {
		final StudentOnMyCourse expected = StudentOnMyCourse.with(1, "f", "l", "u");
		final String json = Resources.toString(this.getClass().getResource("Student MyCourse.json"),
				StandardCharsets.UTF_8);
		final StudentOnMyCourse read;
		try (Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true))) {
			read = jsonb.fromJson(json, StudentOnMyCourse.class);
			LOGGER.info("Deserialized: {}.", read);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		assertEquals(expected, read);
	}

	@Test
	public void gradeWriteJson() {
		final SingleGrade grade1 = SingleGrade.max(ENC);
		final SingleGrade grade2 = SingleGrade.zero(ANNOT);
		final Grade grade = Grade.of(getStudentOnGitHubKnown().asStudentOnGitHub(), ImmutableSet.of(grade1, grade2));
		final String json;
		try (Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withAdapters(new GHAsJson()).withFormatting(true))) {
			// TODO does not chain adapters, it seems
//				.create(new JsonbConfig().withAdapters(new AsKnown(), new KnownAsJson()).withFormatting(true))) {
			json = jsonb.toJson(grade);
			LOGGER.info("Serialized pretty json: {}.", json);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	public void studentMyCourseWriteJson() throws Exception {
		final String expectedFormatted = Resources.toString(this.getClass().getResource("Student MyCourse.json"),
				StandardCharsets.UTF_8);
		final String expected = expectedFormatted.replace("\n", "").replace(" ", "");

		final StudentOnMyCourse student = StudentOnMyCourse.with(1, "f", "l", "u");
		final String written;
		try (Jsonb jsonb = JsonbBuilder.create()) {
			written = jsonb.toJson(student);
			LOGGER.info("Serialized pretty json: {}.", written);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		assertEquals(expected, written);
	}

	@Test
	public void gradeReadJson() throws Exception {
		final SingleGrade grade1 = SingleGrade.max(ENC);
		final SingleGrade grade2 = SingleGrade.zero(ANNOT);
		final Grade expected = Grade.of(getStudentOnGitHubKnown().asStudentOnGitHub(), ImmutableSet.of(grade1, grade2));
		final String json = Resources.toString(this.getClass().getResource("Grade.json"), StandardCharsets.UTF_8);
		final Grade read;
		try (Jsonb jsonb = JsonbBuilder
				.create(new JsonbConfig().withAdapters(new AsEx2Criterion(), new GHAsJson()).withFormatting(true))) {
			read = jsonb.fromJson(json, Grade.class);
			LOGGER.info("Deserialized: {}.", read);
		}
		assertEquals(expected, read);
	}

	@Test
	public void gradeReadJsonManually() throws Exception {
		final SingleGrade grade1 = SingleGrade.max(ENC);
		final SingleGrade grade2 = SingleGrade.zero(ANNOT);
		final Grade expected = Grade.of(getStudentOnGitHubKnown().asStudentOnGitHub(), ImmutableSet.of(grade1, grade2));
		final String jsonStr = Resources.toString(this.getClass().getResource("Grade.json"), StandardCharsets.UTF_8);

		final GHAsJson ghAsJson = new GHAsJson();
		final JsonObject json;
		try (JsonReader jr = Json.createReader(new StringReader(jsonStr))) {
			json = jr.readObject();
		}
		final JsonObject st = json.getJsonObject("student");
		final StudentOnGitHub student = ghAsJson.adaptFromJson(st);
		final JsonArray grades = json.getJsonArray("gradeValues");
		final Builder<SingleGrade> gradesBuilder = ImmutableSet.builder();
		for (JsonValue grade : grades) {
			try (Jsonb jsonb = JsonbBuilder
					.create(new JsonbConfig().withAdapters(new AsEx2Criterion()).withFormatting(true))) {
				final SingleGrade thisGrade = jsonb.fromJson(grade.toString(), SingleGrade.class);
				gradesBuilder.add(thisGrade);
				LOGGER.info("Deserialized: {}.", thisGrade);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}
		final Grade grade = Grade.of(student, gradesBuilder.build());
		assertEquals(expected, grade);
	}

	@Test
	public void studentGitHubReadJson() throws Exception {
		final StudentOnGitHubKnown studentGH = getStudentOnGitHubKnown();

		final String json = Resources.toString(this.getClass().getResource("Student GitHub.json"),
				StandardCharsets.UTF_8);
		final StudentOnGitHubKnown read;
		try (Jsonb jsonb = JsonbBuilder
				.create(new JsonbConfig().withAdapters(new KnownAsJson()).withFormatting(true))) {
			read = jsonb.fromJson(json, StudentOnGitHubKnown.class);
			LOGGER.info("Deserialized: {}.", read);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		assertEquals(studentGH, read);
	}

	private StudentOnGitHubKnown getStudentOnGitHubKnown() {
		final StudentOnMyCourse studentMC = StudentOnMyCourse.with(1, "f", "l", "u");
		final StudentOnGitHubKnown studentGH = StudentOnGitHubKnown.with(studentMC, "g");
		return studentGH;
	}

	@Test
	public void singleReadJson() throws Exception {
		final SingleGrade expected = SingleGrade.max(ENC);
		final String json = Resources.toString(this.getClass().getResource("Single grade.json"),
				StandardCharsets.UTF_8);
		final SingleGrade read;
		try (Jsonb jsonb = JsonbBuilder
				.create(new JsonbConfig().withAdapters(new AsEx2Criterion()).withFormatting(true))) {
			read = jsonb.fromJson(json, SingleGrade.class);
			LOGGER.info("Deserialized: {}.", read);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		assertEquals(expected, read);
	}

}
