package io.github.oliviercailloux.grade.markers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.FilesSource;

public class JavaEEMarkers {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JavaEEMarkers.class);

	public static IGrade getNoJsp(FilesSource source) {
		final FilesSource sourcer = source.filterOnPath((p) -> p.getFileName().toString().endsWith(".jsp"));
		LOGGER.debug("Found as potential JSPs: {}.", sourcer.getContents().keySet());
		return Mark.binary(sourcer.asFileContents().isEmpty(), "Not using outdated JSPs", "Using outdated JSPs");
	}

	public static IGrade getNoWebXml(FilesSource source) {
		final FilesSource sourcer = source.filterOnPath((p) -> p.getFileName().toString().equals("web.xml"));
		return Mark.binary(sourcer.asFileContents().isEmpty(), "No spurious web.xml", "Spurious web.xml");
	}

}
