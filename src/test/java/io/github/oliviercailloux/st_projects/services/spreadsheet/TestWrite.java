package io.github.oliviercailloux.st_projects.services.spreadsheet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes.Name;

import org.junit.Test;
import org.mockito.Mockito;
import org.odftoolkit.simple.SpreadsheetDocument;
import org.odftoolkit.simple.table.Cell;
import org.odftoolkit.simple.table.Table;
import org.odftoolkit.simple.text.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import io.github.oliviercailloux.st_projects.model.Contributor;
import io.github.oliviercailloux.st_projects.model.Functionality;
import io.github.oliviercailloux.st_projects.model.GitHubIssue;
import io.github.oliviercailloux.st_projects.model.GitHubProject;
import io.github.oliviercailloux.st_projects.model.ModelMocker;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.git_hub.Utils;

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
			writer.writeProjects(projects);
			written = out.toByteArray();
		}
		final ByteArrayInputStream input = new ByteArrayInputStream(written);
		try (SpreadsheetDocument doc = SpreadsheetDocument.loadDocument(input)) {
			assertEquals(1, doc.getTableList().size());
			final Table sheet = Iterables.getOnlyElement(doc.getTableList());
			assertTrue(sheet.getRowCount() == 1);
			assertTrue(sheet.getColumnCount() == 1);
		}
	}

	@Test
	public void testWriteOneGHProjectWithIssues() throws Exception {
		final Project p1 = ModelMocker.newProject("p1", 2);
		final Contributor c1 = ModelMocker.newContributor("c1");
		final GitHubProject ghp1 = ModelMocker.newGitHubProject(p1, c1, Utils.EXAMPLE_URL);
		TODO : when the GHP is initialized, it does not accept any getIssue thus will make the writer crash. Second, change this test Name.class */
		final List<GitHubProject> projects = ImmutableList.of(ghp1);
		final byte[] written;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			final SpreadsheetWriter writer = new SpreadsheetWriter();
			writer.setOutputStream(out);
			writer.writeGitHubProjects(projects);
			written = out.toByteArray();
		}
		final ByteArrayInputStream input = new ByteArrayInputStream(written);
		try (SpreadsheetDocument doc = SpreadsheetDocument.loadDocument(input)) {
			assertEquals(1, doc.getTableList().size());
			final Table sheet = Iterables.getOnlyElement(doc.getTableList());
			assertTrue(sheet.getRowCount() >= 3);
			assertTrue(sheet.getColumnCount() == 3);
		}
		save(written);
	}

	@Test
	public void testWriteOneProject() throws Exception {
		LOGGER.info("Started write p1.");
		final Project p1 = new Project("p1");
		p1.getFunctionalities().add(new Functionality("f11", "d11", BigDecimal.ONE));
		p1.getFunctionalities().add(new Functionality("f12", "d12", BigDecimal.TEN));
		final List<Project> projects = ImmutableList.of(p1);
		final byte[] written;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			final SpreadsheetWriter writer = new SpreadsheetWriter();
			writer.setOutputStream(out);
			writer.writeProjects(projects);
			written = out.toByteArray();
		}
		final ByteArrayInputStream input = new ByteArrayInputStream(written);
		try (SpreadsheetDocument doc = SpreadsheetDocument.loadDocument(input)) {
			assertEquals(1, doc.getTableList().size());
			final Table sheet = Iterables.getOnlyElement(doc.getTableList());
			assertTrue(sheet.getRowCount() >= 3);
			assertTrue(sheet.getColumnCount() == 3);
		}
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

	@Test
	public void testWriteOneGHProject() throws Exception {
		final Project project = new Project("p1");
		project.getFunctionalities().add(new Functionality("p1-f1", "p1-d1", BigDecimal.valueOf(1)));
		project.getFunctionalities().add(new Functionality("p1-f2", "p1-d2", BigDecimal.valueOf(2)));
		final Project p1 = project;
		final Contributor c1 = ModelMocker.newContributor("c1");
		final GitHubProject ghp1 = ModelMocker.newGitHubProject(p1, c1, Utils.EXAMPLE_URL);
		final GitHubIssue is1 = ModelMocker.newGitHubIssue("p1-f1", Utils.EXAMPLE_URL);
		Mockito.when(ghp1.getIssue("p1-f1")).thenReturn(Optional.of(is1));
		final List<GitHubProject> projects = ImmutableList.of(ghp1);
		final byte[] written;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			final SpreadsheetWriter writer = new SpreadsheetWriter();
			writer.setOutputStream(out);
			writer.writeGitHubProjects(projects);
			written = out.toByteArray();
		}
		final ByteArrayInputStream input = new ByteArrayInputStream(written);
		try (SpreadsheetDocument doc = SpreadsheetDocument.loadDocument(input)) {
			assertEquals(1, doc.getTableList().size());
			final Table sheet = Iterables.getOnlyElement(doc.getTableList());
			assertTrue(sheet.getRowCount() >= 3);
			assertTrue(sheet.getColumnCount() == 3);
		}
		save(written);
	}

}
