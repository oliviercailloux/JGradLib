package io.github.oliviercailloux.javagrade.utils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.univocity.parsers.common.IterableResult;
import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.format.json.JsonSimpleGrade;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrsImport {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(PrsImport.class);

  private static record CsvEntry (GitHubUsername username, Mark mark) {
    public CsvEntry(Record r) {
      this(GitHubUsername.given(r.getString("GitHub username")),
          Mark.given(r.getDouble("Note") / 20d, r.getString("Commentaire")));
      LOGGER.debug("Parsing {}.", r);
    }
  }

  public static void main(String[] args) throws Exception {
    final String prsString = Files.readString(Path.of("Itération 4 UML.csv"));
    final CsvParserSettings settings = new CsvParserSettings();
    settings.setHeaderExtractionEnabled(true);
    final CsvParser parser = new CsvParser(settings);
    // parser.beginParsing(new StringReader(prsString));
    final IterableResult<Record, ParsingContext> iterable =
        parser.iterateRecords(new StringReader(prsString));
    final ImmutableSet<CsvEntry> entries = StreamSupport.stream(iterable.spliterator(), false)
        .map(CsvEntry::new).collect(ImmutableSet.toImmutableSet());
    final ImmutableMap<GitHubUsername, MarksTree> marksByUsername =
        entries.stream().collect(ImmutableMap.toImmutableMap(CsvEntry::username, CsvEntry::mark));
    final Exam exam = new Exam(GradeAggregator.TRIVIAL, marksByUsername);
    final String json = JsonSimpleGrade.toJson(exam);
    Files.writeString(Path.of("Bonus Itération 4 UML.json"), json);
  }
}
