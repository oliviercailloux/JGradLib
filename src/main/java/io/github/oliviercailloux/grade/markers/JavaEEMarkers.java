package io.github.oliviercailloux.grade.markers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.grade.context.FilesSource;

public class JavaEEMarkers {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JavaEEMarkers.class);

	public static boolean getNoJsp(FilesSource source) {
		final FilesSource sourcer = source.filterOnPath((p) -> p.getFileName().toString().endsWith(".jsp"));
		LOGGER.debug("Found as potential JSPs: {}.", sourcer.getContents().keySet());
		return sourcer.asFileContents().isEmpty();
	}

	public static boolean getNoWebXml(FilesSource source) {
		final FilesSource sourcer = source.filterOnPath((p) -> p.getFileName().toString().equals("web.xml"));
		return sourcer.asFileContents().isEmpty();
	}

}
