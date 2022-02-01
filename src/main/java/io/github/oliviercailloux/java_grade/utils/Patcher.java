package io.github.oliviercailloux.java_grade.utils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Patch;
import io.github.oliviercailloux.grade.format.json.JsonCriterion;
import io.github.oliviercailloux.grade.format.json.JsonbGrade;
import io.github.oliviercailloux.json.JsonbUtils;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class Patcher {
	public static void main(String[] args) throws Exception {
		final String prefix = "admin-manages-users";

		@SuppressWarnings("serial")
		final Type typePatch = new LinkedHashMap<String, LinkedHashSet<Patch>>() {
		}.getClass().getGenericSuperclass();
		final Map<String, Set<Patch>> patches = JsonbUtils.fromJson(Files.readString(Path.of(prefix + " patches.json")),
				typePatch, JsonbGrade.instance(), JsonCriterion.instance());

		@SuppressWarnings("serial")
		final Type type = new LinkedHashMap<String, IGrade>() {
		}.getClass().getGenericSuperclass();

		final Map<String, IGrade> grades = JsonbUtils.fromJson(Files.readString(Path.of("grades " + prefix + ".json")),
				type, JsonbGrade.instance());

		final ImmutableMap<String, Set<Patch>> completedPatches = grades.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey,
						e -> patches.containsKey(e.getKey()) ? patches.get(e.getKey()) : ImmutableSet.of()));

		final ImmutableMap<String, IGrade> patched = grades.entrySet().stream().collect(ImmutableMap
				.toImmutableMap(Map.Entry::getKey, e -> e.getValue().withPatches(completedPatches.get(e.getKey()))));

		Files.writeString(Path.of("grades " + prefix + " patched.json"),
				JsonbUtils.toJsonObject(patched, JsonCriterion.instance(), JsonbGrade.instance()).toString());
	}
}
