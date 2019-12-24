package io.github.oliviercailloux.git.git_hub.model.graph_ql;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import io.github.oliviercailloux.json.PrintableJsonObjectFactory;

public class PushedDatesAnswer {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PushedDatesAnswer.class);

	public static PushedDatesAnswer parse(JsonObject pushedDatesRepositoryJson, boolean isInitialRequest) {
		final JsonObject refs = pushedDatesRepositoryJson.getJsonObject("refs");
		LOGGER.debug("Refs: {}.", PrintableJsonObjectFactory.wrapObject(refs));
		final JsonArray refNodes = refs.getJsonArray("nodes");
		final ImmutableList.Builder<RefNode> refNodesBuilder = ImmutableList.builder();
		for (JsonValue refNodeValue : refNodes) {
			final JsonObject node = refNodeValue.asJsonObject();
			final RefNode refNode = RefNode.parse(node, isInitialRequest);
			refNodesBuilder.add(refNode);
		}
		final ImmutableList<RefNode> refNodesList = refNodesBuilder.build();
		return new PushedDatesAnswer(refNodesList, isInitialRequest);
	}

	public static class CommitNode {
		public static CommitNode parse(JsonObject commit) {
			final JsonObject commitJsonObject = commit.asJsonObject();
			final String oidString = commitJsonObject.getString("oid");
			final ObjectId oid = ObjectId.fromString(oidString);
			final JsonObject parentsObject = commitJsonObject.getJsonObject("parents");
			final int nbParents = parentsObject.getInt("totalCount");
			final JsonArray parentsArray = parentsObject.getJsonArray("nodes");
			checkArgument(parentsArray.size() == nbParents);
			final ImmutableSet.Builder<ObjectId> thoseParentsBuilder = ImmutableSet.builder();
			for (JsonValue parentNode : parentsArray) {
				final JsonObject parentJsonObject = parentNode.asJsonObject();
				final String parentOid = parentJsonObject.getString("oid");
				thoseParentsBuilder.add(ObjectId.fromString(parentOid));
			}
			final ImmutableSet<ObjectId> parents = thoseParentsBuilder.build();
			final JsonValue pushedDateJson = commitJsonObject.get("pushedDate");
			final Optional<Instant> pushedDate;
			if (!pushedDateJson.equals(JsonValue.NULL)) {
				pushedDate = Optional.of(Instant.parse(((JsonString) pushedDateJson).getString()));
			} else {
				pushedDate = Optional.empty();
			}

			return new CommitNode(oid, parents, pushedDate);
		}

		private final ObjectId oid;
		private final ImmutableSet<ObjectId> parents;
		private final Optional<Instant> pushedDate;

		private CommitNode(ObjectId oid, ImmutableSet<ObjectId> parents, Optional<Instant> pushedDate) {
			this.oid = checkNotNull(oid);
			this.parents = checkNotNull(parents);
			this.pushedDate = checkNotNull(pushedDate);
		}

		public ObjectId getOid() {
			return oid;
		}

		public ImmutableSet<ObjectId> getParents() {
			return parents;
		}

		public Optional<Instant> getPushedDate() {
			return pushedDate;
		}

		@Override
		public boolean equals(Object o2) {
			if (!(o2 instanceof CommitNode)) {
				return false;
			}
			final CommitNode c2 = (CommitNode) o2;
			return oid.equals(c2.oid) && parents.equals(c2.parents) && pushedDate.equals(c2.pushedDate);
		}

		@Override
		public int hashCode() {
			return Objects.hash(oid, parents, pushedDate);
		}
	}

	public static class RefNode {
		public static RefNode parse(JsonObject node, boolean initialRequest) {
			final String name = node.getString("name");
			final String prefix = node.getString("prefix");
			checkArgument(prefix.equals("refs/heads/"));
			final JsonObject target = node.getJsonObject("target");
			final ObjectId refOid = ObjectId.fromString(target.getString("oid"));
			LOGGER.debug("Ref oid: {}.", refOid);
			final JsonObject jsonHistory = target.getJsonObject("history");
			final int historyTotalCount = jsonHistory.getInt("totalCount");
			final JsonObject pageInfo = jsonHistory.getJsonObject("pageInfo");
			final boolean hasNextPage = pageInfo.getBoolean("hasNextPage");
			final String endCursor = pageInfo.getString("endCursor");
			final JsonArray commitNodes = jsonHistory.getJsonArray("nodes");
			final ImmutableList.Builder<CommitNode> commitsBuilder = ImmutableList.builder();
			for (JsonValue commitNodeValue : commitNodes) {
				final JsonObject commit = commitNodeValue.asJsonObject();
				final CommitNode commitNode = CommitNode.parse(commit);
				commitsBuilder.add(commitNode);
			}
			final ImmutableList<CommitNode> commitNodesList = commitsBuilder.build();
			checkArgument(!commitNodesList.isEmpty());
			if (initialRequest) {
				checkArgument(commitNodesList.get(0).getOid().equals(refOid));
			}
			/**
			 * When it’s a continuation request (one that has an "after" parameter), the
			 * name of the branch is unrelated to the nodes!
			 */
			if (initialRequest && hasNextPage) {
				checkArgument(commitNodesList.size() < historyTotalCount, String.format(
						"history total count: %s, commit nodes: %s", historyTotalCount, commitNodesList.size()));
			}
			return new RefNode(name, prefix, refOid, historyTotalCount, hasNextPage, endCursor, commitNodesList,
					initialRequest);
		}

		private final String name;
		private final String prefix;
		private final ObjectId refOid;
		private final int historyTotalCount;
		private final boolean hasNextPage;
		private final String endCursor;
		private final ImmutableList<CommitNode> commitNodes;
		private final boolean initialRequest;

		private RefNode(String name, String prefix, ObjectId refOid, int historyTotalCount, boolean hasNextPage,
				String endCursor, ImmutableList<CommitNode> commitNodes, boolean initialRequest) {
			this.name = checkNotNull(name);
			this.prefix = checkNotNull(prefix);
			this.refOid = checkNotNull(refOid);
			this.historyTotalCount = checkNotNull(historyTotalCount);
			this.hasNextPage = checkNotNull(hasNextPage);
			this.endCursor = checkNotNull(endCursor);
			this.commitNodes = checkNotNull(commitNodes);
			this.initialRequest = checkNotNull(initialRequest);
			if (initialRequest) {
				verify(historyTotalCount >= commitNodes.size());
			}
		}

		public String getName() {
			return name;
		}

		public String getPrefix() {
			return prefix;
		}

		public ObjectId getRefOid() {
			return refOid;
		}

		/**
		 * This number is meaningless if this is a continuation request.
		 *
		 * @return if it’s an initial request, at least the number of commit nodes, and
		 *         has next page ⇒ strictly more (the converse doesn’t hold: could have
		 *         strictly more total count than commit nodes but no next page because
		 *         this might be the last page).
		 */
		public int getHistoryTotalCount() {
			return historyTotalCount;
		}

		public boolean hasNextPage() {
			return hasNextPage;
		}

		public String getEndCursor() {
			return endCursor;
		}

		public ImmutableList<CommitNode> getCommitNodes() {
			return commitNodes;
		}

		public boolean isInitialRequest() {
			return initialRequest;
		}
	}

	private PushedDatesAnswer(ImmutableList<RefNode> refNodes, boolean isInitialRequest) {
		this.refNodes = checkNotNull(refNodes);
		final ImmutableSet<Boolean> areInitialRequests = refNodes.stream().map((r) -> r.isInitialRequest()).distinct()
				.collect(ImmutableSet.toImmutableSet());
		checkArgument(areInitialRequests.size() == 1);
		isInitialRequest = Iterables.getOnlyElement(areInitialRequests);
		if (!isInitialRequest) {
			checkArgument(refNodes.size() == 1);
		}
	}

	private final ImmutableList<RefNode> refNodes;
	private final boolean isInitialRequest;

	public ImmutableList<RefNode> getRefNodes() {
		return refNodes;
	}

	public ImmutableSet<String> getNextCursors() {
		/**
		 * TODO must ask when fetching again the branch corresponding to the cursor,
		 * otherwise GitHub bugs and answers with the history related to the cursor but
		 * copied to every branch.
		 */
		return refNodes.stream().filter((r) -> r.hasNextPage).map((r) -> r.endCursor)
				.collect(ImmutableSet.toImmutableSet());
	}

	public boolean isInitialRequest() {
		return isInitialRequest;
	}
}
