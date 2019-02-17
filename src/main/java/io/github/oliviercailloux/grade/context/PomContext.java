package io.github.oliviercailloux.grade.context;

import java.util.List;

public interface PomContext {

	List<String> getGroupIdElements();

	String getGroupId();

	boolean isGroupIdValid();

}
