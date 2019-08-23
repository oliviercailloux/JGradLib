package io.github.oliviercailloux.java_grade;

import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.context.FilesSource;

public class JavaMarks {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JavaMarks.class);

	public static IGrade travisBadgeGrade(FilesSource source, String repositoryName) {
		/**
		 * Note that we want the readme to be at the root of the repository, to check
		 * for the badge.
		 */
		final FilesSource readmes = source.filterOnPath((p) -> p.toString().equalsIgnoreCase("README.adoc"));
		final ImmutableSet<FileContent> readmeFcs = readmes.asFileContents();

		switch (readmeFcs.size()) {
		case 0:
			return Mark.zero("No README.adoc file found.");
		case 1:
			final boolean badgeUrl = readmes.existsAndAllMatch(Predicates.contains(Pattern.compile(
					"image:https://(?:api\\.)?travis-ci\\.com/oliviercailloux-org/" + repositoryName + "\\.svg")));
			final boolean rightCase = readmes.asFileContents().stream().collect(MoreCollectors.onlyElement()).getPath()
					.equals(Paths.get("README.adoc"));
			final Mark badgeMark = Mark.ifPasses(badgeUrl);
			final Mark caseMark = Mark.ifPasses(rightCase);
			return WeightingGrade
					.from(ImmutableSet.of(CriterionGradeWeight.from(Criterion.given("Badge url"), badgeMark, 2d),
							CriterionGradeWeight.from(Criterion.given("Case README"), caseMark, -1d)));
		default:
			return Mark.zero("More than one README.adoc file found (with various cases).");
		}
	}
}
