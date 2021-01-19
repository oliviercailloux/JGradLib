package io.github.oliviercailloux.grade;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 *
 * Grade (interface): {points generally in [0, 1], comment, subGrades:
 * ImmutableMap<CriterionAndPoints, Grade> which may be empty}. A mark is a
 * grade that has no sub-grades. A composite grade is a grade that has at least
 * one sub-grade. Interfaces to distinguish marks from composite grades do not
 * exist: it would raise complexity and not bring much benefit.
 *
 *
 * A grade knows the criteria and sub-grades that it is composed of. But it does
 * not know which fraction it should preferably use for its own display: this is
 * known by the user of the grade at display time. It relates to display and not
 * to grade information per se.
 *
 * @author Olivier Cailloux
 *
 */
public interface IGrade {
	/**
	 * Returns the points. It is not mandatory that the points on a composite grade
	 * be a deterministic function of the points on the sub-grades: manual
	 * correction may intervene in between.
	 *
	 * @return the points.
	 */
	public double getPoints();

	/**
	 * Returns the comment about the points. Comment on a composite grade serves to
	 * explain how a grade has been obtained from its sub-grades (may be empty if
	 * obvious), and is not supposed to be a concatenation of sub-comments: this
	 * comment is not supposed to be redundant with sub-comments.
	 *
	 * @return the comment.
	 */
	public String getComment();

	/**
	 * Returns the sub-grades (with the key set iterating in order of the
	 * sub-grades), empty iff this grade is a mark, non-empty iff this grade is a
	 * composite grade.
	 *
	 * @return the sub grades.
	 */
	public ImmutableMap<Criterion, IGrade> getSubGrades();

	/**
	 * @param depth ≥0; 0 for a mark.
	 */
	public IGrade limitedDepth(int depth);

	/**
	 * If the given criterion exists among the sub grades contained in this grade,
	 * returns a new grade identical to this one except that the corresponding sub
	 * grade is replaced. If the criterion does not exist, then either returns a
	 * grade identical to this one with a new sub grade, or throws an
	 * {@link IllegalArgumentException}.
	 *
	 */
	public IGrade withSubGrade(Criterion criterion, IGrade newSubGrade);

	public default Optional<IGrade> getGrade(List<Criterion> path) {
		if (path.isEmpty()) {
			return Optional.of(this);
		}
		final Criterion criterion = path.get(0);
		if (!getSubGrades().containsKey(criterion)) {
			return Optional.empty();
		}
		return getSubGrades().get(criterion).getGrade(path.subList(1, path.size()));
	}

	public default IGrade withPatch(Patch patch) {
		final ImmutableList<Criterion> path = patch.getPath();
		if (path.isEmpty()) {
			return patch.getGrade();
		}
		final Criterion criterion = path.get(0);
		final IGrade subPatched = getSubGrades().get(criterion)
				.withPatch(Patch.create(path.subList(1, path.size()), patch.getGrade()));
		return withSubGrade(criterion, subPatched);
	}

	public default IGrade withPatches(Set<Patch> patches) {
		IGrade current = this;
		for (Patch patch : patches) {
			current = current.withPatch(patch);
		}
		return current;
	}

	/**
	 * Two {@link IGrade} objects are equal iff they have the same points, comment,
	 * and sub grades (irrespective of the order of the sub grades).
	 */
	@Override
	public boolean equals(Object o2);
}
