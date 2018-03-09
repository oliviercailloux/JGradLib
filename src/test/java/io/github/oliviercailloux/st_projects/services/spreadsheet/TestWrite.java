package io.github.oliviercailloux.st_projects.services.spreadsheet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;
import org.odftoolkit.simple.SpreadsheetDocument;
import org.odftoolkit.simple.table.Cell;
import org.odftoolkit.simple.table.Table;
import org.odftoolkit.simple.text.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.jcabi.github.Coordinates;

import io.github.oliviercailloux.git_hub_gql.UserQL;
import io.github.oliviercailloux.st_projects.model.Functionality;
import io.github.oliviercailloux.st_projects.model.ModelMocker;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.model.RepositoryWithIssuesWithHistoryQL;
import io.github.oliviercailloux.st_projects.services.git_hub.GitHubFetcher;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestWrite {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestWrite.class);

	public void debugRead() throws Exception {
		try (SpreadsheetDocument doc = SpreadsheetDocument.loadDocument("out.ods")) {
			final Table sheet = Iterables.getOnlyElement(doc.getTableList());
			final Cell cell = sheet.getCellByPosition(1, 0);
			LOGGER.info(asSimplifiedString(cell));
		}
	}

	@Test
	public void testWriteNoProjects() throws Exception {
		final List<Project> projects = ImmutableList.of();
		final byte[] written;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			final SpreadsheetWriter writer = new SpreadsheetWriter();
			writer.setOutputStream(out);
			writer.write(projects, ImmutableMap.of());
			written = out.toByteArray();
		}
		final ByteArrayInputStream input = new ByteArrayInputStream(written);
		try (SpreadsheetDocument doc = SpreadsheetDocument.loadDocument(input)) {
			assertEquals(1, doc.getTableList().size());
			final Table sheet = Iterables.getOnlyElement(doc.getTableList());
			assertEquals(1, sheet.getRowCount());
			assertEquals(1, sheet.getColumnCount());
		}
	}

	@Test
	public void testWriteOneGHProject() throws Exception {
		final Project p1 = ModelMocker.newProject("p1", 3);
		final UserQL c1 = ModelMocker.newContributor("c1");
		final RepositoryWithIssuesWithHistoryQL ghp1 = ModelMocker.newGitHubProject(c1, Utils.EXAMPLE_URL);
		ModelMocker.addIssue(ghp1, "p1-f1");
		ModelMocker.addIssue(ghp1, "p1-f3");
		final byte[] written;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			final SpreadsheetWriter writer = new SpreadsheetWriter();
			writer.setOutputStream(out);
			writer.write(ImmutableList.of(p1), ImmutableMap.of(p1, ghp1));
			written = out.toByteArray();
		}
		final ByteArrayInputStream input = new ByteArrayInputStream(written);
		try (SpreadsheetDocument doc = SpreadsheetDocument.loadDocument(input)) {
			assertEquals(1, doc.getTableList().size());
			final Table sheet = Iterables.getOnlyElement(doc.getTableList());
			assertTrue(sheet.getRowCount() >= 3);
			assertEquals(4, sheet.getColumnCount());
		}
		save(written);
	}

	@Test
	public void testWriteOneProject() throws Exception {
		LOGGER.info("Started write p1.");
		final Builder<Functionality> functionalitiesBuilder = ImmutableList.builder();
		functionalitiesBuilder.add(new Functionality("f11", "d11", BigDecimal.ONE));
		functionalitiesBuilder.add(new Functionality("f12", "d12", BigDecimal.TEN));
		final Project p1 = Project.from("p1", functionalitiesBuilder.build());
		final byte[] written;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			final SpreadsheetWriter writer = new SpreadsheetWriter();
			writer.setOutputStream(out);
			writer.write(ImmutableList.of(p1), ImmutableMap.of());
			written = out.toByteArray();
		}
		final ByteArrayInputStream input = new ByteArrayInputStream(written);
		try (SpreadsheetDocument doc = SpreadsheetDocument.loadDocument(input)) {
			assertEquals(1, doc.getTableList().size());
			final Table sheet = Iterables.getOnlyElement(doc.getTableList());
			assertTrue(sheet.getRowCount() >= 3);
			assertEquals(4, sheet.getColumnCount());
		}
	}

	@Test
	public void testWriteTwoGHProjectsDeep() throws Exception {
		final Project p1 = ModelMocker.newProject("p1", 3);
		final UserQL c1 = ModelMocker.newContributor("c1");
		final RepositoryWithIssuesWithHistoryQL ghp1 = ModelMocker.newGitHubProject(c1, Utils.EXAMPLE_URL);
		ModelMocker.addIssue(ghp1, "p1-f1");
		ModelMocker.addIssue(ghp1, "p1-f3");
		final Project p2 = ModelMocker.newProject("p2", 4);
		final UserQL c2 = ModelMocker.newContributor("c2");
		final RepositoryWithIssuesWithHistoryQL ghp2 = ModelMocker.newGitHubProject(c2, Utils.EXAMPLE_URL);
		ModelMocker.addIssue(ghp2, "p2-f1");
		ModelMocker.addIssue(ghp2, "p2-f2");

		final byte[] written;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			final SpreadsheetWriter writer = new SpreadsheetWriter();
			writer.setWide(false);
			writer.setOutputStream(out);
			writer.write(ImmutableList.of(p1, p2), ImmutableMap.of(p1, ghp1, p2, ghp2));
			written = out.toByteArray();
		}
		save(written);
		final ByteArrayInputStream input = new ByteArrayInputStream(written);
		try (SpreadsheetDocument doc = SpreadsheetDocument.loadDocument(input)) {
			assertEquals(1, doc.getTableList().size());
			final Table sheet = Iterables.getOnlyElement(doc.getTableList());
			assertTrue(sheet.getRowCount() >= 7);
			assertEquals(4, sheet.getColumnCount());
		}
	}

	@Test
	public void testWriteWithAssignees() throws Exception {
		final Builder<Functionality> functionalitiesBuilder = ImmutableList.builder();
		functionalitiesBuilder.add(new Functionality("test1", "Descr test 1", BigDecimal.ONE));
		functionalitiesBuilder.add(new Functionality("f2", "Descr f2", BigDecimal.ONE));
		functionalitiesBuilder.add(new Functionality("test open", "Descr test open", BigDecimal.ONE));
		final Project project = Project.from("testrel", functionalitiesBuilder.build());
		final Coordinates.Simple coords = new Coordinates.Simple("oliviercailloux", "testrel");
		final RepositoryWithIssuesWithHistoryQL ghProject;
		try (GitHubFetcher factory = GitHubFetcher.using(Utils.getToken())) {
			ghProject = factory.getProject(coords).get();
		}
		final byte[] written;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			final SpreadsheetWriter writer = new SpreadsheetWriter();
			writer.setOutputStream(out);
			writer.write(ImmutableList.of(project), ImmutableMap.of(project, ghProject));
			written = out.toByteArray();
		}
		final ByteArrayInputStream input = new ByteArrayInputStream(written);
		try (SpreadsheetDocument doc = SpreadsheetDocument.loadDocument(input)) {
			assertEquals(1, doc.getTableList().size());
			final Table sheet = Iterables.getOnlyElement(doc.getTableList());
			assertEquals(5, sheet.getRowCount());
			assertEquals(4, sheet.getColumnCount());
		}
		save(written);
	}

	@SuppressWarnings("unused")
	private String asSimplifiedString(Cell cell) {
		final ToStringHelper helper = MoreObjects.toStringHelper("Simplified cell");
		helper.add("Style", cell.getCellStyleName()).add("Display", cell.getDisplayText()).add("Font", cell.getFont())
				.add("Format string", cell.getFormatString()).add("Formula", cell.getFormula())
				.add("Paragraphs", ImmutableList.copyOf(cell.getParagraphIterator()))
				.add("String value", cell.getStringValue());
		return helper.toString();
	}

	@SuppressWarnings("unused")
	private String asSimplifiedString(Paragraph paragraph) {
		final ToStringHelper helper = MoreObjects.toStringHelper("Simplified paragraph");
		helper.add("Text content", paragraph.getTextContent());
		return helper.toString();
	}

	@SuppressWarnings("unused")
	private void save(final byte[] written) throws Exception {
		try (SpreadsheetDocument doc = SpreadsheetDocument.loadDocument(new ByteArrayInputStream(written))) {
			doc.save("out.ods");
		}
	}

}
