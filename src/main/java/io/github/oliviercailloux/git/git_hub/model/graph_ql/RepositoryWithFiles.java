package io.github.oliviercailloux.git.git_hub.model.graph_ql;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Optional;

import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableMap;

public class RepositoryWithFiles {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryWithFiles.class);

	public static RepositoryWithFiles from(JsonObject json, Path path) {
		return new RepositoryWithFiles(json, path);
	}

	private final ImmutableMap<Path, String> contentFromFileNames;

	private final Path path;

	private final Repository repository;

	private RepositoryWithFiles(JsonObject json, Path path) {
		this.path = requireNonNull(path);
		repository = Repository.from(json);
		checkArgument(json.get("refTree").getValueType() != ValueType.NULL);
		{
			contentFromFileNames = json.getJsonObject("refTree").getJsonArray("entries").stream()
					.map(JsonValue::asJsonObject).filter((e) -> e.getString("type").equals("blob"))
					.map((e) -> new AbstractMap.SimpleEntry<>(path.resolve(e.getString("name")),
							e.getJsonObject("object")))
					.filter(((e) -> {
						final boolean binary = e.getValue().getBoolean("isBinary");
						if (!binary) {
							if (e.getValue().getBoolean("isTruncated")) {
								throw new IllegalStateException();
							}
						} else {
							LOGGER.warn("Ignoring binary file.");
						}
						return !binary;
					})).collect(ImmutableMap.<SimpleEntry<Path, JsonObject>, Path, String>toImmutableMap(
							SimpleEntry::getKey, (e) -> e.getValue().getString("text")));
		}
	}

	public Repository getBare() {
		return repository;
	}

	public Optional<String> getContent(Path file) {
		return Optional.ofNullable(contentFromFileNames.get(file));
	}

	public Optional<String> getContent(String fileName) {
		return getContent(path.resolve(fileName));
	}

	public ImmutableMap<Path, String> getContentFromFileNames() {
		return contentFromFileNames;
	}

	public User getOwner() {
		return repository.getOwner();
	}

	public Path getPath() {
		return path;
	}

	public URI getURI(Path completePath) {
		/**
		 * TODO obtain a uri, but https://developer.github.com/v4/object/treeentry/ does
		 * not give it. Test this method.
		 */
		try {
			return new URI(repository.getURI().getScheme(), repository.getURI().getHost(),
					repository.getURI().getPath() + "/blob/master/" + completePath.toString(), null);
//			return new URL(repository.getURI(), "blob/master/" + Utils.getEncoded(completePath));
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		helper.addValue(repository.getName());
		helper.add("files", contentFromFileNames.keySet());
		return helper.toString();
	}
}
