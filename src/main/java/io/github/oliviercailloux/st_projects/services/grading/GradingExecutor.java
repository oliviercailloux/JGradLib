package io.github.oliviercailloux.st_projects.services.grading;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.graph.Graph;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.Traverser;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.st_projects.model.GradingContexter;
import io.github.oliviercailloux.st_projects.model.Mark;
import io.github.oliviercailloux.st_projects.model.StudentGrade;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class GradingExecutor {
	private final BoxSupplier coordinatesSupplier;

	public Supplier<RepositoryCoordinates> getInitialSupplier() {
		return coordinatesSupplier;
	}

	public GradingExecutor() {
		coordinatesSupplier = new BoxSupplier();
		prerequisites = null;
		sortedTasks = null;
		contexters = null;
		criteriaComparator = null;
	}

	private ImmutableSet<Mark> grade(RepositoryCoordinates coordinates) throws GradingException {
		final ImmutableSet.Builder<Mark> gradesBuilder = ImmutableSet.builder();

		for (Object worker : sortedTasks) {
			if (worker instanceof BoxSupplier) {
				assert worker.equals(coordinatesSupplier);
				LOGGER.debug("Initializing initial supplier.");
				coordinatesSupplier.set(coordinates);
			} else if (worker instanceof GradingContexter) {
				final GradingContexter contexter = (GradingContexter) worker;
				LOGGER.debug("Initializing contexter {}.", contexter);
				contexter.init();
			} else if (worker instanceof CriterionMarker) {
				final CriterionMarker grader = (CriterionMarker) worker;
				LOGGER.debug("Grading from {}.", grader);
				Mark grade = grader.mark();
				gradesBuilder.add(grade);
			} else {
				throw new AssertionError();
			}
		}

		for (GradingContexter contexter : contexters) {
			LOGGER.debug("Clearing contexter {}.", contexter);
			contexter.clear();
		}

		return gradesBuilder.build();
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GradingExecutor.class);
	private ImmutableGraph<Object> prerequisites;
	private Queue<Object> sortedTasks;
	private ImmutableSet<GradingContexter> contexters;
	private Comparator<Mark> criteriaComparator;

	public StudentGrade grade(StudentOnGitHub student, RepositoryCoordinates coordinates) throws GradingException {
		final ImmutableSet<Mark> marks = grade(coordinates);
		if (criteriaComparator == null) {
			return StudentGrade.of(student, marks);
		}
		final ImmutableSortedSet<Mark> sortedGrades = ImmutableSortedSet.copyOf(criteriaComparator, marks);
		return StudentGrade.of(student, sortedGrades);
	}

	public void setGraph(Graph<Object> prerequisites) {
		checkArgument(prerequisites.nodes().contains(coordinatesSupplier));
		checkArgument(prerequisites.predecessors(coordinatesSupplier).isEmpty());
		final Set<Object> nodes = prerequisites.nodes();
		final ImmutableSet<Object> roots = nodes.stream().filter((n) -> prerequisites.inDegree(n) == 0)
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<Object> reachableNodes = ImmutableSet
				.copyOf(Traverser.forGraph(prerequisites).breadthFirst(roots));
		checkArgument(reachableNodes.size() == prerequisites.nodes().size());
		final Predicate<Object> checker = Predicates.instanceOf(BoxSupplier.class)
				.and(Predicates.equalTo(coordinatesSupplier))
				.or(Predicates.instanceOf(GradingContexter.class).or(Predicates.instanceOf(CriterionMarker.class)));
		final ImmutableSet<Object> invalid = reachableNodes.stream().filter(checker.negate())
				.collect(ImmutableSet.toImmutableSet());
		checkArgument(invalid.isEmpty(), invalid);

		this.prerequisites = ImmutableGraph.copyOf(prerequisites);
		sortedTasks = Utils.topologicalSort(prerequisites, roots);
		contexters = sortedTasks.stream().filter(Predicates.instanceOf(GradingContexter.class))
				.map((o) -> (GradingContexter) o).collect(ImmutableSet.toImmutableSet());
	}

	public ImmutableGraph<Object> getPrerequisites() {
		return prerequisites;
	}

	public ImmutableSet<StudentGrade> gradeAll(ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositories) {
		final ImmutableSet.Builder<StudentGrade> gradesBuilder = ImmutableSet.builder();
		for (Entry<StudentOnGitHub, RepositoryCoordinates> entry : repositories.entrySet()) {
			final StudentOnGitHub student = entry.getKey();
			final RepositoryCoordinates repo = entry.getValue();
			final StudentGrade grade = grade(student, repo);
			gradesBuilder.add(grade);
			LOGGER.debug("Student {}, grades {}.", student, grade.getMarks().values());
			LOGGER.info("Evaluation: {}", grade.getAsMyCourseString());
		}
		return gradesBuilder.build();
	}

	public Comparator<Mark> getCriteriaComparator() {
		return criteriaComparator;
	}

	public void setCriteriaComparator(Comparator<Mark> criteriaComparator) {
		this.criteriaComparator = criteriaComparator;
	}
}
