package io.github.oliviercailloux.grade.markers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PackageGroupIdMarker {
	public static boolean hasPrefix(Path relativizedPath, List<String> groupIdElements) {
		final ImmutableList<String> pathAsStrings = Streams.stream(relativizedPath).map(Path::toString)
				.collect(ImmutableList.toImmutableList());
		LOGGER.debug("Obtained from {}, path as strings: {}; comparing to group: {}.", relativizedPath, pathAsStrings,
				groupIdElements);

		if (pathAsStrings.size() < groupIdElements.size()) {
			return false;
		}
		return pathAsStrings.subList(0, groupIdElements.size()).equals(groupIdElements);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PackageGroupIdMarker.class);
}
