package io.github.oliviercailloux.grade;

import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p1;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p11;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p111;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p1111;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p12;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p1Double;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p1Quadruple;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p1Triple;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p2;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c1;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c11;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c111;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

public class MarksTreeTests {
  @Test
  void testMarksTree1_11_111() throws Exception {
    final MarksTree tree = MarksTreeTestsHelper.get1_11_111();
    assertEquals(ImmutableSet.of(c1), tree.getCriteria());
    assertEquals(Mark.one(), tree.getMark(p111));
    assertThrows(Exception.class, () -> tree.getMark(p1));
    assertThrows(Exception.class, () -> tree.getMark(p11));
    assertThrows(Exception.class, () -> tree.getMark(p1Double));
    assertThrows(Exception.class, () -> tree.getMark(p1Triple));
    assertThrows(Exception.class, () -> tree.getMark(p1Quadruple));
    assertEquals(ImmutableSet.of(p111), tree.getPathsToMarks());
    assertEquals(MarksTreeTestsHelper.get11_111(), tree.getTree(c1));
    assertEquals(MarksTreeTestsHelper.get11_111(), tree.getTree(p1));
    assertEquals(MarksTree.composite(ImmutableMap.of(c111, Mark.one())),
        tree.getTree(c1).getTree(c11));
    assertEquals(MarksTree.composite(ImmutableMap.of(c111, Mark.one())), tree.getTree(p11));
    assertTrue(tree.hasPath(p1));
    assertTrue(tree.hasPath(p11));
    assertTrue(tree.hasPath(p111));
    assertFalse(tree.hasPath(p1111));
    assertFalse(tree.hasPath(p2));
    assertFalse(tree.hasPath(p12));
    assertTrue(tree.isComposite());
    assertFalse(tree.isMark());
  }

  @Test
  void testMarksTree1_1_1() throws Exception {
    final MarksTree tree = MarksTreeTestsHelper.get1_1_1();
    assertEquals(ImmutableSet.of(c1), tree.getCriteria());
    assertEquals(Mark.one(), tree.getMark(p1Triple));
    assertThrows(Exception.class, () -> tree.getMark(p1));
    assertThrows(Exception.class, () -> tree.getMark(p11));
    assertThrows(Exception.class, () -> tree.getMark(p1Double));
    assertThrows(Exception.class, () -> tree.getMark(p1Quadruple));
    assertEquals(ImmutableSet.of(p1Triple), tree.getPathsToMarks());
    assertEquals(MarksTreeTestsHelper.get1_1(), tree.getTree(c1));
    assertEquals(MarksTreeTestsHelper.get1_1(), tree.getTree(p1));
    assertEquals(MarksTree.composite(ImmutableMap.of(c1, Mark.one())),
        tree.getTree(c1).getTree(c1));
    assertEquals(MarksTree.composite(ImmutableMap.of(c1, Mark.one())), tree.getTree(p1Double));
    assertTrue(tree.hasPath(p1));
    assertTrue(tree.hasPath(p1Double));
    assertTrue(tree.hasPath(p1Triple));
    assertFalse(tree.hasPath(p11));
    assertFalse(tree.hasPath(p111));
    assertFalse(tree.hasPath(p1Quadruple));
    assertFalse(tree.hasPath(p2));
    assertFalse(tree.hasPath(p12));
    assertTrue(tree.isComposite());
    assertFalse(tree.isMark());
  }
}
