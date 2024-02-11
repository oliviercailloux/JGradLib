package io.github.oliviercailloux.grade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.old.Mark;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GradeUtilsTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GradeUtilsTests.class);

  @Test
  void testFromTree() throws Exception {
    final Criterion root = Criterion.given("ROOT");
    final Criterion cl = Criterion.given("l");
    final Criterion cll = Criterion.given("ll");
    final Criterion clc = Criterion.given("lc");
    final Criterion clr = Criterion.given("lr");
    final Criterion cr = Criterion.given("r");

    final ImmutableValueGraph.Builder<Criterion, Double> builder = ValueGraphBuilder.directed().immutable();
    builder.putEdgeValue(root, cl, 1d);
    builder.putEdgeValue(root, cr, 2d);
    builder.putEdgeValue(cl, cll, 1d);
    builder.putEdgeValue(cl, clc, 1d);
    builder.putEdgeValue(cl, clr, 2d);
    final ImmutableValueGraph<Criterion, Double> tree = builder.build();

    final Mark r = Mark.given(0.4d, "Far right");
    final Mark ll = Mark.given(0.1d, "Far left");
    final Mark lc = Mark.given(0.2d, "Left center");
    final IGrade lr = GradeTestsHelper.getComplexGradeWithPenalty();
    final ImmutableMap<Criterion, IGrade> leafs = ImmutableMap.of(cll, ll, clc, lc, clr, lr, cr, r);

    final IGrade actual = GradeUtils.toGrade(root, tree, leafs);

    final IGrade expected = JsonGrade
        .asGrade(Files.readString(Path.of(getClass().getResource("Unbalanced grade.json").toURI())));
    assertEquals(expected.getSubGrades().keySet(), actual.getSubGrades().keySet());
    assertEquals(expected.getSubGrades().get(cr), actual.getSubGrades().get(cr));
    assertEquals(expected.getSubGrades().get(cl).getSubGrades().get(cll),
        actual.getSubGrades().get(cl).getSubGrades().get(cll));
    assertEquals(expected.getSubGrades().get(cl).getSubGrades().get(clc),
        actual.getSubGrades().get(cl).getSubGrades().get(clc));
    assertEquals(actual.getSubGrades().get(cl).getSubGrades().get(clr), lr);
    assertEquals(expected.getSubGrades().get(cl).getSubGrades().get(clr).getSubGrades(), lr.getSubGrades());
    assertEquals(expected.getSubGrades().get(cl).getSubGrades().get(clr),
        actual.getSubGrades().get(cl).getSubGrades().get(clr));
    assertEquals(expected.getSubGrades().get(cl).getSubGrades(), actual.getSubGrades().get(cl).getSubGrades());
    assertEquals(expected.getSubGrades().get(cl), actual.getSubGrades().get(cl));
    assertEquals(expected.getSubGrades(), actual.getSubGrades());
    assertEquals(expected, actual);
  }
}
