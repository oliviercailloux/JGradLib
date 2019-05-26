package io.github.oliviercailloux.git.git_hub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Range;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;

class TestTimeline {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestTimeline.class);

	@Test
	void testJavaCourse() throws Exception {
		final GitHubTimelineReader reader = new GitHubTimelineReader();
		final Client client = Client.aboutAndUsingTmp(RepositoryCoordinates.from("oliviercailloux", "java-course"));
		client.tryRetrieve();
		assertTrue(client.hasContent());
		client.getWholeHistory();
		final Map<ObjectId, Range<Instant>> receptions = reader.getReceptionRanges(client);
		final ImmutableSet<RevCommit> unknownCommits = reader.getCommitsBeforeFirstPush();
		LOGGER.info("Reception ranges: {}; before push: {}.", receptions.size(), unknownCommits.size());
		assertTrue(receptions.size() > 800);
		assertTrue(unknownCommits.size() > 700);
		assertEquals(Instant.MIN, reader.getReceivedAtLowerBounds().values().stream().limit(unknownCommits.size())
				.distinct().collect(MoreCollectors.onlyElement()));
	}

	@Test
	void testRecent() throws Exception {
		final GitHubTimelineReader reader = new GitHubTimelineReader();
		final Client client = Client.aboutAndUsingTmp(RepositoryCoordinates.from("oliviercailloux", "JdL"));
		client.tryRetrieve();
		assertTrue(client.hasContent());
		client.getWholeHistory();
		final Map<ObjectId, Range<Instant>> receptions = reader.getReceptionRanges(client);
		final ImmutableSet<RevCommit> commitsBeforeFirstPush = reader.getCommitsBeforeFirstPush();
		/** Note that this is not precisely correct, simplification to be solved. */
		LOGGER.info("Reception ranges: {}; before push: {}.", receptions.size(), commitsBeforeFirstPush.size());
		assertTrue(receptions.size() < 100);
		assertTrue(commitsBeforeFirstPush.size() == 3);
//		assertEquals(Range.singleton(ZonedDateTime.parse("2019-02-18T12:33:11+01:00").toInstant()),
//				receptions.get(ObjectId.fromString("3122819edda3dfd74178167e519a26c6bfdb694d")));
//		assertEquals(Range.singleton(ZonedDateTime.parse("2019-02-18T12:50:59+01:00").toInstant()),
//				receptions.get(ObjectId.fromString("1ce3b311215df9a40e5751fd6318beea17060325")));
	}

}
