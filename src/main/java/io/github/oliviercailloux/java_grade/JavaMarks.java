package io.github.oliviercailloux.java_grade;

import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffplug.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.context.FilesSource;

public class JavaMarks {
	public static Grade travisBadgeMark(Criterion criterion, FilesSource source, String repositoryName) {
		/**
		 * Note that we want the readme to be at the root of the repository, to check
		 * for the badge.
		 */
		final FilesSource readmes = source.filterOnPath((p) -> p.toString().equalsIgnoreCase("README.adoc"));
		final ImmutableSet<FileContent> readmeFcs = readmes.asFileContents();

		switch (readmeFcs.size()) {
		case 0:
			return Grade.min(criterion, "No README.adoc file found.");
		case 1:
			final boolean badgeUrl = readmes.existsAndAllMatch(Predicates.contains(Pattern.compile(
					"image:https://(?:api\\.)?travis-ci\\.com/oliviercailloux-org/" + repositoryName + "\\.svg")));
			final boolean rightCase = readmes.asFileContents().stream().collect(MoreCollectors.onlyElement()).getPath()
					.equals(Paths.get("README.adoc"));
			/**
			 * We need some subtlety because we want to be proportional to: badgeUrl and
			 * (badgeUrl && rightCase); in other words, we do not award points just for
			 * rightCase without badgeUrl.
			 *
			 * TODO think about the fact that ideally we might want here to indicate that
			 * the test of case fails even when the badge fails as well and even though
			 * conditioned on badge url failing, the casing does not bring points.
			 */
			if (badgeUrl) {
				return Grade.proportional(criterion, badgeUrl, rightCase);
			}
			assert !badgeUrl;
			return Grade.min(criterion, "Did not find correct badge url in readme.");
		default:
			return Grade.min(criterion, "More than one README.adoc file found (with various cases).");
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JavaMarks.class);
}
