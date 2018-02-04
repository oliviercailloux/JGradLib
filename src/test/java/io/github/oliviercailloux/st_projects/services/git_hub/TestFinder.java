package io.github.oliviercailloux.st_projects.services.git_hub;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.jcabi.github.Coordinates;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.model.ProjectOnGitHub;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestFinder {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestFinder.class);

	@Test
	public void testFindMyRepo() throws IOException {
		final Project myProject = Project.from("XMCDA-2.2.1-JAXB");
		final RepositoryFinder finder = new RepositoryFinder();
		final RtGithub gitHub = new RtGithub(Utils.getToken());
		finder.setGitHub(gitHub);
		finder.setFloorSearchDate(LocalDate.of(2015, Month.DECEMBER, 1));
		final List<Coordinates> found = finder.find(myProject);
		assertFalse(found.isEmpty());
		final GitHubFetcher factory = GitHubFetcher.using(gitHub);
		final ImmutableList<ProjectOnGitHub> projects = Utils.map(found, (c) -> factory.getProject(c));
		final Instant realCreation = LocalDateTime.of(2016, Month.JULY, 29, 17, 34, 19).toInstant(ZoneOffset.UTC);
		final ProjectOnGitHub project = projects.get(0);
		LOGGER.debug("Created at: {}." + project.getCreatedAt());
		assertTrue(projects.stream().anyMatch((p) -> p.getCreatedAt().equals(realCreation)));
	}

	/**
	 * Fails because not-authenticated, apparently limited to 300 search results
	 * (according to my tests).
	 *
	 * @throws IOException
	 */
	@Test(expected = AssertionError.class)
	public void testFindTooMany() throws IOException {
		final Project myProject = Project.from("Biblio");
		final RepositoryFinder finder = new RepositoryFinder();
		finder.setGitHub(new RtGithub());
		finder.setFloorSearchDate(LocalDate.of(2017, Month.SEPTEMBER, 1));
		finder.find(myProject);
	}

	@Test
	public void testHasPom() throws IOException {
		final Project myProject = Project.from("XMCDA-2.2.1-JAXB");
		final RepositoryFinder finder = new RepositoryFinder();
		finder.setGitHub(new RtGithub(Utils.getToken()));
		finder.setFloorSearchDate(LocalDate.of(2015, Month.DECEMBER, 1));
		final List<Coordinates> found = finder.find(myProject);
		LOGGER.info("Found: {}.", found);
		final List<Coordinates> pom = finder.withPom();
		LOGGER.info("With POM: {}.", pom);
		assertFalse(pom.isEmpty());
	}

	@Test
	public void testNoFindTooLate() throws IOException {
		final Project myProject = Project.from("java-course");
		final RepositoryFinder finder = new RepositoryFinder();
		finder.setGitHub(new RtGithub(Utils.getToken()));
		finder.setFloorSearchDate(LocalDate.of(2049, Month.OCTOBER, 4));
		final List<Coordinates> found = finder.find(myProject);
		assertTrue(found.isEmpty());
	}

}
