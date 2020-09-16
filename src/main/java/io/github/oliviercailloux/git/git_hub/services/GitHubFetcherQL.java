package io.github.oliviercailloux.git.git_hub.services;

import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.exceptions.Unchecker.IO_UNCHECKER;
import static java.util.Objects.requireNonNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.graph.Graph;
import com.google.common.io.Resources;

import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.git.git_hub.model.GitHubRealToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.PushedDatesAnswer;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.PushedDatesAnswer.CommitNode;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.PushedDatesAnswer.CommitNodes;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithFiles;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;
import io.github.oliviercailloux.utils.Utils;

public class GitHubFetcherQL implements AutoCloseable {
	public static final String GRAPHQL_ENDPOINT = "https://api.github.com/graphql";

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHubFetcherQL.class);

	private static final Function<String, String> RESOURCE_READER = IO_UNCHECKER
			.wrapFunction((n) -> Resources.toString(GitHubFetcherQL.class.getResource(n), StandardCharsets.UTF_8));

	public static GitHubFetcherQL using(GitHubRealToken token) {
		return new GitHubFetcherQL(token);
	}

	private Client client;

	private final JsonBuilderFactory jsonBuilderFactory;

	/**
	 * Not <code>null</code>.
	 */
	private String rateLimit;

	private Instant rateReset;

	private final GitHubRealToken token;

	private GitHubFetcherQL(GitHubRealToken token) {
		/** Authorization token required for Graph QL GitHub API. */
		this.token = requireNonNull(token);
		rateLimit = "";
		rateReset = null;
		client = ClientBuilder.newClient();
		jsonBuilderFactory = Json.createBuilderFactory(null);
	}

	@Override
	public void close() {
		client.close();
	}

	public List<RepositoryWithIssuesWithHistory> find(String gitHubProjectName, Instant floorSearchDate)
			throws UnsupportedOperationException {
		final JsonObject varsJson = jsonBuilderFactory.createObjectBuilder().add("queryString",
				"\"" + gitHubProjectName + "\"" + " in:name created:>=" + floorSearchDate.toString()).build();
		final JsonObject res = query("searchRepositories", ImmutableList.of("repositoryWithIssuesWithHistory"),
				varsJson);
		final JsonObject searchRes = res.getJsonObject("search");
		final int nb = searchRes.getInt("repositoryCount");
		final JsonArray edges = searchRes.getJsonArray("edges");
		final List<RepositoryWithIssuesWithHistory> found = edges.stream()
				.map((v) -> v.asJsonObject().getJsonObject("node")).map(RepositoryWithIssuesWithHistory::from)
				.collect(Collectors.toList());
		final List<RepositoryWithIssuesWithHistory> matching = found.stream()
				.filter((r) -> r.getBare().getName().equals(gitHubProjectName)).collect(Collectors.toList());
		if (searchRes.getJsonObject("pageInfo").getBoolean("hasNextPage")) {
			throw new UnsupportedOperationException("Too many results (" + nb + "), partial list is: " + found + ".");
		}
		assert edges.size() == nb;
		return matching;
	}

	public Optional<RepositoryWithIssuesWithHistory> getRepository(RepositoryCoordinates coordinates) {
		final JsonObject varsJson = jsonBuilderFactory.createObjectBuilder()
				.add("repositoryName", coordinates.getRepositoryName()).add("repositoryOwner", coordinates.getOwner())
				.build();
		/**
		 * TODO check why queryOpt is used here (thereby masking errors) instead of
		 * query.
		 */
		return queryOpt("repository", ImmutableList.of("repositoryWithIssuesWithHistory"), varsJson)
				.map((d) -> d.getJsonObject("repository")).map(RepositoryWithIssuesWithHistory::from);
	}

	public Optional<RepositoryWithFiles> getRepositoryWithFiles(RepositoryCoordinates coordinates, Path path) {
		LOGGER.info("Getting files from {}, {}.", coordinates, path);
		final String pathString = Streams.stream(path.iterator()).map(Path::toString).collect(Collectors.joining("/"));
		final JsonObject varsJson = jsonBuilderFactory.createObjectBuilder()
				.add("repositoryName", coordinates.getRepositoryName()).add("repositoryOwner", coordinates.getOwner())
				.add("ref", "master:" + pathString).build();
		final Optional<RepositoryWithFiles> repo = queryOpt("filesAtRef", ImmutableList.of(), varsJson)
				.map((d) -> d.getJsonObject("repository")).map((r) -> RepositoryWithFiles.from(r, path));
		LOGGER.info("Got: {}.", repo);
		return repo;
	}

	public GitHubHistory getGitHubHistory(RepositoryCoordinates coordinates) {
		/**
		 * I build the graph while asking queries: I need to be able to detect when I’m
		 * back at some commit I know already. Thus, parse the initial request, build a
		 * partial graph of parents, maintain a list of nodes that have not been seen
		 * yet (those whose parents are unknown). Request history about those nodes
		 * (without using the after end cursor) with the continuation query.
		 */
		final PushedDatesAnswer initialAnswer;
		{
			final JsonObjectBuilder builder = jsonBuilderFactory.createObjectBuilder()
					.add("repositoryName", coordinates.getRepositoryName())
					.add("repositoryOwner", coordinates.getOwner());
			LOGGER.info("Initial request to {}.", coordinates);
			final JsonObject varsJson = builder.build();
			final JsonObject pushedDatesRepositoryJson = query("pushedDates", ImmutableList.of("commitHistory"),
					varsJson).getJsonObject("repository");
			initialAnswer = PushedDatesAnswer.parseInitialAnswer(pushedDatesRepositoryJson);
		}
		final ImmutableList.Builder<CommitNode> commitsBuilder = ImmutableList.builder();
		commitsBuilder.addAll(initialAnswer.getCommitNodes());
		Optional<ObjectId> next = initialAnswer.getUnknownOids().stream().findFirst();
		while (next.isPresent()) {
			final JsonObjectBuilder builder = jsonBuilderFactory.createObjectBuilder()
					.add("repositoryName", coordinates.getRepositoryName())
					.add("repositoryOwner", coordinates.getOwner());
			final ObjectId oid = next.get();
			builder.add("oid", oid.getName());
			LOGGER.info("Continuation request to {}, {}.", coordinates, oid);
			final JsonObject varsJson = builder.build();
			final JsonObject continuedJson = query("pushedDatesContinued", ImmutableList.of(), varsJson)
					.getJsonObject("repository");
			final CommitNodes answer = CommitNodes.parse(continuedJson);
			commitsBuilder.addAll(answer.asSet());
			next = CommitNodes.given(commitsBuilder.build()).getUnknownOids().stream().findFirst();
		}

		final ImmutableList<CommitNode> commits = commitsBuilder.build();
		final ImmutableSetMultimap<ObjectId, CommitNode> byOid = commits.stream()
				.collect(ImmutableSetMultimap.toImmutableSetMultimap((c) -> c.getOid(), (c) -> c));

		verify(byOid.size() == byOid.keySet().size());
		final ImmutableBiMap<ObjectId, CommitNode> oidToNode = byOid.asMap().entrySet().stream().collect(
				ImmutableBiMap.toImmutableBiMap((e) -> e.getKey(), (e) -> Iterables.getOnlyElement(e.getValue())));

		final Graph<ObjectId> history = Utils.asGraph((o) -> oidToNode.get(o).getParents(), oidToNode.keySet());
		final ImmutableMap<ObjectId, Instant> commitDates = oidToNode.values().stream()
				.collect(ImmutableMap.toImmutableMap((c) -> c.getOid(), (c) -> c.getCommittedDate()));
		final ImmutableMap<ObjectId, Instant> pushedDates = oidToNode.values().stream()
				.filter((c) -> c.getPushedDate().isPresent())
				.collect(ImmutableMap.toImmutableMap((c) -> c.getOid(), (c) -> c.getPushedDate().get()));
		return GitHubHistory.given(history, commitDates, pushedDates);
	}

	private JsonObject query(String queryName, List<String> fragmentNames, JsonObject variables) {
		final JsonObject ret = rawQuery(queryName, fragmentNames, variables);
		if (ret.containsKey("errors")) {
			throw new IllegalStateException(ret.toString());
		}
		final JsonObject data = ret.getJsonObject("data");
		LOGGER.debug(PrintableJsonObjectFactory.wrapObject(data).toString());
		return data;
	}

	private Optional<JsonObject> queryOpt(String queryName, List<String> fragmentNames, JsonObject variables) {
		final JsonObject ret = rawQuery(queryName, fragmentNames, variables);

		final Optional<JsonObject> dataOpt;
		if (ret.containsKey("errors")) {
			LOGGER.debug("Error: {}.", ret.toString());
			dataOpt = Optional.empty();
		} else {
			final JsonObject data = ret.getJsonObject("data");
			LOGGER.debug(PrintableJsonObjectFactory.wrapObject(data).toString());
			dataOpt = Optional.of(data);
		}
		return dataOpt;
	}

	/**
	 * Returns the raw result of the given query, normally including a data and
	 * possibly an error key.
	 */
	private JsonObject rawQuery(String queryName, List<String> fragmentNames, JsonObject variables) {
		final JsonObject queryJson;
		{
			final String queryGQL = RESOURCE_READER.apply("queries/" + queryName + ".txt");
			final String fragments = fragmentNames.stream().map((n) -> "fragments/" + n + ".txt").map(RESOURCE_READER)
					.collect(Collectors.joining(""));
			queryJson = jsonBuilderFactory.createObjectBuilder().add("query", queryGQL + "\n" + fragments)
					.add("variables", variables).build();
		}

		final Builder request = client.target(GRAPHQL_ENDPOINT).request();
		token.addToRequest(request);

		final JsonObject ret;
		try (Response response = request.post(Entity.json(queryJson))) {
			readRates(response);
			if (response.getStatus() != Response.Status.OK.getStatusCode()) {
				String message;
				try {
					ret = response.readEntity(JsonObject.class);
					message = PrintableJsonObjectFactory.wrapObject(ret).toString();
				} catch (Exception masqued) {
					message = "(and could not read entity: " + masqued.getMessage() + ")";
				}
				throw new WebApplicationException(message, response);
			}
			ret = response.readEntity(JsonObject.class);
		}
		return ret;
	}

	private void readRates(Response response) {
		rateLimit = Strings.nullToEmpty(response.getHeaderString("X-RateLimit-Remaining"));
		LOGGER.debug("Rate limit: {}.", rateLimit);
		final String rateResetString = Strings.nullToEmpty(response.getHeaderString("X-RateLimit-Reset"));
		if (!rateResetString.isEmpty()) {
			rateReset = Instant.ofEpochSecond(Integer.parseInt(rateResetString));
			LOGGER.debug("Rate reset: {}.", rateReset);
		} else {
			rateReset = null;
			LOGGER.debug("No rate reset info.");
		}
	}

}
