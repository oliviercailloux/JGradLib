package io.github.oliviercailloux.git.gith_hub;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithFiles;

class ModelTest {

	@Test
	void test() {
		final RepositoryWithFiles repo = RepositoryWithFiles.from(null, Paths.get("ploum"));
	}

}
