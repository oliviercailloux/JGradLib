package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.grade.IGrade.GradePath;

public class GradeStructure {

	public static GradeStructure given(Graph<GradePath> graph) {
		return new GradeStructure(graph);
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

	/**
	 * @return all paths in this graph that have the given path as prefix
	 */
	public ImmutableSet<GradePath> getSuccessorPaths(GradePath path) {
		return ImmutableSet.copyOf(graph.successors(path));
	}

	/**
	 * @return all criteria such that path + c in sucessorpath(path)
	 */
	public ImmutableSet<Criterion> getSuccessorCriteria(GradePath path) {
		return graph.successors(path).stream().map(p -> p.getTail()).collect(ImmutableSet.toImmutableSet());
	}

	public ImmutableGraph<GradePath> asGraph() {
		return graph;
	}

}
