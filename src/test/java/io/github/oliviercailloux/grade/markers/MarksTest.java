package io.github.oliviercailloux.grade.markers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.github.oliviercailloux.grade.CriterionAndPoints;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.contexters.PomContexter;
import io.github.oliviercailloux.grade.contexters.PomSupplier;

class MarksTest {

	@Test
	void testPrefixExpectTwoButOnlyOne() {
		final IGrade mark = Marks.packageGroupId(
				FilesSource.fromMemory(ImmutableMap.of(Paths.get("src", "main", "java", "aa", "file.txt"), "content")),
				getPomSupplier(), getPomContexter("aa", "b"));
		assertEquals(0d, mark.getPoints());
	}

	private PomContexter getPomContexter(String... strings) {
		final PomContexter mocked = Mockito.mock(PomContexter.class);
		Mockito.when(mocked.getGroupIdElements()).thenReturn(ImmutableList.copyOf(strings));
		return mocked;
	}

	private PomSupplier getPomSupplier() {
		final PomSupplier mocked = Mockito.mock(PomSupplier.class);
		Mockito.when(mocked.getSrcMainJavaFolder()).thenReturn(Paths.get("src/main/java"));
		Mockito.when(mocked.getSrcTestJavaFolder()).thenReturn(Paths.get("src/test/java"));
		return mocked;
	}

	public CriterionAndPoints newCriterion(String req, double min, double max) {
		return new CriterionAndPoints() {
			@Override
			public String getRequirement() {
				return req;
			}

			@Override
			public double getMinPoints() {
				return min;
			}

			@Override
			public double getMaxPoints() {
				return max;
			}
		};
	}

	@Test
	void testNoPrefix() {
		final IGrade mark = Marks.packageGroupId(
				FilesSource.fromMemory(ImmutableMap.of(Paths.get("src", "main", "java", "file.txt"), "content")),
				getPomSupplier(), getPomContexter("aa"));
		assertEquals(0d, mark.getPoints());
	}

	@Test
	void testPrefixOneButWrongRoot() {
		final IGrade mark = Marks.packageGroupId(
				FilesSource.fromMemory(ImmutableMap.of(Paths.get("aa", "file.txt"), "content")), getPomSupplier(),
				getPomContexter("aa"));
		assertEquals(0d, mark.getPoints());
	}

	@Test
	void testPrefixTwo() {
		final Path path = Paths.get("src", "main", "java", "aa", "b", "c", "file.txt");
		final Path relativizedPath = Paths.get("aa", "b", "c", "file.txt");
		final PomContexter pomContexter = getPomContexter("aa", "b");
		final IGrade mark = Marks.packageGroupId(FilesSource.fromMemory(ImmutableMap.of(path, "content")),
				getPomSupplier(), pomContexter);
		assertTrue(PackageGroupIdMarker.hasPrefix(relativizedPath, pomContexter.getGroupIdElements()));
		assertTrue(PackageGroupIdMarker.hasPrefix(relativizedPath, ImmutableList.of("aa")));
		assertFalse(PackageGroupIdMarker.hasPrefix(relativizedPath, ImmutableList.of("aa", "c")));
		assertEquals(1d, mark.getPoints());
	}

	@Test
	void testPrefixOne() {
		final IGrade mark = Marks.packageGroupId(
				FilesSource.fromMemory(ImmutableMap.of(Paths.get("src", "main", "java", "aa", "file.txt"), "content")),
				getPomSupplier(), getPomContexter("aa"));
		assertEquals(1d, mark.getPoints());
	}

}
