package io.github.oliviercailloux.javagrade.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedGrade;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.javagrade.utils.Summarizer;
import org.junit.jupiter.api.Test;

public class SummarizeTests {
  @Test
  void testFilterKeepsOrderOfKeys() throws Exception {
    final IGrade grade = WeightingGrade
        .from(ImmutableMap.of(CriteriaPath.from("c1"), WeightedGrade.given(Mark.one(), 1d),
            CriteriaPath.from("c2/Spurious/sub"), WeightedGrade.given(Mark.one(), 1d)));
    final IGrade filtered = Summarizer.create().filter(grade);
    final ImmutableList<Criterion> filteredKeys =
        ImmutableList.copyOf(filtered.getSubGrades().keySet());
    assertEquals(ImmutableList.of(Criterion.given("c1"), Criterion.given("c2")), filteredKeys);
  }
}
