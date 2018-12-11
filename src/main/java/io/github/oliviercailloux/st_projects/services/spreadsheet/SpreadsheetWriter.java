package io.github.oliviercailloux.st_projects.services.spreadsheet;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.OutputStream;
import java.net.URL;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.odftoolkit.simple.SpreadsheetDocument;
import org.odftoolkit.simple.style.StyleTypeDefinitions.VerticalAlignmentType;
import org.odftoolkit.simple.table.Cell;
import org.odftoolkit.simple.table.Table;
import org.odftoolkit.simple.text.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import io.github.oliviercailloux.git_hub.graph_ql.Repository;
import io.github.oliviercailloux.git_hub.graph_ql.User;
import io.github.oliviercailloux.st_projects.model.Functionality;
import io.github.oliviercailloux.st_projects.model.GradedProject;
import io.github.oliviercailloux.st_projects.model.IssueWithHistory;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.model.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class SpreadsheetWriter {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SpreadsheetWriter.class);

	private int curCol;

	private int curRow;

	private boolean difficultySummed;

	private Instant ignoreAfter;

	private final NumberFormat numberFormatter;

	private OutputStream out;

	private Table sheet;

	private boolean wide;

	public SpreadsheetWriter() {
		sheet = null;
		numberFormatter = NumberFormat.getNumberInstance(Locale.ENGLISH);
		curRow = 0;
		curCol = 0;
		out = null;
		difficultySummed = false;
		wide = true;
		ignoreAfter = Instant.MAX;
	}

	public Instant getIgnoreAfter() {
		return ignoreAfter;
	}

	public boolean isDifficultySummed() {
		return difficultySummed;
	}

	public boolean isWide() {
		return wide;
	}

	public void setDifficultySummed(boolean sumDifficulty) {
		this.difficultySummed = sumDifficulty;
	}

	public void setIgnoreAfter(Instant ignoreAfter) {
		this.ignoreAfter = ignoreAfter;
	}

	public void setOutputStream(OutputStream out) {
		this.out = out;
	}

	public void setWide(boolean wide) {
		this.wide = wide;
	}

	public void write(Iterable<Project> projects, Map<Project, RepositoryWithIssuesWithHistory> ghProjects)
			throws SpreadsheetException {
		try (SpreadsheetDocument doc = SpreadsheetDocument.newSpreadsheetDocument()) {
			sheet = doc.getSheetByIndex(0);
			sheet.setTableName("Projects");
			curCol = 0;
			curRow = 0;
			if (wide) {
				for (Project project : projects) {
					writeProject(project, Utils.getOptionally(ghProjects, project));
					sheet.getColumnByIndex(curCol + 2).setWidth(10);
					curRow = 0;
					curCol += 9;
				}
				for (curRow = 2; curRow < sheet.getRowCount(); ++curRow) {
					sheet.getRowByIndex(curRow).setHeight(10, false);
				}
			} else {
				for (Project project : projects) {
					final int startRow = curRow;
					writeProject(project, Utils.getOptionally(ghProjects, project));
					final int lastRow = sheet.getRowCount() - 1;
					for (curRow = startRow + 2; curRow <= lastRow; ++curRow) {
						sheet.getRowByIndex(curRow).setHeight(10, false);
					}
					curRow = lastRow + 1;
					curCol = 0;
					/**
					 * This is necessary to prevent the next row entry to be repeated in the empty
					 * row. Probably a bug in ODFToolkit or LibreOffice.
					 */
					writeInRow("");
					curCol = 0;
					++curRow;
				}
				/**
				 * Setting the col width while writing the projects produces strange effects. No
				 * idea why.
				 */
				sheet.getColumnByIndex(2).setWidth(10);
			}
			doc.save(out);
		} catch (Exception e) {
			throw new SpreadsheetException(e);
		}
	}

	/**
	 * Copied from {@link Table}, removed absolute ($) part.
	 *
	 * @param colIndex
	 * @param rowIndex
	 * @return
	 */
	private String getCellAddress(int colIndex, int rowIndex) {
		int remainder = 0;
		int colIndex2 = colIndex;
		int multiple = colIndex2;
		String cellRange = "";
		while (multiple != 0) {
			multiple = colIndex2 / 26;
			remainder = colIndex2 % 26;
			char c;
			if (multiple == 0) {
				c = (char) ('A' + remainder);
			} else {
				c = (char) ('A' + multiple - 1);
			}
			cellRange = cellRange + String.valueOf(c);
			colIndex2 = remainder;
		}
		cellRange = cellRange + (rowIndex + 1);
		return cellRange;

	}

	private void writeColumnHeaders() {
		final String diffColLabel;
		if (difficultySummed) {
			diffColLabel = "Σ Difficulty";
		} else {
			diffColLabel = "Difficulty";
		}
		writeInRow("Issue", "Description", diffColLabel, "Ass @ done", "Done", "Date grade", "Comments", "Diff acc",
				"Raw grade", "Final grade");
		curCol -= 10;
	}

	private void writeFct(Functionality fct, boolean firstFct, GradedProject project) {
		final String fctName = fct.getName();
		final ImmutableSortedSet<IssueWithHistory> issues = project.getIssuesCorrespondingTo(fct);
		if (issues.isEmpty()) {
			final Cell cellFctName = sheet.getCellByPosition(curCol, curRow);
			cellFctName.setStringValue(fctName);
		}
		++curCol;
		final Cell cellDescr = sheet.getCellByPosition(curCol, curRow);
		cellDescr.setStringValue(fct.getDescription());
		cellDescr.setTextWrapped(true);
		cellDescr.setVerticalAlignment(VerticalAlignmentType.TOP);
		++curCol;
		final Cell cellDiff = sheet.getCellByPosition(curCol, curRow);
		if (firstFct || !difficultySummed) {
			cellDiff.setDoubleValue(fct.getDifficulty().doubleValue());
		} else {
			final String adr = getCellAddress(curCol, curRow - 1);
			cellDiff.setFormula("=" + adr + "+" + numberFormatter.format(fct.getDifficulty()));
		}

		curCol -= 2;
		if (!issues.isEmpty()) {
			checkArgument(issues.iterator().next().getOriginalName().equals(fctName));
		}
		if (!issues.isEmpty()) {
			/**
			 * We need to cancel the first row increment: we start writing at the very
			 * position we are at.
			 */
			--curRow;
		}
		for (IssueWithHistory issue : issues) {
			++curRow;
			final URL issueUrl = issue.getBare().getHtmlURL();
			final Cell issueCell = sheet.getCellByPosition(curCol, curRow);
			LOGGER.debug("Writing {} at row {}.", issue.getBare().getHtmlURL(), curRow);
			issueCell.addParagraph("").appendHyperlink(issue.getOriginalName(), Utils.toURI(issueUrl));

			final Set<User> assignees = issue.getFirstSnapshotDone().map((s) -> s.getAssignees())
					.orElse(ImmutableSet.of());

			if (!assignees.isEmpty()) {
				curCol += 3;

				final Cell cellAss = sheet.getCellByPosition(curCol, curRow);
				final Paragraph p = cellAss.addParagraph("");
				final Iterator<User> assigneesIt = assignees.iterator();
				{
					final User assignee = assigneesIt.next();
					p.appendHyperlink(assignee.getLogin(), Utils.toURI(assignee.getHtmlURL()));
				}
				while (assigneesIt.hasNext()) {
					final User assignee = assigneesIt.next();
					p.appendTextContent(", ", false);
					p.appendHyperlink(assignee.getLogin(), Utils.toURI(assignee.getHtmlURL()));
				}

				curCol -= 3;
			}
			final Optional<Instant> doneTime = issue.getFirstSnapshotDone().map((s) -> s.getBirthTime());
			if (doneTime.isPresent()) {
				curCol += 4;
				final Cell cellDone = sheet.getCellByPosition(curCol, curRow);
				final ZonedDateTime zonedDoneTime = ZonedDateTime.ofInstant(doneTime.get(), ZoneOffset.UTC);
				cellDone.setDateTimeValue(GregorianCalendar.from(zonedDoneTime));
				cellDone.setFormatString("d MMM yy");
				curCol -= 4;
			}
		}
	}

	private void writeInRow(String... strings) {
		for (String string : strings) {
			final Cell cell = sheet.getCellByPosition(curCol, curRow);
			cell.setStringValue(string);
			++curCol;
		}
	}

	private void writeProject(Project project, Optional<RepositoryWithIssuesWithHistory> ghProject) {
		final GradedProject gradedProject = ghProject.isPresent() ? GradedProject.from(project, ghProject.get())
				: GradedProject.from(project);
		writeProjectTitle(gradedProject);

		++curRow;
		writeColumnHeaders();

		++curRow;
		final List<Functionality> functionalities = project.getFunctionalities();
		final Iterator<Functionality> iterator = functionalities.iterator();
		if (iterator.hasNext()) {
			final Functionality fct = iterator.next();
			writeFct(fct, true, gradedProject);
			++curRow;
		}
		while (iterator.hasNext()) {
			final Functionality fct = iterator.next();
			writeFct(fct, false, gradedProject);
			++curRow;
		}
	}

	private void writeProjectTitle(GradedProject project) {
		{
			final Cell cellTitle = sheet.getCellByPosition(curCol, curRow);
			final Paragraph paragraph = cellTitle.addParagraph("");
			paragraph.appendHyperlink(project.getName(), Utils.toURI(project.getProject().getURL()));
		}
		final Optional<Repository> bare = project.getBareRepository();
		if (bare.isPresent()) {
			final Cell cellRepo = sheet.getCellByPosition(curCol + 1, curRow);
			final Paragraph paragraph = cellRepo.addParagraph("");
			paragraph.appendHyperlink("repo", Utils.toURI(bare.get().getURL()));
		}
	}
}
