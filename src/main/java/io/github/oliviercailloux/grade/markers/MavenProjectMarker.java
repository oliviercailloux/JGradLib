package io.github.oliviercailloux.grade.markers;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;

import com.google.common.primitives.Booleans;

import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.MavenManager;
import io.github.oliviercailloux.grade.contexters.PomContexter;
import io.github.oliviercailloux.grade.contexters.PomSupplier;
import io.github.oliviercailloux.java_grade.testers.MarkHelper;

public class MavenProjectMarker {

	private final FilesSource filesReader;
	private PomSupplier pomSupplier;
	private PomContexter pomContexter;
	private FilesSource testFiles;
	private Boolean testsExistAndPass;
	private Path projectDirectory;

	private MavenProjectMarker(FilesSource filesSource, Path projectDirectory) {
		this.projectDirectory = requireNonNull(projectDirectory);
		filesReader = requireNonNull(filesSource);
		pomSupplier = null;
		pomContexter = null;
		testFiles = null;
		testsExistAndPass = null;
	}

	public static MavenProjectMarker given(FilesSource filesReader, Path projectDirectory) {
		final MavenProjectMarker marker = new MavenProjectMarker(filesReader, projectDirectory);
		return marker;
	}

	public FilesSource getTestFiles() {
		if (testFiles == null) {
			testFiles = MarkHelper.getTestFiles(filesReader);
		}
		return testFiles;
	}

	public PomSupplier getPomSupplier() {
		if (pomSupplier == null) {
			pomSupplier = PomSupplier.basedOn(filesReader);
		}
		return pomSupplier;
	}

	public IGrade atRootGrade() {
		return Mark.given(Booleans.countTrue(getPomSupplier().isMavenProjectAtRoot()), "");
	}

	public PomContexter getPomContexter() {
		if (pomContexter == null) {
			pomContexter = new PomContexter(getPomSupplier().getContent());
			pomContexter.init();
		}
		return pomContexter;
	}

	public IGrade groupIdGrade() {
		return Mark.given(Booleans.countTrue(getPomContexter().isGroupIdValid()), "");
	}

	public boolean doTestsExistAndPass() {
		if (testsExistAndPass == null) {
			final MavenManager mavenManager = new MavenManager();
			testsExistAndPass = getTestFiles().getContents().keySet().stream().anyMatch(MarkHelper::isSurefireTestFile)
					&& pomSupplier.getMavenRelativeRoot().isPresent() && mavenManager.test(
							projectDirectory.resolve(pomSupplier.getMavenRelativeRoot().get().resolve("pom.xml")));
		}
		return testsExistAndPass;
	}

	public static MavenProjectMarker given(GitFullContext context) {
		final MavenProjectMarker marker = new MavenProjectMarker(context.getFilesReader(context.getMainCommit()),
				context.getClient().getProjectDirectory());
		return marker;
	}

}
