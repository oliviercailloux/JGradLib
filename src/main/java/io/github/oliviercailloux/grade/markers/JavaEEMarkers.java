package io.github.oliviercailloux.grade.markers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.grade.context.GitContext;
import io.github.oliviercailloux.grade.context.MultiContent;
import io.github.oliviercailloux.grade.contexters.GitToMultipleSourcer;

public class JavaEEMarkers {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JavaEEMarkers.class);

	public static boolean getNoJsp(GitContext context) {
		final MultiContent sourcer = GitToMultipleSourcer.satisfyingPath(context,
				(p) -> p.getFileName().toString().endsWith(".jsp"));
		LOGGER.debug("Found as potential JSPs: {}.", sourcer.getContents().keySet());
		return sourcer.getContents().isEmpty();
	}

	public static boolean getNoWebXml(GitContext context) {
		final MultiContent sourcer = GitToMultipleSourcer.satisfyingPath(context,
				(p) -> p.getFileName().toString().equals("web.xml"));
		return sourcer.getContents().isEmpty();
	}

}
