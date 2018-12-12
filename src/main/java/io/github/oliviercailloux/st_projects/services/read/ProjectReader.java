package io.github.oliviercailloux.st_projects.services.read;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.asciidoctor.Asciidoctor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.CharStreams;

import io.github.oliviercailloux.st_projects.model.Functionality;
import io.github.oliviercailloux.st_projects.model.Project;

public class ProjectReader {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectReader.class);

	public static ProjectReader noInit() {
		return new ProjectReader();
	}

	public static ProjectReader using(Asciidoctor asciidoctor) {
		final ProjectReader reader = new ProjectReader();
		reader.setFunctionalitiesReader(FunctionalitiesReader.using(asciidoctor));
		return reader;
	}

	public static ProjectReader using(FunctionalitiesReader functionalitiesReader) {
		final ProjectReader reader = new ProjectReader();
		reader.setFunctionalitiesReader(functionalitiesReader);
		return reader;
	}

	private FunctionalitiesReader functionalitiesReader;

	private ProjectReader() {
		functionalitiesReader = null;
	}

	public Project asProject(File file) throws IllegalFormat, IOException, FileNotFoundException {
		checkState(functionalitiesReader != null);
		final Project project;
		try (InputStreamReader source = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
			final Instant queried = Instant.now();
			final Instant lastModified = Instant.ofEpochMilli(file.lastModified());
			project = asProject(CharStreams.toString(requireNonNull(source)), file.toURI(), lastModified, queried);
		}
		return project;
	}

	public Project asProject(String source, URI uri, Instant lastModification, Instant queried) throws IllegalFormat {
		requireNonNull(source);
		requireNonNull(uri);
		requireNonNull(lastModification);
		checkState(functionalitiesReader != null);
		functionalitiesReader.read(source);
		final List<Functionality> functionalities = functionalitiesReader.getFunctionalities();
		final String title = functionalitiesReader.getDoc().getAttribute("doctitle").toString();
		final Project project = Project.from(title, uri, functionalities, lastModification, queried);
		return project;
	}

	public List<Project> asProjects(Path path) throws IOException, IllegalFormat {
		checkState(functionalitiesReader != null);
		final List<Project> projects = new ArrayList<>();
		final File dir = path.toFile();
		checkArgument(dir.isDirectory());
		final List<File> files = Arrays.asList(dir.listFiles());
		Collections.sort(files);
		LOGGER.info("Found files: {}.", files);
		for (File file : files) {
			final Project project = asProject(file);
			projects.add(project);
		}
		return projects;
	}

	public FunctionalitiesReader getFunctionalitiesReader() {
		return functionalitiesReader;
	}

	public void setFunctionalitiesReader(FunctionalitiesReader functionalitiesReader) {
		this.functionalitiesReader = requireNonNull(functionalitiesReader);
	}

}
