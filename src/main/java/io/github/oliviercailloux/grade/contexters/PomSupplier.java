package io.github.oliviercailloux.grade.contexters;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.grade.context.GitContext;
import io.github.oliviercailloux.grade.context.MultiContent;

public class PomSupplier {

	public static PomSupplier basedOn(GitContext context) {
		/**
		 * Need to limit depth, otherwise will find
		 * target/m2e-wtp/web-resources/META-INF/maven/<groupId>/<artifactId>/pom.xml.
		 */
		final MultiContent multiPom = GitToMultipleSourcer.satisfyingPath(context,
				(p) -> p.getNameCount() <= 6 && p.getFileName().toString().equals("pom.xml"));
		return new PomSupplier(multiPom);
	}

	private final String content;
	private MultiContent underlyingMultiSupplier;

	public MultiContent asMultiContent() {
		return underlyingMultiSupplier;
	}

	private PomSupplier(MultiContent supplier) {
		this.underlyingMultiSupplier = requireNonNull(supplier);
		final ImmutableMap<Path, String> contents = underlyingMultiSupplier.getContents();
		this.content = contents.size() == 1 ? contents.values().iterator().next() : "";
		final ImmutableSet<Path> possiblePoms = underlyingMultiSupplier.getContents().keySet();
		possibleMavenRelativeRoots = possiblePoms.stream().map(this::getParentPath)
				.collect(ImmutableSet.toImmutableSet());
	}

	public String getContent() {
		LOGGER.debug("Found poms: {}.", underlyingMultiSupplier.getContents().keySet());
		return content;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PomSupplier.class);
	private ImmutableSet<Path> possibleMavenRelativeRoots;

	/**
	 * Useful if absolutely need to be in the folder of the pom (for example, for
	 * invoking Maven for compilation). Otherwise, consider
	 * {@link #getForcedMavenRelativeRoot()}.
	 */
	public Optional<Path> getMavenRelativeRoot() {
		if (possibleMavenRelativeRoots.size() != 1) {
			return Optional.empty();
		}
		final Path mavenRelativeRoot = possibleMavenRelativeRoots.iterator().next();
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
	public Path getForcedMavenRelativeRoot() {
		if (possibleMavenRelativeRoots.contains(Paths.get(""))) {
			return Paths.get("");
		}
		if (possibleMavenRelativeRoots.isEmpty()) {
			return Paths.get("");
		}
		return possibleMavenRelativeRoots.iterator().next();
	}

	public Path getSrcMainJavaFolder() {
		return getForcedMavenRelativeRoot().resolve("src/main/java/");
	}

	public Path getSrcTestJavaFolder() {
		return getForcedMavenRelativeRoot().resolve("src/test/java/");
	}

	public Path getSrcFolder() {
		return getForcedMavenRelativeRoot().resolve("src/");
	}

	/**
	 * @return <code>true</code> iff only one pom has been found and it is at the
	 *         project root, equivalently, <code>true</code> iff
	 *         {@link #getMavenRelativeRoot()} is the empty path.
	 */
	public boolean isMavenProjectAtRoot() {
		return possibleMavenRelativeRoots.size() == 1 && getForcedMavenRelativeRoot().equals(Paths.get(""));
	}

	public static PomSupplier basedOn(MultiContent supplier) {
		return new PomSupplier(supplier);
	}

}
