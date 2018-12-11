package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.time.Instant;
import java.util.List;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.annotation.JsonbPropertyOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.st_projects.utils.Utils;

/**
 * Immutable.
 *
 * @author Olivier Cailloux
 *
 */
@JsonbPropertyOrder({ "name", "gitHubName", "URL", "functionalities", "lastModification", "queried" })
public class Project {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Project.class);

	/**
	 * This constructor is appropriate when the project has just been built from
	 * memory (e.g. for a test).
	 */
	public static Project from(String name) {
		return new Project(name, Utils.EXAMPLE_URL, ImmutableList.of(), Instant.now(), Instant.now());
	}

	/**
	 * This constructor is appropriate when the project has just been built from
	 * memory (e.g. for a test).
	 */
	public static Project from(String name, List<Functionality> functionalities) {
		return new Project(name, Utils.EXAMPLE_URL, functionalities, Instant.now(), Instant.now());
	}

	public static Project from(String name, List<Functionality> functionalities, Instant lastModification) {
		return new Project(name, Utils.EXAMPLE_URL, functionalities, lastModification, Instant.now());
	}

	public static Project from(String name, List<Functionality> functionalities, Instant lastModification,
			Instant queried) {
		/**
		 * TODO remove all the constructors that have no URLs, which really should not
		 * exist, I suppose.
		 */
		return new Project(name, Utils.EXAMPLE_URL, functionalities, lastModification, queried);
	}

	public static Project from(String name, URL url, List<Functionality> functionalities, Instant lastModification,
			Instant queried) {
		return new Project(name, url, functionalities, lastModification, queried);
	}

	/**
	 * Not <code>null</code>.
	 */
	private final ImmutableList<Functionality> functionalities;

	private final Instant lastModification;

	/**
	 * Not <code>null</code>, not empty.
	 */
	private final String name;

	private final Instant queried;

	private URL url;

	private Project(String name, URL url, Iterable<Functionality> functionalities, Instant lastModification,
			Instant queried) {
		this.name = requireNonNull(name);
		this.url = requireNonNull(url);
		/** Just to check for conformance. */
		Utils.toURI(url);
		checkArgument(!name.isEmpty());
		this.functionalities = ImmutableList.copyOf(requireNonNull(functionalities));
		this.queried = requireNonNull(queried);
		this.lastModification = requireNonNull(lastModification);
		checkArgument(lastModification.compareTo(queried) <= 0);
		LOGGER.debug("Created {}.", name);
	}

	public String asJsonPretty() {
		try (Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true))) {
			return jsonb.toJson(this);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public ImmutableList<Functionality> getFunctionalities() {
		return functionalities;
	}

	public String getGitHubName() {
		return name.replace(' ', '-');
	}

	/**
	 * @return before or equal to {@link #getQueried()}.
	 */
	public Instant getLastModification() {
		return lastModification;
	}

	public String getName() {
		return name;
	}

	/**
	 * @return after or equal to {@link #getLastModification()}.
	 */
	public Instant getQueried() {
		return queried;
	}

	/**
	 * @return {@link Utils#EXAMPLE_URL} if the url is not set.
	 */
	public URL getURL() {
		return url;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(name).toString();
	}

}
