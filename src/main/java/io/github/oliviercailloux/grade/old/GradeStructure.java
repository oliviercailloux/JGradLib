package io.github.oliviercailloux.grade.old;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public class GradeStructure {

	public static GradeStructure given(Graph<CriteriaPath> graph) {
		return new GradeStructure(graph);
	}

	/**
	 * @param paths need not contain every intermediate paths; anything is accepted
	 *              (the whole structure is deduced anyway)
	 * @return an empty grade structure iff the given paths set is empty or has only
	 *         one root
	 */
	public static GradeStructure given(Set<CriteriaPath> paths) {
		final Queue<CriteriaPath> toVisit = new ArrayDeque<>();
		toVisit.addAll(paths);

		final Set<CriteriaPath> visited = new LinkedHashSet<>();

		final ImmutableGraph.Builder<CriteriaPath> builder = GraphBuilder.directed().immutable();
		builder.addNode(CriteriaPath.ROOT);

		while (!toVisit.isEmpty()) {
			final CriteriaPath current = toVisit.remove();
			if (visited.contains(current)) {
				continue;
			}
			if (!current.isRoot()) {
				final CriteriaPath parent = current.withoutTail();
				builder.putEdge(parent, current);
				toVisit.add(parent);
			}
			visited.add(current);
		}

		return new GradeStructure(builder.build());
	}

	public static GradeStructure toTree(Set<Criterion> nodes) {
		final ImmutableGraph.Builder<CriteriaPath> builder = GraphBuilder.directed().immutable();
		nodes.forEach(c -> builder.addNode(CriteriaPath.from(ImmutableList.of(c))));
		return new GradeStructure(builder.build());
	}

	public static GradeStructure toTree(Map<Criterion, GradeStructure> subTrees) {
		final ImmutableGraph.Builder<CriteriaPath> builder = GraphBuilder.directed().immutable();
		builder.addNode(CriteriaPath.ROOT);
		for (Criterion criterion : subTrees.keySet()) {
			final GradeStructure subTree = subTrees.get(criterion);
			final CriteriaPath criterionPath = CriteriaPath.ROOT.withPrefix(criterion);
			builder.putEdge(CriteriaPath.ROOT, criterionPath);
			for (EndpointPair<CriteriaPath> endpointPair : subTree.asGraph().edges()) {
				builder.putEdge(endpointPair.source().withPrefix(criterion),
						endpointPair.target().withPrefix(criterion));
			}
		}
		return new GradeStructure(builder.build());
	}

	public static GradeStructure merge(Set<GradeStructure> grades) {
		return given(grades.stream().flatMap(g -> g.getLeaves().stream()).collect(ImmutableSet.toImmutableSet()));
	}

	public static GradeStructure from(Set<String> paths) {
		return GradeStructure.given(paths.stream().map(CriteriaPath::from).collect(ImmutableSet.toImmutableSet()));
	}

	private final ImmutableGraph<CriteriaPath> graph;

	private GradeStructure(Graph<CriteriaPath> graph) {
		this.graph = ImmutableGraph.copyOf(graph);
		checkArgument(graph.nodes().contains(CriteriaPath.ROOT));
		checkArgument(graph.nodes().stream()
				.allMatch(p -> graph.successors(p).stream().allMatch(s -> s.withoutTail().equals(p))));
		checkArgument(graph.nodes().stream().allMatch(
				p -> p.equals(CriteriaPath.ROOT) || graph.predecessors(p).equals(ImmutableSet.of(p.withoutTail()))));
	}

	public ImmutableSet<CriteriaPath> getPaths() {
		return ImmutableSet.copyOf(graph.nodes());
	}

	public ImmutableSet<CriteriaPath> getLeaves() {
		return graph.nodes().stream().filter(p -> graph.successors(p).isEmpty()).collect(ImmutableSet.toImmutableSet());
	}

	/**
	 * @return all paths in this graph that have the given path as prefix
	 */
	public ImmutableSet<CriteriaPath> getSuccessorPaths(CriteriaPath path) {
		return ImmutableSet.copyOf(graph.successors(path));
	}

	/**
	 * @return all paths in this graph that have the given path as prefix
	 */
	public ImmutableSet<CriteriaPath> getSiblingsIfTail(CriteriaPath path) {
		if (path.isRoot()) {
			return ImmutableSet.of(CriteriaPath.ROOT);
		}
		return getSuccessorPaths(path.withoutTail());
	}

	/**
	 * @return all criteria such that path + c in sucessorpath(path)
	 */
	public ImmutableSet<Criterion> getSuccessorCriteria(CriteriaPath path) {
		return graph.successors(path).stream().map(p -> p.getTail()).collect(ImmutableSet.toImmutableSet());
	}

	public GradeStructure getStructure(Criterion child) {
		return getStructure(CriteriaPath.from(ImmutableList.of(child)));
	}

	public GradeStructure getStructure(CriteriaPath path) {
		return given(graph.nodes().stream().filter(p -> p.startsWith(path))
				.map(p -> CriteriaPath.from(p.subList(path.size(), p.size()))).collect(ImmutableSet.toImmutableSet()));
	}

	public ImmutableGraph<CriteriaPath> asGraph() {
		return graph;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GradeStructure)) {
			return false;
		}
		final GradeStructure t2 = (GradeStructure) o2;
		return graph.equals(t2.graph);
	}

	@Override
	public int hashCode() {
		return Objects.hash(graph);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Leaves", getLeaves()).toString();
	}

}
