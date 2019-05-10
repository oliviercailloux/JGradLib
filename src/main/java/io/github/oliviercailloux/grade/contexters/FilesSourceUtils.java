package io.github.oliviercailloux.grade.contexters;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.collect.MoreCollectors;

import io.github.oliviercailloux.grade.context.FilesSource;

public class FilesSourceUtils {

	public static String getSingleContent(FilesSource source) {
		return source.asFileContents().size() != 1 ? ""
				: source.asFileContents().stream().collect(MoreCollectors.onlyElement()).getContent();
	}

	public static Path getSinglePath(FilesSource source) {
		return source.asFileContents().size() != 1 ? Paths.get("")
				: source.asFileContents().stream().collect(MoreCollectors.onlyElement()).getPath();
	}

}
