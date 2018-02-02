package io.github.oliviercailloux.st_projects.services.git_hub;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.ZoneOffset;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.jcabi.github.Github;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;
import com.jcabi.github.Search;

import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.model.ProjectOnGitHub;

public class RepositoryFinder {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryFinder.class);

	private LocalDate floorSearchDate;

	private List<ProjectOnGitHub> ghProjects;

	/**
	 * Not <code>null</code>.
	 */
	private Github gitHub;

	public RepositoryFinder() {
		floorSearchDate = getFloorSept1();
		gitHub = new RtGithub();
		ghProjects = Lists.newLinkedList();
	}

	public List<ProjectOnGitHub> find(Project project) throws IOException {
		final String projectName = project.getGitHubName();
		final String searchKeywords = projectName + " in:name created:>" + floorSearchDate.toString();

		ghProjects = Lists.newLinkedList();

		LOGGER.debug("Searching for {}.", searchKeywords);
		final Iterable<Repo> repos = gitHub.search().repos(searchKeywords, "", Search.Order.ASC);
		int i = 0;
		for (Repo repo : repos) {
			++i;
			LOGGER.debug("Found repo: {} (i = {}).", repo, i);
			if (!repo.coordinates().repo().equals(project.getGitHubName())) {
				if (repo.coordinates().repo().equalsIgnoreCase(project.getGitHubName())) {
					LOGGER.error("Equals only when ignoring case: {}.", project);
				}
				continue;
			}
			final ProjectOnGitHub ghp = new ProjectOnGitHub(project, repo);
			ghProjects.add(ghp);
		}

		return ghProjects;
	}

	public LocalDate getFloorSearchDate() {
		return floorSearchDate;
	}

	public Github getGitHub() {
		return gitHub;
	}

	public boolean hasPom(Repo repo) throws IOException {
		return repo.contents().exists("pom.xml", "master");
	}

	public void setFloorSearchDate(LocalDate floorSearchDate) {
		this.floorSearchDate = requireNonNull(floorSearchDate);
	}

	public void setGitHub(Github gitHub) {
		this.gitHub = requireNonNull(gitHub);
	}

	public List<ProjectOnGitHub> withPom() throws IOException {
		final LinkedList<ProjectOnGitHub> withPom = Lists.newLinkedList();
		for (ProjectOnGitHub p : ghProjects) {
			if (hasPom(p.getRepo())) {
				withPom.add(p);
			}
		}
		return withPom;
	}

	/**
	 * @return the latest date that is the first of september and before now. That’s
	 *         either this year, first of september, or last year, first of
	 *         september in case the first one is ahead of us.
	 */
	private LocalDate getFloorSept1() {
		final LocalDate now = LocalDate.now(ZoneOffset.UTC);
		final int curYear = now.getYear();
		final MonthDay sept1 = MonthDay.of(Month.SEPTEMBER, 1);
		final LocalDate latestSept1 = sept1.atYear(curYear);
		final LocalDate previousSept1 = sept1.atYear(curYear - 1);
		final LocalDate floorComp;
		if (latestSept1.isBefore(now)) {
			floorComp = latestSept1;
		} else {
			assert previousSept1.isBefore(now);
			floorComp = previousSept1;
		}
		return floorComp;
	}
}
