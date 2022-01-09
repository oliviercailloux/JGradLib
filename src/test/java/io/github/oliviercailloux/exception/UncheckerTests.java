package io.github.oliviercailloux.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.jaris.exceptions.Unchecker;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.junit.jupiter.api.Test;

class UncheckerTests {

	@Test
	void test() {
		final Throwing.Runnable<GitAPIException> throwingGitAPIRunnable = () -> {
			throw new InvalidRemoteException("hey");
		};
		final Unchecker<GitAPIException, IllegalStateException> unchecker = Unchecker
				.wrappingWith(IllegalStateException::new);
		final IllegalStateException thrown = assertThrows(IllegalStateException.class,
				() -> unchecker.call(throwingGitAPIRunnable));
		assertTrue(thrown.getCause() instanceof InvalidRemoteException);
		assertEquals("hey", thrown.getCause().getMessage());
	}

}
