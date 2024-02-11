package io.github.oliviercailloux.grade;

import static io.github.oliviercailloux.grade.CriterionTestsHelper.c1;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c11;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c111;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c1111;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c12;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c2;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c21;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c22;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;

public class CriteriaPathTestsHelper {
  public static CriteriaPath p1 = CriteriaPath.from(ImmutableList.of(c1));
  public static CriteriaPath p11 = CriteriaPath.from(ImmutableList.of(c1, c11));
  public static CriteriaPath p111 = CriteriaPath.from(ImmutableList.of(c1, c11, c111));
  public static CriteriaPath p1111 = CriteriaPath.from(ImmutableList.of(c1, c11, c111, c1111));
  public static CriteriaPath p12 = CriteriaPath.from(ImmutableList.of(c1, c12));
  public static CriteriaPath p2 = CriteriaPath.from(ImmutableList.of(c2));
  public static CriteriaPath p21 = CriteriaPath.from(ImmutableList.of(c2, c21));
  public static CriteriaPath p22 = CriteriaPath.from(ImmutableList.of(c2, c22));
  public static CriteriaPath p1Double = CriteriaPath.from(ImmutableList.of(c1, c1));
  public static CriteriaPath p1Triple = CriteriaPath.from(ImmutableList.of(c1, c1, c1));
  public static CriteriaPath p1Quadruple = CriteriaPath.from(ImmutableList.of(c1, c1, c1, c1));
}
