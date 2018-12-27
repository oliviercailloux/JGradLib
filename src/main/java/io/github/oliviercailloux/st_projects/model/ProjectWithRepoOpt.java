package io.github.oliviercailloux.st_projects.model;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSortedSet;

import io.github.oliviercailloux.git.git_hub.model.graph_ql.IssueWithHistory;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.Repository;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;

public class ProjectWithRepoOpt {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectWithRepoOpt.class);

	public static ProjectWithRepoOpt from(Project project) {
		final ProjectWithRepoOpt gradedProject = new ProjectWithRepoOpt(project, Optional.empty());
		return gradedProject;
	}

	public static ProjectWithRepoOpt from(Project project, RepositoryWithIssuesWithHistory repository) {
		final ProjectWithRepoOpt gradedProject = new ProjectWithRepoOpt(project, Optional.of(repository));
		return gradedProject;
	}

	private Project project;

	private Optional<RepositoryWithIssuesWithHistory> repositoryOpt;

	private ProjectWithRepoOpt(Project project, Optional<RepositoryWithIssuesWithHistory> repository) {
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
