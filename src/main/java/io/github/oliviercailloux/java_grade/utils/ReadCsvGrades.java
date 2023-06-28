package io.github.oliviercailloux.java_grade.utils;

import com.google.common.base.MoreObjects;
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
			final String cmt = (comment != null && comment.length() >= 15) ? comment.substring(0, 10) : comment;
			return MoreObjects.toStringHelper(this).add("Name", name).add("Mark", mark).add("Comment", cmt).toString();
		}
	}

	public static void main(String[] args) throws Exception {

//		readCsv();
	}

	private static void readCsv() throws IOException {
		final String csv = Files.readString(Path.of("../../L3/Projets.csv"));
		final StringReader csvReader = new StringReader(csv);
		final BeanListProcessor<MarkRecord> processor = new BeanListProcessor<>(MarkRecord.class);
		final CsvParserSettings settings = new CsvParserSettings();
		settings.setMaxCharsPerColumn(10_000);
		settings.setProcessor(processor);
		final CsvParser reader = new CsvParser(settings);
		reader.parse(csvReader);
		final List<MarkRecord> all = processor.getBeans();
		final ImmutableSet<MarkRecord> markRecords = all.stream().filter(t -> t.mark != null)
				.collect(ImmutableSet.toImmutableSet());
		settings.setHeaderExtractionEnabled(true);
		LOGGER.info("Mark records: {}.", markRecords);

		final ImmutableMap<GitHubUsername, MarksTree> gradeMap = markRecords.stream().collect(
				ImmutableMap.toImmutableMap(r -> GitHubUsername.given(r.gu), r -> Mark.given(r.mark / 20d, r.comment)));
		final String exam = JsonSimpleGrade.toJson(new Exam(GradeAggregator.TRIVIAL, gradeMap));
		Files.writeString(Path.of("grades Release 2.json"), exam);
	}
}
