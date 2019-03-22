package io.github.oliviercailloux.grade.markers;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Predicates;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.MavenManager;
import io.github.oliviercailloux.grade.contexters.PomContexter;
import io.github.oliviercailloux.grade.contexters.PomSupplier;
import io.github.oliviercailloux.java_grade.testers.TestFileRecognizer;

public class MavenProjectMarker {

	private final GitFullContext context;
	private final FilesSource filesReader;
	private PomSupplier pomSupplier;
	private PomContexter pomContexter;
	private FilesSource testFiles;
	private Boolean testsExistAndPass;

	public MavenProjectMarker(GitFullContext context) {
		this.context = requireNonNull(context);
		filesReader = context.getMainFilesReader();
		pomSupplier = null;
		pomContexter = null;
		testFiles = null;
		testsExistAndPass = null;
	}

	public static MavenProjectMarker given(GitFullContext context) {
		final MavenProjectMarker marker = new MavenProjectMarker(context);
		return marker;
	}

	public FilesSource getTestFiles() {
		if (testFiles == null) {
			testFiles = TestFileRecognizer.getTestFiles(filesReader);
		}
		return testFiles;
	}

	public PomSupplier getPomSupplier() {
		if (pomSupplier == null) {
			pomSupplier = PomSupplier.basedOn(filesReader);
		}
		return pomSupplier;
	}

	public Mark atRootMark(Criterion criterion) {
		return Mark.binary(criterion, getPomSupplier().isMavenProjectAtRoot());
	}

	public PomContexter getPomContexter() {
		if (pomContexter == null) {
			pomContexter = new PomContexter(getPomSupplier().getContent());
			pomContexter.init();
		}
		return pomContexter;
	}

	public Mark groupIdMark(Criterion criterion) {
		return Mark.binary(criterion, getPomContexter().isGroupIdValid());
	}

	/**
	 * The project must be checked out at the version to be tested, at the path
	 * indicated by the project directory of the client.
	 */
	public Mark testMark(Criterion criterion) {
		return Mark.binary(criterion, testsExistAndPass);
	}

	public boolean doTestsExistAndPass() {
		if (testsExistAndPass == null) {
			final MavenManager mavenManager = new MavenManager();
			testsExistAndPass = getTestFiles().getContents().keySet().stream()
					.anyMatch(TestFileRecognizer::isSurefireTestFile) && pomSupplier.getMavenRelativeRoot().isPresent()
					&& mavenManager.test(context.getClient().getProjectDirectory()
							.resolve(pomSupplier.getMavenRelativeRoot().get().resolve("pom.xml")));
		}
		return testsExistAndPass;
	}

}
