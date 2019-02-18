package io.github.oliviercailloux.grade.contexters;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.oliviercailloux.grade.context.GitContext;

public class GitToSourcer {

	public static String given(GitContext context, Path path) {
		return GitAndBaseToSourcer.given(context, Paths.get(""), path);
	}
}
