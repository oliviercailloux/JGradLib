package io.github.oliviercailloux.st_projects.model;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;

import javax.json.JsonObject;
import javax.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.git_hub.graph_ql.Repository;
import io.github.oliviercailloux.git_hub.graph_ql.User;

public class RepositoryWithFiles {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryWithFiles.class);

	public static RepositoryWithFiles from(JsonObject json, Path path) {
		return new RepositoryWithFiles(json, path);
	}

	private final ImmutableList<String> files;

	private final Path path;

	private final Repository repository;

	private RepositoryWithFiles(JsonObject json, Path path) {
		this.path = requireNonNull(path);
		repository = Repository.from(json);
		{
			files = json.getJsonObject("refTree").getJsonArray("entries").stream().map(JsonValue::asJsonObject)
					.filter((e) -> e.getString("type").equals("blob")).map((e) -> e.getString("name"))
					.collect(ImmutableList.toImmutableList());
		}
	}

	public Repository getBare() {
		return repository;
	}

	/**
	 * @return the first-level files in this repository.
	 */
	public ImmutableList<String> getFiles() {
		return files;
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
