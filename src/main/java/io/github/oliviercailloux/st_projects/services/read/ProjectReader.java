package io.github.oliviercailloux.st_projects.services.read;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

import io.github.oliviercailloux.st_projects.model.Project;

public class ProjectReader {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectReader.class);

	private FunctionalitiesReader functionalitiesReader;

	public ProjectReader() {
		functionalitiesReader = new FunctionalitiesReader();
	}

	public Project asProject(File file) throws IllegalFormat, IOException, FileNotFoundException {
		final Project project;
		try (InputStreamReader source = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
			project = asProject(source, file.getName());
		}
		return project;
	}

	public Project asProject(Reader source, String originFileName) throws IllegalFormat, IOException {
		requireNonNull(source);
		final String baseName = Files.getNameWithoutExtension(requireNonNull(originFileName));
		checkArgument(!baseName.isEmpty());
		final Project project = new Project(baseName);
		functionalitiesReader.read(source);
		project.getFunctionalities().addAll(functionalitiesReader.getFunctionalities());
		final String title = functionalitiesReader.getDoc().getAttribute("doctitle").toString();
		if (!baseName.equals(title)) {
			throw new IllegalFormat("Read title: " + title + ".");
		}
		return project;
	}

	public List<Project> asProjects(Path path) throws IOException, IllegalFormat {
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
