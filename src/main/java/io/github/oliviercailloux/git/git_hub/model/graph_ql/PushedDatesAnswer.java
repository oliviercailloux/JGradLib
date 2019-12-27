package io.github.oliviercailloux.git.git_hub.model.graph_ql;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.time.Instant;
import java.util.List;
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
		final ImmutableList<HeadNode> headNodesList;
		{
			final JsonObject heads = pushedDatesRepositoryJson.getJsonObject("heads");
			LOGGER.debug("Refs: {}.", PrintableJsonObjectFactory.wrapObject(heads));
			checkArgument(!heads.getJsonObject("pageInfo").getBoolean("hasNextPage"));
			final JsonArray headNodes = heads.getJsonArray("nodes");
			final ImmutableList.Builder<HeadNode> headNodesBuilder = ImmutableList.builder();
			for (JsonValue headNodeValue : headNodes) {
				final JsonObject node = headNodeValue.asJsonObject();
				final HeadNode headNode = HeadNode.parse(node);
				headNodesBuilder.add(headNode);
			}
			headNodesList = headNodesBuilder.build();
		}

		final JsonObject tags = pushedDatesRepositoryJson.getJsonObject("tags");
		checkArgument(!tags.getJsonObject("pageInfo").getBoolean("hasNextPage"));
		final JsonArray tagNodes = tags.getJsonArray("nodes");
		final ImmutableList.Builder<TagNode> tagNodesBuilder = ImmutableList.builder();
		for (JsonValue tagNodeValue : tagNodes) {
			final JsonObject node = tagNodeValue.asJsonObject();
			final TagNode tagNode = TagNode.parse(node);
			tagNodesBuilder.add(tagNode);
		}
		final ImmutableList<TagNode> tagNodesList = tagNodesBuilder.build();
		return new PushedDatesAnswer(headNodesList, tagNodesList);
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
			final Instant authoredDate = Instant.parse(commitJsonObject.getString("authoredDate"));
			final Instant committedDate = Instant.parse(commitJsonObject.getString("committedDate"));
			final JsonValue pushedDateJson = commitJsonObject.get("pushedDate");
			final Optional<Instant> pushedDate;
			if (!pushedDateJson.equals(JsonValue.NULL)) {
				pushedDate = Optional.of(Instant.parse(((JsonString) pushedDateJson).getString()));
			} else {
				pushedDate = Optional.empty();
			}

			return new CommitNode(oid, parents, authoredDate, committedDate, pushedDate);
		}

		private final ObjectId oid;
		private final ImmutableSet<ObjectId> parents;
		private Instant authoredDate;
		private Instant committedDate;
		private final Optional<Instant> pushedDate;

		private CommitNode(ObjectId oid, ImmutableSet<ObjectId> parents, Instant authoredDate, Instant committedDate,
				Optional<Instant> pushedDate) {
			this.oid = checkNotNull(oid);
			this.parents = checkNotNull(parents);
			this.authoredDate = checkNotNull(authoredDate);
			this.committedDate = checkNotNull(committedDate);
			this.pushedDate = checkNotNull(pushedDate);
			checkArgument(!authoredDate.isAfter(committedDate));
		}

		public ObjectId getOid() {
			return oid;
		}

		public ImmutableSet<ObjectId> getParents() {
			return parents;
		}

		public Instant getAuthoredDate() {
			return authoredDate;
		}

		public Instant getCommittedDate() {
			return committedDate;
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

	public static class CommitWithHistoryNode {
		public static CommitWithHistoryNode parse(JsonObject node) {
			final ObjectId oid = ObjectId.fromString(node.getString("oid"));
			LOGGER.debug("Commit oid: {}.", oid);
			final JsonObject jsonHistory = node.getJsonObject("history");
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
			checkArgument(commitNodesList.get(0).getOid().equals(oid));
			checkArgument(commitNodesList.size() <= historyTotalCount, String
					.format("history total count: %s, commit nodes: %s", historyTotalCount, commitNodesList.size()));
			checkArgument((commitNodesList.size() == historyTotalCount) == !hasNextPage, String
					.format("history total count: %s, commit nodes: %s", historyTotalCount, commitNodesList.size()));
			return new CommitWithHistoryNode(oid, historyTotalCount, hasNextPage, endCursor, commitNodesList);
		}

		private final ObjectId oid;
		private final int historyTotalCount;
		private final boolean hasNextPage;
		private final String endCursor;
		private final CommitNodes commitNodes;

		private CommitWithHistoryNode(ObjectId oid, int historyTotalCount, boolean hasNextPage, String endCursor,
				Iterable<CommitNode> commitNodes) {
			this.oid = checkNotNull(oid);
			this.historyTotalCount = checkNotNull(historyTotalCount);
			this.hasNextPage = checkNotNull(hasNextPage);
			this.endCursor = checkNotNull(endCursor);
			this.commitNodes = CommitNodes.given(checkNotNull(commitNodes));
			verify(historyTotalCount >= this.commitNodes.asSet().size());
		}

		public ObjectId getOid() {
			return oid;
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

	public static class HeadNode {
		public static HeadNode parse(JsonObject node) {
			final String name = node.getString("name");
			final String prefix = node.getString("prefix");
			checkArgument(prefix.equals("refs/heads/"));
			final JsonObject target = node.getJsonObject("target");
			final CommitWithHistoryNode commitWithHistory = CommitWithHistoryNode.parse(target);
			return new HeadNode(name, prefix, commitWithHistory);
		}

		private final String name;
		private final String prefix;
		private final CommitWithHistoryNode commit;

		private HeadNode(String name, String prefix, CommitWithHistoryNode commit) {
			this.name = checkNotNull(name);
			this.prefix = checkNotNull(prefix);
			this.commit = checkNotNull(commit);
		}

		public String getName() {
			return name;
		}

		public String getPrefix() {
			return prefix;
		}

		public CommitWithHistoryNode getCommitWithHistory() {
			return commit;
		}
	}

	public static class TagNode {
		public static TagNode parse(JsonObject node) {
			final String name = node.getString("name");
			final String prefix = node.getString("prefix");
			checkArgument(prefix.equals("refs/tags/"));
			final JsonObject firstTarget = node.getJsonObject("target");
			final String targetType = firstTarget.getString("__typename");
			final Optional<ObjectId> tagOid;
			final JsonObject commitTarget;
			switch (targetType) {
			case "Tag":
				tagOid = Optional.of(ObjectId.fromString(firstTarget.getString("oid")));
				commitTarget = firstTarget.getJsonObject("target");
				break;
			case "Commit":
				tagOid = Optional.empty();
				commitTarget = firstTarget;
				break;
			default:
				throw new IllegalStateException();
			}
			final CommitWithHistoryNode commitWithHistory = CommitWithHistoryNode.parse(commitTarget);
			LOGGER.debug("Tag ref oid: {}.", tagOid);
			return new TagNode(name, tagOid, commitWithHistory);
		}

		private final String name;
		private final Optional<ObjectId> tagOid;
		private final CommitWithHistoryNode commit;

		private TagNode(String name, Optional<ObjectId> tagOid, CommitWithHistoryNode commit) {
			this.name = checkNotNull(name);
			this.tagOid = checkNotNull(tagOid);
			this.commit = checkNotNull(commit);
		}

		public String getName() {
			return name;
		}

		public Optional<ObjectId> getTagOid() {
			return tagOid;
		}

		public CommitWithHistoryNode getCommitWithHistory() {
			return commit;
		}
	}

	private final ImmutableList<HeadNode> headNodes;
	private final ImmutableList<TagNode> tagNodes;
	private final ImmutableGraph<ObjectId> parentsGraph;
	/**
	 * The oids whose parents are known, thus, a subset of those seen in the
	 * provided json (there’s also those that are designated as parents of a known
	 * oid, but which are themselves unknown).
	 */
	private final ImmutableSet<ObjectId> knownOids;

	private PushedDatesAnswer(List<HeadNode> headNodes, List<TagNode> tagNodes) {
		this.headNodes = ImmutableList.copyOf(headNodes);
		this.tagNodes = ImmutableList.copyOf(tagNodes);
		final ImmutableSet<CommitWithHistoryNode> commitsWithHistory = getCommitsWithHistory();
		knownOids = commitsWithHistory.stream().flatMap((c) -> c.getCommitNodes().getKnownOids().stream())
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<Entry<ObjectId, CommitNode>> noDupl = commitsWithHistory.stream()
				.flatMap((c) -> c.getCommitNodes().asBiMap().entrySet().stream())
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableBiMap<ObjectId, CommitNode> asBiMap = noDupl.stream()
				.collect(ImmutableBiMap.toImmutableBiMap(Entry::getKey, Entry::getValue));
		parentsGraph = ImmutableGraph.copyOf(Utils.asGraph((o) -> {
			final CommitNode commitNode = asBiMap.get(o);
			return commitNode == null ? ImmutableSet.of() : commitNode.getParents();
		}, asBiMap.keySet()));
	}

	private ImmutableSet<CommitWithHistoryNode> getCommitsWithHistory() {
		final ImmutableSet<CommitWithHistoryNode> commitsFromHead = getHeadNodes().stream()
				.map((r) -> r.getCommitWithHistory()).collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<CommitWithHistoryNode> commitsFromTag = getTagNodes().stream()
				.map((r) -> r.getCommitWithHistory()).collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<CommitWithHistoryNode> commitsWithHistory = Sets.union(commitsFromHead, commitsFromTag)
				.immutableCopy();
		return commitsWithHistory;
	}

	private ImmutableList<HeadNode> getHeadNodes() {
		return headNodes;
	}

	private ImmutableList<TagNode> getTagNodes() {
		return tagNodes;
	}

	public ImmutableGraph<ObjectId> getParentsGraph() {
		return parentsGraph;

	}

	public ImmutableSet<ObjectId> getUnknownOids() {
		return Sets.difference(parentsGraph.nodes(), knownOids).immutableCopy();
	}

	public ImmutableList<CommitNode> getCommitNodes() {
		return getCommitsWithHistory().stream().flatMap((c) -> c.getCommitNodes().asSet().stream())
				.collect(ImmutableList.toImmutableList());
	}
}
