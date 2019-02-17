package io.github.oliviercailloux.st_projects.services.git_hub;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.asciidoctor.Asciidoctor;
import org.junit.jupiter.api.Test;

import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.students_project_following.Project;
import io.github.oliviercailloux.students_project_following.ProjectsMonitor;

public class TestProjectsMonitor {
	@Test
	public void testMonitor() throws Exception {
		try (ProjectsMonitor monitor = ProjectsMonitor.using(Asciidoctor.Factory.create(),
				GitHubToken.getRealInstance())) {
			monitor.updateProjectsAsync();
			monitor.await();
			assertEquals(7, monitor.getSEProjects().size());
			assertEquals(7, monitor.getEEProjects().size());
			monitor.updateRepositoriesAsync();
			monitor.await();
			final Project project = monitor.getProject("J-Voting").get();
			assertEquals("J-Voting", project.getName());
			assertEquals(URI.create("https://github.com/oliviercailloux/projets/blob/master/SE/J-Voting.adoc"),
					project.getURI());
			assertEquals(1, monitor.getRepositories(project).size());
		}
	}
}
