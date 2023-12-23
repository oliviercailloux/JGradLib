package io.github.oliviercailloux.git.filter;

import io.github.oliviercailloux.gitjfs.GitPathRoot;

sealed interface IGitPathRootOnFilteredFs extends
		IGitPathOnFilteredFs permits GitPathRootOnFilteredFs, GitPathRootRefOnFilteredFs, GitPathRootShaOnFilteredFs, GitPathRootShaCachedOnFilteredFs {
	@Override
	GitPathRoot delegate();
}
