package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.grade.IGrade.GradePath;

public class GradeStructure {

	public static GradeStructure given(Graph<GradePath> graph) {
		return new GradeStructure(graph);
	}

	/**
	 * @param paths need not contain every intermediate paths; anything is accepted
	 *              (the whole structure is deduced anyway)
	 * @return an empty grade structure iff the given paths set is empty or has only
	 *         one root
	 */
	public static GradeStructure given(Set<GradePath> paths) {
		final Queue<GradePath> toVisit = new ArrayDeque<>();
		toVisit.addAll(paths);

		final Set<GradePath> visited = new LinkedHashSet<>();

		final ImmutableGraph.Builder<GradePath> builder = GraphBuilder.directed().immutable();
		builder.addNode(GradePath.ROOT);

		while (!toVisit.isEmpty()) {
			final GradePath current = toVisit.remove();
			if (visited.contains(current)) {
				continue;
			}
			if (!current.isRoot()) {
				final GradePath parent = current.withoutTail();
				builder.putEdge(parent, current);
				toVisit.add(parent);
			}
			visited.add(current);
		}

		return new GradeStructure(builder.build());
	}

	public static GradeStructure toTree(Set<Criterion> nodes) {
		final ImmutableGraph.Builder<GradePath> builder = GraphBuilder.directed().immutable();
		nodes.forEach(c -> builder.addNode(GradePath.from(ImmutableList.of(c))));
		return new GradeStructure(builder.build());
	}

	public static GradeStructure toTree(Map<Criterion, GradeStructure> subTrees) {
		final ImmutableGraph.Builder<GradePath> builder = GraphBuilder.directed().immutable();
		builder.addNode(GradePath.ROOT);
		for (Criterion criterion : subTrees.keySet()) {
			final GradeStructure subTree = subTrees.get(criterion);
			final GradePath criterionPath = GradePath.ROOT.withPrefix(criterion);
			builder.putEdge(GradePath.ROOT, criterionPath);
			for (EndpointPair<GradePath> endpointPair : subTree.asGraph().edges()) {
				builder.putEdge(endpointPair.source().withPrefix(criterion),
						endpointPair.target().withPrefix(criterion));
			}
		}
		return new GradeStructure(builder.build());
	}

	private final ImmutableGraph<GradePath> graph;

	private GradeStructure(Graph<GradePath> graph) {
		this.graph = ImmutableGraph.copyOf(graph);
		checkArgument(graph.nodes().contains(GradePath.ROOT));
		checkArgument(graph.nodes().stream()
				.allMatch(p -> graph.successors(p).stream().allMatch(s -> s.withoutTail().equals(p))));
		checkArgument(graph.nodes().stream().allMatch(
				p -> p.equals(GradePath.ROOT) || graph.predecessors(p).equals(ImmutableSet.of(p.withoutTail()))));
	}

	public ImmutableSet<GradePath> getPaths() {
		return ImmutableSet.copyOf(graph.nodes());
	}

	public ImmutableSet<GradePath> getLeaves() {
		return graph.nodes().stream().filter(p -> graph.successors(p).isEmpty()).collect(ImmutableSet.toImmutableSet());
	}

	/**
	 * @return all paths in this graph that have the given path as prefix
	 */
	public ImmutableSet<GradePath> getSuccessorPaths(GradePath path) {
		return ImmutableSet.copyOf(graph.successors(path));
	}

	/**
	 * @return all paths in this graph that have the given path as prefix
	 */
	public ImmutableSet<GradePath> getSiblings(GradePath path) {
		if (path.isRoot()) {
			return ImmutableSet.of(GradePath.ROOT);
		}
		return getSuccessorPaths(path.withoutTail());
	}

	/**
	 * @return all criteria such that path + c in sucessorpath(path)
	 */
	public ImmutableSet<Criterion> getSuccessorCriteria(GradePath path) {
		return graph.successors(path).stream().map(p -> p.getTail()).collect(ImmutableSet.toImmutableSet());
	}

	public GradeStructure getStructure(Criterion child) {
		return getStructure(GradePath.from(ImmutableList.of(child)));
	}

	public GradeStructure getStructure(GradePath path) {
		return given(graph.nodes().stream().filter(p -> p.startsWith(path))
				.map(p -> GradePath.from(p.subList(path.size(), p.size()))).collect(ImmutableSet.toImmutableSet()));
	}

	public ImmutableGraph<GradePath> asGraph() {
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
		return MoreObjects.toStringHelper(this).add("Paths", getLeaves()).toString();
	}

}
