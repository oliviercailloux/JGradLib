package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import com.github.miachm.sods.Range;
import com.github.miachm.sods.Sheet;
import com.github.miachm.sods.SpreadSheet;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.processor.BeanListProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.format.json.JsonSimpleGrade;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadCsvGrades {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ReadCsvGrades.class);

	private static class MarkRecord {
		@Parsed(field = "Nom")
		private String name;
		@Parsed(field = "Prenom")
		private String first;
		@Parsed(field = "Adresse mail")
		private String email;
		@Parsed(field = "uid")
		private String uid;
		@Parsed(field = "GitHub username")
		private String gu;
		@Parsed(field = "Note")
		private Double mark;
		@Parsed(field = "Commentaire")
		private String comment;

		public MarkRecord(String name, String first, String email, String uid, String gu, double mark,
				String comment) {
			this.name = checkNotNull(name);
			this.first = checkNotNull(first);
			this.email = checkNotNull(email);
			this.uid = checkNotNull(uid);
			this.gu = checkNotNull(gu);
			checkArgument(Double.isFinite(mark));
			this.mark = mark;
			this.comment = checkNotNull(comment);
		}

		@Override
		public boolean equals(Object o2) {
			if (!(o2 instanceof ReadCsvGrades.MarkRecord)) {
				return false;
			}
			final ReadCsvGrades.MarkRecord t2 = (ReadCsvGrades.MarkRecord) o2;
			return uid.equals(t2.uid);
		}

		@Override
		public int hashCode() {
			return Objects.hash(uid);
		}

		@Override
		public String toString() {
			final String cmt =
					(comment != null && comment.length() >= 15) ? comment.substring(0, 10) : comment;
			return MoreObjects.toStringHelper(this).add("Name", name).add("Mark", mark)
					.add("Comment", cmt).toString();
		}
	}

	public static void main(String[] args) throws Exception {
		// final ImmutableSet<MarkRecord> markRecords = readOds("R3");
		final ImmutableSet<MarkRecord> markRecords = readOds("Présentations");

		// markRecords = readCsv();

		final ImmutableMap<GitHubUsername, MarksTree> gradeMap =
				markRecords.stream().collect(ImmutableMap.toImmutableMap(r -> GitHubUsername.given(r.gu),
						r -> Mark.given(r.mark / 20d, r.comment)));
		final String exam = JsonSimpleGrade.toJson(new Exam(GradeAggregator.TRIVIAL, gradeMap));
		// Files.writeString(Path.of("grades Release 3.json"), exam);
		Files.writeString(Path.of("grades Présentation.json"), exam);
	}

	private static ImmutableSet<MarkRecord> readOds(String sheetName) throws IOException {
		final ImmutableSet<MarkRecord> markRecords;
		try (InputStream is = Files.newInputStream(Path.of("../../L3/Projets.ods"))) {
			final SpreadSheet spread = new SpreadSheet(is);
			final Sheet sheet = spread.getSheet(sheetName);
			final Range range = sheet.getDataRange();
			verify(range.getColumn() == 0);
			checkState(range.getLastColumn() == 7);
			verify(range.getRow() == 0);
			LOGGER.info("Last row: {}.", range.getLastRow());
			final ImmutableList.Builder<MarkRecord> builder = ImmutableList.builder();
			for (int row = 1; row <= range.getLastRow(); ++row) {
				final String name = (String) range.getCell(row, 0).getValue();
				final String first = (String) range.getCell(row, 1).getValue();
				final String email = (String) range.getCell(row, 2).getValue();
				final String uid = range.getCell(row, 3).getValue().toString();
				final String gu = (String) range.getCell(row, 4).getValue();
				final double mark = (double) range.getCell(row, 6).getValue();
				final String comment = (String) range.getCell(row, 7).getValue();
				final MarkRecord rec = new MarkRecord(name, first, email, uid, gu, mark, comment);
				LOGGER.info("Adding {}.", rec);
				builder.add(rec);
			}
			final ImmutableList<MarkRecord> recs = builder.build();
			markRecords = ImmutableSet.copyOf(recs);
			checkState(recs.size() == markRecords.size());
		}
		return markRecords;
	}

	private static ImmutableSet<MarkRecord> readCsv() throws IOException {
		final String csv = Files.readString(Path.of("../../L3/Projets.csv"));
		final StringReader csvReader = new StringReader(csv);
		final BeanListProcessor<MarkRecord> processor = new BeanListProcessor<>(MarkRecord.class);
		final CsvParserSettings settings = new CsvParserSettings();
		settings.setMaxCharsPerColumn(10_000);
		settings.setProcessor(processor);
		final CsvParser reader = new CsvParser(settings);
		reader.parse(csvReader);
		final List<MarkRecord> all = processor.getBeans();
		final ImmutableSet<MarkRecord> markRecords =
				all.stream().filter(t -> t.mark != null).collect(ImmutableSet.toImmutableSet());
		settings.setHeaderExtractionEnabled(true);
		LOGGER.info("Mark records: {}.", markRecords);
		return markRecords;
	}
}
