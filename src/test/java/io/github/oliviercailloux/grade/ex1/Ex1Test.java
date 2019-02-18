package io.github.oliviercailloux.grade.ex1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.v3.Event;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.ex1.Ex1Criterion;
import io.github.oliviercailloux.grade.ex1.Ex1Grader;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHub;
import io.github.oliviercailloux.grade.mycourse.StudentOnMyCourse;

public class Ex1Test {
	@Test
	void testGrader() throws Exception {
		final String path = this.getClass().getResource("git").toString();
		/** For now we cheat and use a file-based access to the classpath. */
		LOGGER.info("Using path: {}.", path);
		final RepositoryCoordinates coordinates = Mockito.mock(RepositoryCoordinates.class);
		Mockito.when(coordinates.getOwner()).thenReturn("oliviercailloux");
		Mockito.when(coordinates.getRepositoryName()).thenReturn("sol-ex-1");
		Mockito.when(coordinates.getSshURLString()).thenReturn(path);
		final Ex1Grader grader = new Ex1Grader();
		@SuppressWarnings("resource")
		final GitHubFetcherV3 fetcher = Mockito.mock(GitHubFetcherV3.class);
		grader.setRawGitHubFetcherSupplier(() -> fetcher);
		final Event lastEvent = Mockito.mock(Event.class);
		Mockito.when(lastEvent.getCreatedAt()).thenReturn(Instant.now());
		Mockito.when(fetcher.getEvents(coordinates)).thenReturn(ImmutableList.of(lastEvent));
		grader.grade(coordinates,
				StudentOnGitHub.with("ocaillou", StudentOnMyCourse.with(1, "Olivier", "Cailloux", "ocailloux")), true);
		LOGGER.info("Evaluation: {}.", grader.getEvaluation());
		LOGGER.info("Comments: {}.", grader.getComments());
		/** Because: no deadline set. */
		assertTrue(grader.getPass().contains(Ex1Criterion.ON_TIME));
		assertEquals(EnumSet.range(Ex1Criterion.SUBMITTED_GITHUB_USER_NAME, Ex1Criterion.MERGE2_COMMIT),
				grader.getPass());
	}

	@Test
	void testClient() throws Exception {
		final String path = this.getClass().getResource("git").toString();
		/** For now we cheat and use a file-based access to the classpath. */
		LOGGER.info("Using path: {}.", path);
		final RepositoryCoordinates coordinates = Mockito.mock(RepositoryCoordinates.class);
		Mockito.when(coordinates.getOwner()).thenReturn("oliviercailloux");
		Mockito.when(coordinates.getRepositoryName()).thenReturn("sol-ex-1");
		Mockito.when(coordinates.getSshURLString()).thenReturn(path);
		final Client client = Client.about(coordinates);
		client.tryRetrieve();
		client.checkout("origin/dev");
		final ObjectId master = client.resolve("master");
		final String blob = client.fetchBlob(master, Paths.get("bold.txt"));
		LOGGER.info("Blob: {}.", blob);
		assertTrue(blob.startsWith("alternative approach"));
		final Optional<AnyObjectId> dir = client.getBlobId(master, Paths.get("src"));
		assertFalse(dir.isPresent());
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Ex1Test.class);
}