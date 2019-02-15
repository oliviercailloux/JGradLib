package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.st_projects.model.GitContext;
import io.github.oliviercailloux.st_projects.model.GitFullContext;
import io.github.oliviercailloux.st_projects.model.MultiContent;

public class GradersFactory {
	public static GradersFactory given(GradingDependencyGrapher g) {
		return new GradersFactory(g);
	}

	private GradingDependencyGrapher g;

	public GradersFactory(GradingDependencyGrapher g) {
		this.g = requireNonNull(g);
	}

	public Supplier<MultiContent> newMC(Supplier<? extends GitContext> context, Predicate<Path> pathPredicate) {
		final Supplier<MultiContent> rawSupplier = GitToMultipleSourcer.satisfyingPath(context, pathPredicate);
		final Supplier<MultiContent> wrappedSupplier = wrapAndRegister(rawSupplier);
		g.putTaskWithDependencies(g.getM().get(wrappedSupplier), g.getM().get(context));
		return wrappedSupplier;
	}

	public Supplier<String> newMultiToSingleSupplier(Supplier<MultiContent> baseSupplier) {
		final Supplier<String> rawSupplier = new MultiToSingleSupplier(baseSupplier);
		final Supplier<String> wrappedSupplier = wrapAndRegister(rawSupplier);
		g.putTaskWithDependencies(g.getM().get(wrappedSupplier), g.getM().get(baseSupplier));
		return wrappedSupplier;
	}

	public Supplier<GitFullContext> newGitFullContexter(Supplier<RepositoryCoordinates> coordinatesSupplier,
			Path projectsBaseDir, Instant ignoreAfter) {
		final Supplier<GitFullContext> rawSupplier = ContextInitializer.withPathAndIgnore(coordinatesSupplier,
				projectsBaseDir, ignoreAfter);
		final Supplier<GitFullContext> wrappedSupplier = wrapAndRegister(rawSupplier);
		g.getG().putEdge(coordinatesSupplier, g.getM().get(wrappedSupplier));
		return wrappedSupplier;
	}

	private <T> Supplier<T> wrapAndRegister(Supplier<T> rawSupplier) {
		final GCCaching<T> gc = new GCCaching<>(rawSupplier);
		final Supplier<T> wrappedSupplier = () -> {
			final T cached = gc.getCached();
			assert cached != null;
			return cached;
		};
		g.getM().put(wrappedSupplier, gc);
		return wrappedSupplier;
	}
}
