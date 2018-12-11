package io.github.oliviercailloux.st_projects.model;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSortedSet;

import io.github.oliviercailloux.git_hub.graph_ql.Repository;

public class GradedProject {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GradedProject.class);

	public static GradedProject from(Project project) {
		final GradedProject gradedProject = new GradedProject(project, Optional.empty());
		return gradedProject;
	}

	public static GradedProject from(Project project, RepositoryWithIssuesWithHistory repository) {
		final GradedProject gradedProject = new GradedProject(project, Optional.of(repository));
		return gradedProject;
	}

	private Project project;

	private Optional<RepositoryWithIssuesWithHistory> repositoryOpt;

	private GradedProject(Project project, Optional<RepositoryWithIssuesWithHistory> repository) {
		this.project = requireNonNull(project);
		this.repositoryOpt = requireNonNull(repository);
	}

	public Optional<Repository> getBareRepository() {
		return repositoryOpt.map(RepositoryWithIssuesWithHistory::getBare);
	}

	public ImmutableSortedSet<IssueWithHistory> getIssuesCorrespondingTo(Functionality functionality) {
		return repositoryOpt.map((p) -> p.getIssuesCorrespondingTo(functionality.getName()))
				.orElseGet(ImmutableSortedSet::of);
	}

	public String getName() {
		return project.getName();
	}

	public Project getProject() {
		return project;
	}
}
