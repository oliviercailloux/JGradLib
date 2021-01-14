package io.github.oliviercailloux.grade;

import java.io.IOException;

public interface GitGrader {
	public IGrade grade(GitFileSystemHistory filteredHistory, String username) throws IOException;
}
