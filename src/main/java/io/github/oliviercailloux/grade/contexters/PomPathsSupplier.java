package io.github.oliviercailloux.grade.contexters;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.markers.MarkingPredicates;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.utils.Utils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PomPathsSupplier {

	public static PomPathsSupplier basedOn(Path root) throws IOException {
		final ImmutableSet<Path> possiblePoms = getPossiblePoms(root);

		return new PomPathsSupplier(possiblePoms);
	}

	public static ImmutableSet<Path> getPossiblePoms(Path root) throws IOException {
		/**
		 * Need to limit depth, otherwise will find
		 * target/m2e-wtp/web-resources/META-INF/maven/<groupId>/<artifactId>/pom.xml.
		 */
		try (Stream<Path> stream = Files.find(root, 7,
				(p, a) -> p.getNameCount() != 0 && p.getFileName().toString().equals("pom.xml"))) {
			return stream.collect(ImmutableSet.toImmutableSet());
		}
	}

	private PomPathsSupplier(Set<Path> possiblePoms) {
		possibleMavenRoots = possiblePoms.stream().map(this::getParentPath).collect(ImmutableSet.toImmutableSet());
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PomPathsSupplier.class);
	private ImmutableSet<Path> possibleMavenRoots;

	public ImmutableSet<Path> getPossibleMavenRoots() {
		return possibleMavenRoots;
	}

	/**
	 * Useful if absolutely need to be in the folder of the pom (for example, for
	 * invoking Maven for compilation). Otherwise, consider
	 * {@link #getForcedMavenRelativeRoot()}.
	 */
	public Optional<Path> getMavenRoot() {
		if (possibleMavenRoots.size() != 1) {
			return Optional.empty();
		}
		final Path mavenRelativeRoot = possibleMavenRoots.iterator().next();
		return Optional.of(mavenRelativeRoot);
	}

	private Path getParentPath(Path pomPath) {
		assert pomPath.getNameCount() >= 1;
		final Path parent;
		if (pomPath.getNameCount() == 1) {
			parent = Paths.get("");
		} else {
			parent = pomPath.getParent();
		}
		return parent;
	}

	/**
	 * Returns a best-guess at the path to be used as Maven root, even if multiple
	 * or no POMs have been found.
	 *
	 * @return a path relative to the project’s folder.
	 */
	public Path getForcedMavenRoot() {
		if (possibleMavenRoots.contains(Paths.get(""))) {
			return Paths.get("");
		}
		if (possibleMavenRoots.isEmpty()) {
			return Paths.get("");
		}
		return possibleMavenRoots.iterator().next();
	}

	public Path getForcedPom() {
		return getForcedMavenRoot().resolve("pom.xml");
	}

	public Path getSrcMainJavaFolder() {
		return getForcedMavenRoot().resolve("src/main/java/");
	}

	public Path getSrcTestJavaFolder() {
		return getForcedMavenRoot().resolve("src/test/java/");
	}

	public Path getSrcFolder() {
		return getForcedMavenRoot().resolve("src/");
	}

	/**
	 * @return {@code true} iff only one pom has been found and it is at the
	 *         project root, equivalently, {@code true} iff
	 *         {@link #getMavenRoot()} is the empty path.
	 */
	public boolean isMavenProjectAtRoot() {
		return possibleMavenRoots.equals(ImmutableSet.of(Paths.get("")));
	}

	public boolean hasJunit5() {
		final Predicate<CharSequence> containsOnce = MarkingPredicates
				.containsOnce(Pattern.compile("<dependencies>" + Utils.ANY_REG_EXP + "<dependency>" + Utils.ANY_REG_EXP
						+ "<groupId>org\\.junit\\.jupiter</groupId>" + Utils.ANY_REG_EXP
						+ "<artifactId>junit-jupiter-engine</artifactId>" + Utils.ANY_REG_EXP + "<version>5\\.[234]\\."
						+ Utils.ANY_REG_EXP + "</version>" + Utils.ANY_REG_EXP + "<scope>test</scope>"));
		return containsOnce.test(JavaMarkHelper.getContentOrEmpty(getForcedPom()));
	}

}
