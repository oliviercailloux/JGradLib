package io.github.oliviercailloux.st_projects.services.git_hub;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.st_projects.model.GitHubProject;
import io.github.oliviercailloux.st_projects.model.Project;

public class TestFinder {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestFinder.class);

	@Test
	public void testFindMyRepo() throws IOException {
		final Project myProject = new Project("XMCDA-2.2.1-JAXB");
		final RepositoryFinder finder = new RepositoryFinder();
		finder.setFloorSearchDate(LocalDate.of(2015, Month.DECEMBER, 1));
		final List<GitHubProject> found = finder.find(myProject);
		assertFalse(found.isEmpty());
		final LocalDateTime realCreation = LocalDateTime.of(2016, Month.JULY, 29, 17, 34, 19);
		LOGGER.debug("Created at: {}." + found.get(0).getCreatedAt());
		assertTrue(found.stream().anyMatch((p) -> p.getCreatedAt().equals(realCreation)));
	}

	@Test
	public void testNoFindTooLate() throws IOException {
		final Project myProject = new Project("java-course");
		final RepositoryFinder finder = new RepositoryFinder();
		finder.setFloorSearchDate(LocalDate.of(2017, Month.DECEMBER, 1));
		final List<GitHubProject> found = finder.find(myProject);
		assertTrue(found.isEmpty());
	}

}
