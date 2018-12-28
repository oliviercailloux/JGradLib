package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.st_projects.model.Criterion;
import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.GitContext;
import io.github.oliviercailloux.st_projects.model.PomContext;
import io.github.oliviercailloux.st_projects.utils.GradingUtils;

public class PackageGroupIdGrader implements CriterionGrader {
	private Criterion criterion;
	private GitContext context;
	private PomContext pomContext;

	public PackageGroupIdGrader(Criterion criterion, GitContext context, PomContext pomContext) {
		this.criterion = requireNonNull(criterion);
		this.context = requireNonNull(context);
		this.pomContext = requireNonNull(pomContext);
	}

	@Override
	public CriterionGrade grade() throws GradingException {
		final List<String> groupIdElements = pomContext.getGroupIdElements();

		if (groupIdElements.isEmpty()) {
			return CriterionGrade.min(criterion, "Unknown group id");
		}

		boolean allMatchMain = allMatch(Paths.get("src/main/java"));
		boolean allMatchTest = allMatch(Paths.get("src/test/java"));
		final ImmutableList<Boolean> successes = ImmutableList.of(allMatchMain, allMatchTest);
		return GradingUtils.getGradeFromSuccesses(criterion, successes);
	}

	private boolean allMatch(Path start) {
		final Client client = context.getClient();
		final List<String> groupIdElements = pomContext.getGroupIdElements();
		assert !groupIdElements.isEmpty();

		boolean allMatch = true;
		Path currentSegment = start;
		for (String element : groupIdElements) {
			LOGGER.debug("Checking for element {}.", element);
			final Path current = client.getProjectDirectory().resolve(currentSegment);
			final Path nextSegment = currentSegment.resolve(element);
			final Path next = client.getProjectDirectory().resolve(nextSegment);
			boolean onlyRightName;
			try {
				onlyRightName = Files.list(current).allMatch(Predicate.isEqual(next));
			} catch (IOException e) {
				throw new GradingException(e);
			}
			if (!onlyRightName) {
				allMatch = false;
				break;
			}
			currentSegment = nextSegment;
		}
		return allMatch;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PackageGroupIdGrader.class);
}
