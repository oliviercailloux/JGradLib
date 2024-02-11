package io.github.oliviercailloux.grade.markers;

import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.jaris.exceptions.Unchecker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaEeMarkers {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(JavaEeMarkers.class);

  public static IGrade getNoJsp(Path source) {
    final Optional<Path> jsp = Unchecker.IO_UNCHECKER
        .getUsing(
            () -> Files.find(source, 99, (p, b) -> p.getFileName().toString().endsWith(".jsp")))
        .findAny();
    LOGGER.debug("Found as potential JSPs: {}.", jsp);
    return Mark.binary(jsp.isEmpty(), "Not using outdated JSPs", "Using outdated JSPs");
  }

  public static IGrade getNoWebXml(Path source) {
    final Optional<Path> found = Unchecker.IO_UNCHECKER
        .getUsing(
            () -> Files.find(source, 99, (p, b) -> p.getFileName().toString().endsWith("web.xml")))
        .findAny();
    return Mark.binary(found.isEmpty(), "No spurious web.xml", "Spurious web.xml");
  }
}
