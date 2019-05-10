package io.github.oliviercailloux.st_projects.services.draft;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class SimpleTester {

	@Test
	void testFails() {
		fail("Not yet implemented");
	}

	@Test
	void testSucceeds() {
		/** Just succeed! */
	}

	@Test
	void testThrows() throws IOException {
		throw new IOException();
	}

}
