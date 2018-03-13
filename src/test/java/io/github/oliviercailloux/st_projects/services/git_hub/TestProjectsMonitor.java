package io.github.oliviercailloux.st_projects.services.git_hub;

import static org.junit.Assert.assertEquals;

import org.asciidoctor.Asciidoctor;
import org.junit.Test;

import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestProjectsMonitor {
	@Test
	public void testMonitor() throws Exception {
		final ProjectsMonitor monitor = ProjectsMonitor.using(Asciidoctor.Factory.create(), Utils.getToken());
		monitor.update();
		assertEquals(7, monitor.getSEProjects().size());
		assertEquals(7, monitor.getEEProjects().size());
	}
}
