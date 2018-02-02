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

import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.model.ProjectOnGitHub;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestFinder {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestFinder.class);

	@Test
	public void testFindMyRepo() throws IOException {
		final Project myProject = new Project("XMCDA-2.2.1-JAXB");
		final RepositoryFinder finder = new RepositoryFinder();
		finder.setGitHub(new RtGithub(Utils.getToken()));
		finder.setFloorSearchDate(LocalDate.of(2015, Month.DECEMBER, 1));
		final List<ProjectOnGitHub> found = finder.find(myProject);
		for (ProjectOnGitHub projectOnGitHub : found) {
			projectOnGitHub.init();
		}
		assertFalse(found.isEmpty());
		final LocalDateTime realCreation = LocalDateTime.of(2016, Month.JULY, 29, 17, 34, 19);
		LOGGER.debug("Created at: {}." + found.get(0).getCreatedAt());
		assertTrue(found.stream().anyMatch((p) -> p.getCreatedAt().equals(realCreation)));
	}

	/**
	 * Fails because not-authenticated, apparently limited to 300 search results
	 * (according to my tests).
	 *
	 * @throws IOException
	 */
	@Test(expected = AssertionError.class)
	public void testFindTooMany() throws IOException {
		final Project myProject = new Project("Biblio");
		final RepositoryFinder finder = new RepositoryFinder();
		finder.setGitHub(new RtGithub());
		finder.setFloorSearchDate(LocalDate.of(2017, Month.SEPTEMBER, 1));
		finder.find(myProject);
	}

	@Test
	public void testHasPom() throws IOException {
		final Project myProject = new Project("XMCDA-2.2.1-JAXB");
		final RepositoryFinder finder = new RepositoryFinder();
		finder.setGitHub(new RtGithub(Utils.getToken()));
		finder.setFloorSearchDate(LocalDate.of(2015, Month.DECEMBER, 1));
		final List<ProjectOnGitHub> found = finder.find(myProject);
		LOGGER.info("Found: {}.", found);
		final List<ProjectOnGitHub> pom = finder.withPom();
		LOGGER.info("With POM: {}.", pom);
		assertFalse(pom.isEmpty());
	}

	@Test
	public void testNoFindTooLate() throws IOException {
		final Project myProject = new Project("java-course");
		final RepositoryFinder finder = new RepositoryFinder();
		finder.setGitHub(new RtGithub(Utils.getToken()));
		finder.setFloorSearchDate(LocalDate.of(2049, Month.OCTOBER, 4));
		final List<ProjectOnGitHub> found = finder.find(myProject);
		assertTrue(found.isEmpty());
	}

}
