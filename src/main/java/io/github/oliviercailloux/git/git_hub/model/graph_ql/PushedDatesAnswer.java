package io.github.oliviercailloux.git.git_hub.model.graph_ql;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.time.Instant;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.json.PrintableJsonObjectFactory;
import io.github.oliviercailloux.utils.Utils;

public class PushedDatesAnswer {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PushedDatesAnswer.class);

	public static PushedDatesAnswer parseInitialAnswer(JsonObject pushedDatesRepositoryJson) {
		final JsonObject refs = pushedDatesRepositoryJson.getJsonObject("refs");
		LOGGER.debug("Refs: {}.", PrintableJsonObjectFactory.wrapObject(refs));
		final JsonArray refNodes = refs.getJsonArray("nodes");
		final ImmutableList.Builder<RefNode> refNodesBuilder = ImmutableList.builder();
		for (JsonValue refNodeValue : refNodes) {
			final JsonObject node = refNodeValue.asJsonObject();
			final RefNode refNode = RefNode.parse(node);
			refNodesBuilder.add(refNode);
		}
		final ImmutableList<RefNode> refNodesList = refNodesBuilder.build();
		return new PushedDatesAnswer(refNodesList);
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

	public static class CommitNodes {
		public static CommitNodes given(Iterable<CommitNode> nodes) {
			final CommitNodes commitNodes = new CommitNodes(nodes);
			return commitNodes;
		}

		public static CommitNodes parse(JsonObject node) {
			final JsonObject target = node.getJsonObject("object");
			final JsonObject jsonHistory = target.getJsonObject("history");
			final JsonArray commitNodes = jsonHistory.getJsonArray("nodes");
			final ImmutableList.Builder<CommitNode> commitsBuilder = ImmutableList.builder();
			for (JsonValue commitNodeValue : commitNodes) {
				final JsonObject commit = commitNodeValue.asJsonObject();
				final CommitNode commitNode = CommitNode.parse(commit);
				commitsBuilder.add(commitNode);
			}
			return given(commitsBuilder.build());
		}

		private final ImmutableSet<CommitNode> commitNodes;
		private final ImmutableBiMap<ObjectId, CommitNode> oidToNode;
		/**
		 * The oids whose parents are known, thus, a subset of those seen in the
		 * provided json (there’s also those that are designated as parents of a known
		 * oid, but which are themselves unknown).
		 */
		private final ImmutableSet<ObjectId> knownOids;
		private final ImmutableGraph<ObjectId> parentsGraph;

		private CommitNodes(Iterable<CommitNode> nodes) {
			commitNodes = ImmutableSet.copyOf(nodes);
			final ImmutableSetMultimap<ObjectId, CommitNode> byOid = commitNodes.stream()
					.collect(ImmutableSetMultimap.toImmutableSetMultimap((c) -> c.getOid(), (c) -> c));
			verify(byOid.size() == byOid.keySet().size());
			oidToNode = byOid.asMap().entrySet().stream().collect(
					ImmutableBiMap.toImmutableBiMap((e) -> e.getKey(), (e) -> Iterables.getOnlyElement(e.getValue())));

			knownOids = oidToNode.keySet();
			parentsGraph = ImmutableGraph.copyOf(Utils.asGraph((o) -> {
				final CommitNode commitNode = oidToNode.get(o);
				return commitNode == null ? ImmutableSet.of() : commitNode.getParents();
			}, oidToNode.keySet()));
		}

		public ImmutableSet<CommitNode> asSet() {
			return commitNodes;
		}

		public ImmutableGraph<ObjectId> getParentsGraph() {
			return parentsGraph;

		}

		public ImmutableBiMap<ObjectId, CommitNode> asBiMap() {
			return oidToNode;
		}

		public ImmutableSet<ObjectId> getKnownOids() {
			return knownOids;
		}

		public ImmutableSet<ObjectId> getUnknownOids() {
			return Sets.difference(parentsGraph.nodes(), knownOids).immutableCopy();
		}
	}

	public static class RefNode {
		public static RefNode parse(JsonObject node) {
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
			checkArgument(commitNodesList.get(0).getOid().equals(refOid));
			checkArgument(commitNodesList.size() <= historyTotalCount, String
					.format("history total count: %s, commit nodes: %s", historyTotalCount, commitNodesList.size()));
			checkArgument((commitNodesList.size() == historyTotalCount) == !hasNextPage, String
					.format("history total count: %s, commit nodes: %s", historyTotalCount, commitNodesList.size()));
			return new RefNode(name, prefix, refOid, historyTotalCount, hasNextPage, endCursor, commitNodesList);
		}

		private final String name;
		private final String prefix;
		private final ObjectId refOid;
		private final int historyTotalCount;
		private final boolean hasNextPage;
		private final String endCursor;
		private final CommitNodes commitNodes;

		private RefNode(String name, String prefix, ObjectId refOid, int historyTotalCount, boolean hasNextPage,
				String endCursor, Iterable<CommitNode> commitNodes) {
			this.name = checkNotNull(name);
			this.prefix = checkNotNull(prefix);
			this.refOid = checkNotNull(refOid);
			this.historyTotalCount = checkNotNull(historyTotalCount);
			this.hasNextPage = checkNotNull(hasNextPage);
			this.endCursor = checkNotNull(endCursor);
			this.commitNodes = CommitNodes.given(checkNotNull(commitNodes));
			verify(historyTotalCount >= this.commitNodes.asSet().size());
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
		 *
		 * @return at least the number of commit nodes, and strictly more iff has next
		 *         page.
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

		public CommitNodes getCommitNodes() {
			return commitNodes;
		}
	}

	private final ImmutableList<RefNode> refNodes;
	private final ImmutableGraph<ObjectId> parentsGraph;
	/**
	 * The oids whose parents are known, thus, a subset of those seen in the
	 * provided json (there’s also those that are designated as parents of a known
	 * oid, but which are themselves unknown).
	 */
	private final ImmutableSet<ObjectId> knownOids;

	private PushedDatesAnswer(ImmutableList<RefNode> refNodes) {
		this.refNodes = checkNotNull(refNodes);
		knownOids = getRefNodes().stream().flatMap((r) -> r.getCommitNodes().getKnownOids().stream())
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<Entry<ObjectId, CommitNode>> noDupl = getRefNodes().stream()
				.flatMap((r) -> r.getCommitNodes().asBiMap().entrySet().stream())
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableBiMap<ObjectId, CommitNode> asBiMap = noDupl.stream()
				.collect(ImmutableBiMap.toImmutableBiMap(Entry::getKey, Entry::getValue));
		parentsGraph = ImmutableGraph.copyOf(Utils.asGraph((o) -> {
			final CommitNode commitNode = asBiMap.get(o);
			return commitNode == null ? ImmutableSet.of() : commitNode.getParents();
		}, asBiMap.keySet()));
	}

	public ImmutableList<RefNode> getRefNodes() {
		return refNodes;
	}

	public ImmutableGraph<ObjectId> getParentsGraph() {
		return parentsGraph;

	}

	public ImmutableSet<ObjectId> getUnknownOids() {
		return Sets.difference(parentsGraph.nodes(), knownOids).immutableCopy();
	}

	public ImmutableList<CommitNode> getCommitNodes() {
		return refNodes.stream().flatMap((r) -> r.getCommitNodes().asSet().stream())
				.collect(ImmutableList.toImmutableList());
	}
}
