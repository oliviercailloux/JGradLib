package io.github.oliviercailloux.st_projects.services.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.st_projects.model.ContentSupplier;
import io.github.oliviercailloux.st_projects.model.GitContext;

class GradingTest {

	@Test
	void testJunitDiscovery() throws IOException {
		/**
		 * 1 iff is in src/test/java, 2 iff is named Test* or *Test, 3 iff contains
		 * right content.
		 */
		final FileContent t1 = getFileContent("src/test/java/PloumTestPloum.java", "nothing");
		final FileContent t2 = getFileContent("src/main/java/TestPloum.java", "nothing");
		final FileContent t3 = getFileContent("src/main/java/Ploum3.java", "org.junit.jupiter.api.Assertions");
		final FileContent u3 = getFileContent("src/main/java/Ploum4.java", "hah\n \t @Test\nYeah");
		final FileContent v3 = getFileContent("other/Ploum.java",
				"hah\nimport static org.junit.jupiter.api.Assertions\n");
		final FileContent t12 = getFileContent("src/test/java/truc/PloumTest.java", "nothing");
		final FileContent t13 = getFileContent("src/test/java/truc/Ploum.java", "hah\n \t @Test\nYeah");
		final FileContent t23 = getFileContent("src/truc/TestPloum.java", "hah\n@Test\nYeah@Test");

		final ImmutableSet<FileContent> files = ImmutableSet.of(t1, t2, t3, u3, v3, t12, t13, t23);
		final ImmutableMap<Path, String> filesMap = files.stream()
				.collect(ImmutableMap.toImmutableMap((f) -> f.getPath(), (f) -> f.getContent()));
		final Map<Path, String> expected = new LinkedHashMap<>(filesMap);
		expected.remove(t1.getPath());
		expected.remove(t2.getPath());

		final FileCrawler fileCrawler = new FileCrawler(Mockito.mock(Client.class)) {
			@Override
			public String getFileContent(Path path) throws IOException {
				return filesMap.get(path);
			}

			@Override
			public ImmutableSet<Path> getRecursively(Path relativeStart) throws IOException {
				return filesMap.keySet();
			}
		};

		final GitToMultipleSourcer testSourcer = GitToTestSourcer.testSourcer(Mockito.mock(GitContext.class))
				.getDelegate();
		testSourcer.initSources(fileCrawler);
		final ImmutableMap<Path, String> contents = testSourcer.getContents();
		LOGGER.info("Expected: {}.", expected);
		LOGGER.info("Got: {}.", contents);
		assertEquals(expected, contents);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GradingTest.class);

	private FileContent getFileContent(String path, String content) {
		return new FileContent() {
			@Override
			public Path getPath() {
				return Paths.get(path);
			}

			@Override
			public String getContent() throws GradingException {
				return content;
			}
		};
	}

	@Test
	void testGroupIdDiscovery() throws Exception {
		{
			final PomContexter c = getPomContexter("<project blah>HEY<groupId>one.two</groupId>HUH");
			assertEquals("one.two", c.getGroupId());
			assertEquals(ImmutableList.of("one", "two"), c.getGroupIdElements());
		}
		{
			final PomContexter c = getPomContexter("<project>HEY\n<groupId>one.two</groupId>");
			assertEquals("one.two", c.getGroupId());
			assertEquals(ImmutableList.of("one", "two"), c.getGroupIdElements());
		}
		{
			final PomContexter c = getPomContexter(
					"HEY<project>\n\t <groupId>one.two</groupId>\n<dependencies><groupId>ploum.again</groupId>");
			assertEquals("one.two", c.getGroupId());
			assertEquals(ImmutableList.of("one", "two"), c.getGroupIdElements());
		}
	}

	private ContentSupplier getContentSupplier(String content) {
		return new ContentSupplier() {
			@Override
			public String getContent() {
				return content;
			}
		};
	}

	private PomContexter getPomContexter(String pom) {
		final PomContexter supp = new PomContexter(new ContentSupplier() {
			@Override
			public String getContent() {
				return pom;
			}
		});
		supp.init();
		return supp;
	}

}
