package io.github.oliviercailloux.st_projects.services.spreadsheet;

import java.io.OutputStream;
import java.net.URL;
import java.text.NumberFormat;
import java.time.Instant;
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
import com.google.common.collect.UnmodifiableIterator;

import io.github.oliviercailloux.st_projects.model.Functionality;
import io.github.oliviercailloux.st_projects.model.Issue;
import io.github.oliviercailloux.st_projects.model.IssueSnapshot;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.model.ProjectOnGitHub;
import io.github.oliviercailloux.st_projects.model.User;
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

	public void write(List<Project> projects, Map<Project, ProjectOnGitHub> ghProjects) throws SpreadsheetException {
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
					curCol += 4;
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
		writeInRow("Issue", "Description", diffColLabel, "Ass @ 1st close");
		curCol -= 4;
	}

	private void writeFct(Functionality fct, boolean firstFct, Optional<ProjectOnGitHub> ghProject) {
		final String fctName = fct.getName();
		final ImmutableSortedSet<Issue> issues = ghProject.map((p) -> p.getIssuesNamed(fctName))
				.orElseGet(ImmutableSortedSet::of);
		final Cell cellFctName = sheet.getCellByPosition(curCol, curRow);
		if (issues.isEmpty()) {
			cellFctName.setStringValue(fctName);
		} else {
			final UnmodifiableIterator<Issue> issuesIt = issues.iterator();
			final Issue main = issuesIt.next();
			final URL issueUrl = main.getHtmlURL();
			final Paragraph fctPar = cellFctName.addParagraph("");
			fctPar.appendHyperlink(fctName, Utils.toURI(issueUrl));
			if (issuesIt.hasNext()) {
				fctPar.appendTextContent(" (");
				int i = 2;
				while (issuesIt.hasNext()) {
					final Issue dupl = issuesIt.next();
					fctPar.appendHyperlink(String.valueOf(i), Utils.toURI(dupl.getHtmlURL()));
					if (issuesIt.hasNext()) {
						fctPar.appendTextContent(", ");
					}
				}
				fctPar.appendTextContent(")");
			}
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

		++curCol;

		final Cell cellAss = sheet.getCellByPosition(curCol, curRow);

		final Optional<IssueSnapshot> issueDoneOpt = issues.isEmpty() ? Optional.empty()
				: issues.iterator().next().getFirstSnapshotDone();
		final Optional<Set<User>> assigneesOpt = issueDoneOpt.map((s) -> s.getAssignees());
		final Set<User> assignees = assigneesOpt.orElse(ImmutableSet.of());

		if (!assignees.isEmpty()) {
			final Paragraph p = cellAss.addParagraph("");
			final Iterator<User> assigneesIt = assignees.iterator();
			if (assigneesIt.hasNext()) {
				final User assignee = assigneesIt.next();
				p.appendHyperlink(assignee.getLogin(), Utils.toURI(assignee.getHtmlURL()));
			}
			while (assigneesIt.hasNext()) {
				final User assignee = assigneesIt.next();
				p.appendTextContentNotCollapsed(", ");
				p.appendHyperlink(assignee.getLogin(), Utils.toURI(assignee.getHtmlURL()));
			}
		}

		curCol -= 3;
	}

	private void writeInRow(String... strings) {
		for (String string : strings) {
			final Cell cell = sheet.getCellByPosition(curCol, curRow);
			cell.setStringValue(string);
			++curCol;
		}
	}

	private void writeProject(Project project, Optional<ProjectOnGitHub> ghProject) {
		writeProjectTitle(project, ghProject);

		++curRow;
		writeColumnHeaders();

		++curRow;
		final List<Functionality> functionalities = project.getFunctionalities();
		final Iterator<Functionality> iterator = functionalities.iterator();
		if (iterator.hasNext()) {
			final Functionality fct = iterator.next();
			writeFct(fct, true, ghProject);
			++curRow;
		}
		while (iterator.hasNext()) {
			final Functionality fct = iterator.next();
			writeFct(fct, false, ghProject);
			++curRow;
		}
	}

	private void writeProjectTitle(Project project, Optional<ProjectOnGitHub> ghProject) {
		/** TODO span the title over all columns. */
		final Cell cellTitle = sheet.getCellByPosition(curCol, curRow);
		if (ghProject.isPresent()) {
			cellTitle.addParagraph("").appendHyperlink(project.getName(), Utils.toURI(ghProject.get().getHtmlURL()));
		} else {
			cellTitle.setStringValue(project.getName());
		}
	}
}
