package io.github.oliviercailloux.st_projects.services.read;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.io.Files;

import io.github.oliviercailloux.st_projects.model.Project;

public class ProjectReader {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectReader.class);

	private final FunctionalitiesReader functionalitiesReader;

	public ProjectReader() {
		functionalitiesReader = new FunctionalitiesReader();
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
		final List<Project> projects = Lists.newLinkedList();
		final List<File> files = Arrays.asList(path.toFile().listFiles());
		Collections.sort(files);
		LOGGER.info("Found files: {}.", files);
		for (File file : files) {
			try (InputStreamReader source = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
				projects.add(asProject(source, file.getName()));
			}
		}
		return projects;
	}

}
