package io.github.oliviercailloux.st_projects.model;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;

import javax.json.JsonObject;
import javax.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git_hub.graph_ql.Repository;
import io.github.oliviercailloux.git_hub.graph_ql.User;

public class RepositoryWithFiles {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryWithFiles.class);

	public static RepositoryWithFiles from(JsonObject json, Path path) {
		return new RepositoryWithFiles(json, path);
	}

	private final ImmutableMap<String, String> contentFromFileNames;

	private final Path path;

	private final Repository repository;

	private RepositoryWithFiles(JsonObject json, Path path) {
		this.path = requireNonNull(path);
		repository = Repository.from(json);
		{
			contentFromFileNames = json.getJsonObject("refTree").getJsonArray("entries").stream()
					.map(JsonValue::asJsonObject).filter((e) -> e.getString("type").equals("blob"))
					.map((e) -> new AbstractMap.SimpleEntry<>(e.getString("name"), e.getJsonObject("object")))
					.filter(((e) -> {
						final boolean binary = e.getValue().getBoolean("isBinary");
						if (!binary) {
							if (e.getValue().getBoolean("isTruncated")) {
								throw new IllegalStateException();
							}
						}
						return !binary;
					})).collect(ImmutableMap.<SimpleEntry<String, JsonObject>, String, String>toImmutableMap(
							SimpleEntry::getKey, (e) -> e.getValue().getString("text")));
		}
	}

	public Repository getBare() {
		return repository;
	}

	public String getContent(String fileName) {
		return contentFromFileNames.get(fileName);
	}

	public ImmutableMap<String, String> getContentFromFileNames() {
		return contentFromFileNames;
	}

	/**
	 * @return the first-level files in this repository.
	 */
	public ImmutableSet<String> getFileNames() {
		return contentFromFileNames.keySet();
	}

	public User getOwner() {
		return repository.getOwner();
	}

	public Path getPath() {
		return path;
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		helper.addValue(repository.getName());
		return helper.toString();
	}
}
