package io.github.oliviercailloux.st_projects.services.git_hub;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.asciidoctor.Asciidoctor;
import org.junit.jupiter.api.Test;

import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.ProjectsMonitor;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestProjectsMonitor {
	@Test
	public void testMonitor() throws Exception {
		try (ProjectsMonitor monitor = ProjectsMonitor.using(Asciidoctor.Factory.create(), Utils.getToken())) {
			monitor.updateProjectsAsync();
			monitor.await();
			assertEquals(7, monitor.getSEProjects().size());
			assertEquals(7, monitor.getEEProjects().size());
			monitor.updateRepositoriesAsync();
			monitor.await();
			final Project project = monitor.getProject("J-Voting").get();
			assertEquals("J-Voting", project.getName());
			assertEquals(Utils.newURL("https://github.com/oliviercailloux/projets/blob/master/SE/J-Voting.adoc"),
					project.getURL());
			assertEquals(1, monitor.getRepositories(project).size());
		}
	}
}
