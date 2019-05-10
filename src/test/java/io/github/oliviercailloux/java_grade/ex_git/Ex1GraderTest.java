package io.github.oliviercailloux.java_grade.ex_git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.oliviercailloux.java_grade.ex_git.ExGitGrader;

class Ex1GraderTest {

	@Test
	void testPenalty() {
		final ExGitGrader grader = new ExGitGrader();
		grader.setMaxGrade(20d);
		assertEquals(-8d / 24d / 2d, grader.getPenalty(Duration.ofMinutes(30)));
		assertEquals(-8d, grader.getPenalty(Duration.ofHours(24)));
		assertEquals(-14d, grader.getPenalty(Duration.ofHours(27)));
		assertEquals(-20d, grader.getPenalty(Duration.ofHours(30)));
		assertEquals(-20d, grader.getPenalty(Duration.ofHours(50)));
	}

}
