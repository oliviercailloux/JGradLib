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
import com.google.common.collect.Iterables;
import com.jcabi.github.Coordinates;
import com.jcabi.github.Github;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.model.Functionality;
import io.github.oliviercailloux.st_projects.model.GitHubUser;
import io.github.oliviercailloux.st_projects.model.ModelMocker;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.model.ProjectOnGitHub;
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
			writer.writeProjects(projects);
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
		final GitHubUser c1 = ModelMocker.newContributor("c1");
		final ProjectOnGitHub ghp1 = ModelMocker.newGitHubProject(p1, c1, Utils.EXAMPLE_URL);
		ModelMocker.addIssue(ghp1, "p1-f1");
		ModelMocker.addIssue(ghp1, "p1-f3");
		final List<ProjectOnGitHub> projects = ImmutableList.of(ghp1);
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
			assertEquals(4, sheet.getColumnCount());
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
			assertEquals(4, sheet.getColumnCount());
		}
	}

	@Test
	public void testWriteTwoGHProjectsDeep() throws Exception {
		final Project p1 = ModelMocker.newProject("p1", 3);
		final GitHubUser c1 = ModelMocker.newContributor("c1");
		final ProjectOnGitHub ghp1 = ModelMocker.newGitHubProject(p1, c1, Utils.EXAMPLE_URL);
		ModelMocker.addIssue(ghp1, "p1-f1");
		ModelMocker.addIssue(ghp1, "p1-f3");
		final Project p2 = ModelMocker.newProject("p2", 4);
		final GitHubUser c2 = ModelMocker.newContributor("c2");
		final ProjectOnGitHub ghp2 = ModelMocker.newGitHubProject(p2, c2, Utils.EXAMPLE_URL);
		ModelMocker.addIssue(ghp2, "p2-f1");
		ModelMocker.addIssue(ghp2, "p2-f2");
		final List<ProjectOnGitHub> projects = ImmutableList.of(ghp1, ghp2);

		final byte[] written;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			final SpreadsheetWriter writer = new SpreadsheetWriter();
			writer.setWide(false);
			writer.setOutputStream(out);
			writer.writeGitHubProjects(projects);
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
		final Project project = new Project("testrel");
		project.getFunctionalities().add(new Functionality("test1", "Descr test 1", BigDecimal.ONE));
		project.getFunctionalities().add(new Functionality("f2", "Descr f2", BigDecimal.ONE));
		project.getFunctionalities().add(new Functionality("test open", "Descr test open", BigDecimal.ONE));
		final Github gitHub = new RtGithub(Utils.getToken());
		final Repo repo = gitHub.repos().get(new Coordinates.Simple("oliviercailloux", "testrel"));
		final ProjectOnGitHub ghProject = new ProjectOnGitHub(project, repo);
		ghProject.init();
		ghProject.initAllIssuesAndEvents();

		final byte[] written;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			final SpreadsheetWriter writer = new SpreadsheetWriter();
			writer.setOutputStream(out);
			writer.writeGitHubProjects(ImmutableList.of(ghProject));
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
