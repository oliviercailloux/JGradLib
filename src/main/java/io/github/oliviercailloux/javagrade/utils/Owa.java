package io.github.oliviercailloux.javagrade.utils;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradeUtils;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import io.github.oliviercailloux.grade.format.json.JsonCriterion;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.javagrade.graders.WorkersGrader;
import io.github.oliviercailloux.json.JsonbUtils;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Owa {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(Owa.class);

  public static void main(String[] args) throws Exception {
    final String prefix = WorkersGrader.PREFIX;

    @SuppressWarnings("serial")
    final Type type = new LinkedHashMap<String, IGrade>() {}.getClass().getGenericSuperclass();

    final Map<String, IGrade> grades = JsonbUtils.fromJson(
        Files.readString(Path.of("grades " + prefix + ".json")), type, JsonGrade.instance());
    final ImmutableMap<String, IGrade> gradesOwa =
        Maps.toMap(grades.keySet(), s -> toOwa(grades.get(s)));

    Files.writeString(Path.of("grades " + prefix + " owa.json"), JsonbUtils
        .toJsonObject(gradesOwa, JsonCriterion.instance(), JsonGrade.instance()).toString());
  }

  private static IGrade toOwa(IGrade grade) {
    if (grade.getPoints() == 0d) {
      return grade;
    }

    final Criterion gradeCriterion = Criterion.given("grade");
    final Criterion mainCriterion = Criterion.given("main");
    final Criterion codeCriterion = Criterion.given("Code");

    final ArrayList<Criterion> paths = new ArrayList<>();
    if (!grade.getSubGrades().keySet().contains(gradeCriterion)
        && ImmutableSet.copyOf(grade.getWeights().values()).equals(ImmutableSet.of(0d, 1d))) {
      final Criterion right = grade.getWeights().keySet().stream()
          .filter(c -> grade.getWeights().get(c) == 1d).collect(MoreCollectors.onlyElement());
      checkArgument(right.getName().startsWith("Cap at "), right);
      paths.add(right);
    }

    if (grade.getGrade(CriteriaPath.from(paths))
        .orElseThrow(() -> new VerifyException(grade.toString())).getSubGrades().keySet()
        .contains(gradeCriterion)) {
      paths.add(gradeCriterion);
    }
    paths.add(mainCriterion);
    if (grade.getGrade(CriteriaPath.from(paths))
        .orElseThrow(() -> new VerifyException(grade.toString())).getSubGrades().keySet()
        .contains(codeCriterion)) {
      paths.add(codeCriterion);
    }

    final CriteriaPath codePath = CriteriaPath.from(paths);
    final IGrade code =
        grade.getGrade(codePath).orElseThrow(() -> new IllegalArgumentException(grade.toString()));

    final DoubleStream streamOfOnes = DoubleStream.generate(() -> 1d).limit(6);
    final DoubleStream streamIncr = IntStream.range(2, 9).asDoubleStream();
    final ImmutableList<Double> increasingWeights = DoubleStream.concat(streamOfOnes, streamIncr)
        .boxed().collect(ImmutableList.toImmutableList());

    final IGrade newCode = GradeUtils.toOwa(code, increasingWeights);
    return grade.withSubGrade(codePath, newCode);
  }
}
