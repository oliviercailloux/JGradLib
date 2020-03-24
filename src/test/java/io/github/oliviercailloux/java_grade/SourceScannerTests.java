package io.github.oliviercailloux.java_grade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.common.jimfs.Jimfs;

public class SourceScannerTests {
	@Test
	void testPackage() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem()) {
			final Path ploum = fs.getPath("Ploum.java");
			Files.writeString(ploum, "package my_package ;\npublic class Ploum {}");
			assertEquals("my_package", SourceScanner.asSourceClass(ploum).getPackageName());

			final Path plum = fs.getPath("Plum.java");
			Files.writeString(plum, "//package my_package;\npublic class Plum {}");
			assertEquals("", SourceScanner.asSourceClass(plum).getPackageName());

			final Path plom = fs.getPath("Plom.java");
			Files.writeString(plom, "public class Plom {}");
			assertEquals("", SourceScanner.asSourceClass(plom).getPackageName());
		}
	}
}
