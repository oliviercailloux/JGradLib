package io.github.oliviercailloux.git.fs;

import io.github.oliviercailloux.gitjfs.GitPath;

sealed interface IGitPathOnFilteredFs extends GitPath permits IGitPathRootOnFilteredFs, GitPathOnFilteredFs {
	GitPath delegate();
}
