package io.github.oliviercailloux.st_projects.model;

import java.util.List;

public interface PomContext {

	List<String> getGroupIdElements();

	String getGroupId();

	boolean isGroupIdValid();

}
