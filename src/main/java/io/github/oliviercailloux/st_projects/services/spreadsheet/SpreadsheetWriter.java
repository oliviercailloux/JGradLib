package io.github.oliviercailloux.st_projects.services.spreadsheet;

import java.io.OutputStream;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.odftoolkit.simple.SpreadsheetDocument;
import org.odftoolkit.simple.style.StyleTypeDefinitions.VerticalAlignmentType;
import org.odftoolkit.simple.table.Cell;
import org.odftoolkit.simple.table.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import io.github.oliviercailloux.st_projects.model.Functionality;
import io.github.oliviercailloux.st_projects.model.GitHubIssue;
import io.github.oliviercailloux.st_projects.model.GitHubProject;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.git_hub.Utils;

public class SpreadsheetWriter {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SpreadsheetWriter.class);

	private int curCol;

	private int curRow;

	private final NumberFormat numberFormatter;

	private OutputStream out;

	private Table sheet;

	public SpreadsheetWriter() {
		sheet = null;
		numberFormatter = NumberFormat.getNumberInstance(Locale.ENGLISH);
		curRow = 0;
		curCol = 0;
		out = null;
	}

	public void setOutputStream(OutputStream out) {
		this.out = out;
	}

	public void writeGitHubProjects(List<GitHubProject> projects) throws SpreadsheetException {
		writeGeneral(Lists.transform(projects, (p) -> new ProjectWithPossibleGitHubData(p)));
	}

	public void writeProjects(List<Project> projects) throws SpreadsheetException {
		writeGeneral(Lists.transform(projects, (p) -> new ProjectWithPossibleGitHubData(p)));
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

	private void writeGeneral(List<ProjectWithPossibleGitHubData> projects) throws SpreadsheetException {
		try (SpreadsheetDocument doc = SpreadsheetDocument.newSpreadsheetDocument()) {
			sheet = doc.getSheetByIndex(0);
			sheet.setTableName("Projects");
			curCol = 0;
			for (ProjectWithPossibleGitHubData project : projects) {
				final Optional<GitHubProject> ghProject = project.getGhProject();
				curRow = 0;
				/** TODO span the title over three columns. */
				final Cell cellTitle = sheet.getCellByPosition(curCol, curRow);
				if (ghProject.isPresent()) {
					cellTitle.addParagraph("").appendHyperlink(project.getProject().getName(),
							Utils.toURI(ghProject.get().getHtmlURL()));
				} else {
					cellTitle.setStringValue(project.getProject().getName());
				}

				++curRow;
				writeInRow(ImmutableList.of("Issue", "Description", "Difficulty"));
				curCol -= 3;
				++curRow;
				final List<Functionality> functionalities = project.getProject().getFunctionalities();
				final Iterator<Functionality> iterator = functionalities.iterator();
				if (iterator.hasNext()) {
					final Functionality fct = iterator.next();
					final String fctName = fct.getName();
					final Optional<GitHubIssue> issueOpt = project.getGhProject().flatMap((p) -> p.getIssue(fctName));
					final Cell cellFctName = sheet.getCellByPosition(curCol, curRow);
					if (issueOpt.isPresent()) {
						final URL issueUrl = issueOpt.get().getUrl();
						cellFctName.addParagraph("").appendHyperlink(fctName, Utils.toURI(issueUrl));
					} else {
						cellFctName.setStringValue(fctName);
					}
					++curCol;
					final Cell cellDescr = sheet.getCellByPosition(curCol, curRow);
					cellDescr.setStringValue(fct.getDescription());
					cellDescr.setTextWrapped(true);
					cellDescr.setVerticalAlignment(VerticalAlignmentType.TOP);
					++curCol;
					final Cell cell = sheet.getCellByPosition(curCol, curRow);
					cell.setDoubleValue(fct.getDifficulty().doubleValue());
					curCol -= 2;
					++curRow;
				}
				while (iterator.hasNext()) {
					final Functionality fct = iterator.next();
					final ImmutableList<String> data = ImmutableList.of(fct.getName(), fct.getDescription());
					writeInRow(data);
					final Cell cellDescr = sheet.getCellByPosition(curCol - 1, curRow);
					cellDescr.setTextWrapped(true);
					cellDescr.setVerticalAlignment(VerticalAlignmentType.TOP);
					final Cell cellDiff = sheet.getCellByPosition(curCol, curRow);
					final String adr = getCellAddress(curCol, curRow - 1);
					cellDiff.setFormula("=" + adr + "+" + numberFormatter.format(fct.getDifficulty()));
					curCol -= 2;
					++curRow;
				}
				curCol += 3;
			}
			/**
			 * Setting the col width in the loop above produces strange effects. No idea
			 * why.
			 */
			curCol = 0;
			for (@SuppressWarnings("unused")
			ProjectWithPossibleGitHubData project : projects) {
				sheet.getColumnByIndex(curCol + 2).setWidth(15);
				LOGGER.info("Set width for {}.", curCol + 2);
				curCol += 3;
			}
			curRow = 2;
			sheet.getRowCount();
			for (curRow = 2; curRow < sheet.getRowCount(); ++curRow) {
				sheet.getRowByIndex(curRow).setHeight(10, false);
			}
			doc.save(out);
		} catch (Exception e) {
			throw new SpreadsheetException(e);
		}
	}

	private void writeInRow(List<String> strings) {
		for (String string : strings) {
			final Cell cell = sheet.getCellByPosition(curCol, curRow);
			cell.setStringValue(string);
			++curCol;
		}
	}
}
