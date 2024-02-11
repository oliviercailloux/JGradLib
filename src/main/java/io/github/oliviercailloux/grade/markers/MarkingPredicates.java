package io.github.oliviercailloux.grade.markers;

import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarkingPredicates {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(MarkingPredicates.class);

	public static Predicate<CharSequence> containsOnce(Pattern pattern) {
		return (s) -> {
			final Matcher matcher = pattern.matcher(s);
			final boolean found = matcher.find();
			final boolean foundAgain = matcher.find();
			LOGGER.debug("Trying to find in source '{}' the pattern {}: {} and {}.", s, pattern, found,
					foundAgain);
			return found && !foundAgain;
		};
	}

	public static Predicate<Path> pathContainsOnce(Pattern pattern) {
		return p -> containsOnce(pattern).test(JavaMarkHelper.getContentOrEmpty(p));
	}

	public static Predicate<Path> startsWithPathRelativeTo(Optional<Path> relativeTo, Path start) {
		final Predicate<Path> p1 = (p) -> relativeTo.isPresent();
		return p1.and((p) -> p.startsWith(relativeTo.get().resolve(start)));
	}
}
