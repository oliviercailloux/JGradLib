package io.github.oliviercailloux.st_projects.services.grading;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import io.github.oliviercailloux.st_projects.model.GradingContexter;

public class GradingDependencyGrapher {

	private MutableGraph<Object> g;

	private Map<Object, GradingContexter> m;

	public GradingDependencyGrapher() {
		g = GraphBuilder.directed().build();
		m = new LinkedHashMap<>();
	}

	public MutableGraph<Object> getG() {
		return g;
	}

	public Map<Object, GradingContexter> getM() {
		return m;
	}

	public void putTaskWithDependencies(GradingContexter dependentContexter, GradingContexter... contexters) {
		g.addNode(dependentContexter);
		for (GradingContexter contexter : contexters) {
			g.putEdge(contexter, dependentContexter);
		}
	}

	public void putTaskWithDependencies(CriterionMarker criterionGrader, GradingContexter... contexters) {
		// g.nodes().stream().filter(Predicates.instanceOf(CriterionGrader.class)).map((n)->(CriterionGrader)n).map(CriterionGrader::)
		g.addNode(criterionGrader);
		for (GradingContexter contexter : contexters) {
			g.putEdge(contexter, criterionGrader);
		}
	}

}
