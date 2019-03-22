package io.github.oliviercailloux.grade.markers;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitContext;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.context.PomContext;
import io.github.oliviercailloux.grade.contexters.MavenManager;
import io.github.oliviercailloux.grade.contexters.PomSupplier;

public class Marks {
	public static Mark packageGroupId(Criterion criterion, FilesSource source, PomSupplier pomSupplier,
			PomContext pomContext) {
		return new PackageGroupIdMarker(criterion, source, pomSupplier, pomContext).mark();
	}

	/**
	 * TODO should use only file sourcer.
	 */
	public static Mark noDerivedFiles(Criterion criterion, GitFullContext context) {
		final Optional<RevCommit> mainCommitOpt = context.getMainCommit();
		if (!mainCommitOpt.isPresent()) {
			return Mark.min(criterion);
		}

		final Client client = context.getClient();
		final RevCommit mainCommit = mainCommitOpt.get();
		try {
			final Optional<AnyObjectId> classpathId = client.getBlobId(mainCommit, Paths.get(".classpath"));
			final Optional<AnyObjectId> settingsId = client.getBlobId(mainCommit, Paths.get(".settings/"));
			final Optional<AnyObjectId> projectId = client.getBlobId(mainCommit, Paths.get(".project"));
			final Optional<AnyObjectId> targetId = client.getBlobId(mainCommit, Paths.get("target/"));
			LOGGER.debug("Found settings? {}.", settingsId);
			final double weightOk = ImmutableList.of(classpathId, settingsId, projectId, targetId).stream()
					.filter((o) -> !o.isPresent()).count() / 4d;
			final double weightKo = 1d - weightOk;
			return Mark.of(criterion, criterion.getMinPoints() * weightKo + criterion.getMaxPoints() * weightOk, "");
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Marks.class);

	public static Mark notEmpty(Criterion criterion, FilesSource multiSupplier) {
		return !multiSupplier.getContents().isEmpty()
				? Mark.of(criterion, criterion.getMaxPoints(), "Found: " + multiSupplier.getContents().keySet() + ".")
				: Mark.min(criterion);
	}

	/**
	 * The project must be checked out at the version to be tested, at the path
	 * indicated by the project directory of the client.
	 */
	public static Mark mavenCompile(Criterion criterion, GitContext context, PomSupplier pomSupplier) {
		final MavenManager mavenManager = new MavenManager();
		final Optional<Path> projectRelativeRootOpt = pomSupplier.getMavenRelativeRoot();
		return Mark.binary(criterion, projectRelativeRootOpt.isPresent() && mavenManager.compile(
				context.getClient().getProjectDirectory().resolve(projectRelativeRootOpt.get().resolve("pom.xml"))));
	}

	public static Mark travisConfMark(Criterion criterion, String travisContent) {
		if (travisContent.isEmpty()) {
			return Mark.min(criterion, "Configuration not found or incorrectly named.");
		}

		final com.google.common.base.Predicate<CharSequence> lang = Predicates
				.contains(Pattern.compile("language: java"));
		final com.google.common.base.Predicate<CharSequence> dist = Predicates
				.contains(Pattern.compile("dist: xenial"));
		final com.google.common.base.Predicate<CharSequence> script = Predicates.contains(Pattern.compile("script: "));
		final boolean hasLang = lang.apply(travisContent);
		final boolean hasDist = dist.apply(travisContent);
		final boolean hasScript = script.apply(travisContent);
		final double points;
		final String comment;
		assert criterion.getMinPoints() == 0d;
		if (!hasLang && !hasScript) {
			points = criterion.getMinPoints();
			comment = "Missing language.";
		} else if (!hasLang && hasScript && !hasDist) {
			points = criterion.getMaxPoints() / 3d;
			comment = "Missing language (script should be defaulted).";
		} else if (!hasLang && hasScript && hasDist) {
			points = criterion.getMinPoints();
			comment = "Missing language (script should be defaulted). Missing dist.";
		} else {
			assert hasLang;
			if (!hasDist && !hasScript) {
				points = criterion.getMaxPoints() * 2d / 3d;
				comment = "Missing dist.";
			} else if (!hasDist && hasScript) {
				points = criterion.getMaxPoints() / 3d;
				comment = "Missing dist. Inappropriate script, why not default?";
			} else if (hasDist && !hasScript) {
				points = criterion.getMaxPoints();
				comment = "";
			} else {
				assert hasDist && hasScript;
				points = criterion.getMaxPoints() / 2d;
				comment = "Inappropriate script, why not default?";
			}
		}
		return Mark.of(criterion, points, comment);
	}

	public static Mark gitRepo(Criterion criterion, GitFullContext context) {
		final Client client = context.getClient();

		final Mark grade;
		if (!client.existsCached()) {
			grade = Mark.min(criterion, "Repository not found");
		} else if (!client.hasContentCached()) {
			grade = Mark.min(criterion, "Repository found but is empty");
		} else if (!context.getMainCommit().isPresent()) {
			grade = Mark.min(criterion, "Repository found with content but no suitable commit found");
		} else {
			grade = Mark.max(criterion);
		}

		return grade;
	}

	public static Mark timeMark(Criterion criterion, GitFullContext contextSupplier, Instant deadline, double maxGrade,
			boolean binary) {
		return new TimeMarker(criterion, contextSupplier, deadline, maxGrade, binary).mark();
	}

}
