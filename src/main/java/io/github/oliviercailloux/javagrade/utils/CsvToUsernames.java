package io.github.oliviercailloux.javagrade.utils;

import com.google.common.collect.ImmutableSet;
import com.univocity.parsers.common.IterableResult;
import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import io.github.oliviercailloux.grade.comm.json.JsonStudents;
import io.github.oliviercailloux.grade.comm.json.JsonStudents.JsonStudentEntry;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsvToUsernames {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(CsvToUsernames.class);

  public static void main(String[] args) throws Exception {
    final String csv = Files.readString(Path.of("classroom_roster.csv"));
    final CsvParserSettings settings = new CsvParserSettings();
    settings.setHeaderExtractionEnabled(true);
    final CsvParser parser = new CsvParser(settings);
    final IterableResult<Record, ParsingContext> iterable =
        parser.iterateRecords(new StringReader(csv));
    final ImmutableSet<JsonStudentEntry> students =
        StreamSupport.stream(iterable.spliterator(), false)
            .filter(r -> r.getString("github_username") != null)
            .map(r -> JsonStudentEntry.given(GitHubUsername.given(r.getString("github_username")),
                EmailAddress.given(r.getString("identifier"))))
            .collect(ImmutableSet.toImmutableSet());
    LOGGER.info("U: {}.", students);
    Files.writeString(Path.of("usernames.json"), JsonStudents.toJson(students));
  }
}
