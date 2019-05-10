package io.github.oliviercailloux.java_grade.ex_extractor;

import static com.google.common.base.Preconditions.checkState;
import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.AT_ROOT;
import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.COMMIT;
import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.COMPILES;
import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.GROUP_ID;
import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.IMPL;
import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.NO_MISLEADING_URL;
import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.ON_TIME;
import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.PDF_DEP;
import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.PREFIX;
import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.SIMPLE_EXTRACTOR;
import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.SOURCE;
import static io.github.oliviercailloux.java_grade.ex_extractor.ExExtractorCriterion.UTF;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.primitives.Booleans;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import io.github.oliviercailloux.git.Checkouter;
import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.AnonymousGrade;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CsvGrades;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GraderOrchestrator;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.contexters.MavenManager;
import io.github.oliviercailloux.grade.contexters.PomSupplier;
import io.github.oliviercailloux.grade.json.JsonGrade;
import io.github.oliviercailloux.grade.markers.MarkingPredicates;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.markers.MavenProjectMarker;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHub;
import io.github.oliviercailloux.java_grade.testers.MarkHelper;
import io.github.oliviercailloux.utils.Utils;
import io.github.oliviercailloux.y2019.extractor.SimpleExtractor;

public class ExExtractorGrader {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExExtractorGrader.class);
	private static final Instant DEADLINE = ZonedDateTime.parse("2019-05-06T16:24:00+02:00").toInstant();
	private static final Instant START = DEADLINE.minus(40, ChronoUnit.MINUTES);

	public static void main(String[] args) throws Exception {
		final String prefix = "extractor";
		final GraderOrchestrator orch = new GraderOrchestrator(prefix);
		final Path srcDir = Paths.get("../../Java L3/");
		orch.readUsernames(srcDir.resolve("usernamesGH-manual.json"));

		orch.readRepositories();

		final ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositories = orch.getRepositoriesByStudent();

		final ExExtractorGrader grader = new ExExtractorGrader();

		final ImmutableSet<Grade> grades = repositories.entrySet().stream()
				.map((e) -> Grade.of(e.getKey(), grader.grade(e.getValue()).getMarks().values()))
				.collect(ImmutableSet.toImmutableSet());

		Files.writeString(srcDir.resolve("all grades " + prefix + ".json"), JsonGrade.asJsonArray(grades).toString());
		Files.writeString(srcDir.resolve("all grades " + prefix + ".csv"), CsvGrades.asCsv(grades));
	}

	public ExExtractorGrader() {
		timeMark = null;
		mavenAbsoluteRoot = null;
		fullContext = null;
		commitsReceptionTime = null;
	}

	private Grade timeMark;
	Path mavenAbsoluteRoot;
	private GitFullContext fullContext;
	private ImmutableMap<ObjectId, Instant> commitsReceptionTime;

	public AnonymousGrade grade(RepositoryCoordinates coord) {
		mavenAbsoluteRoot = null;

		final ImmutableSet.Builder<Grade> gradeBuilder = ImmutableSet.builder();
		final Path projectsBaseDir = Paths.get("/home/olivier/Professions/Enseignement/En cours/extractor");

		final FullContextInitializer spec = (FullContextInitializer) FullContextInitializer.withPathAndIgnore(coord,
				projectsBaseDir, DEADLINE.plusSeconds(60));
		commitsReceptionTime = spec.getCommitsReceptionTime();
		fullContext = spec;
		final Optional<RevCommit> mainCommit = fullContext.getMainCommit();
		if (mainCommit.isPresent()) {
			final Checkouter co = Checkouter.aboutAndUsing(coord, projectsBaseDir);
			try {
				co.checkout(mainCommit.get());
			} catch (IOException | GitAPIException e) {
				throw new GradingException(e);
			}
		}

		final FilesSource filesReader = fullContext.getFilesReader(fullContext.getMainCommit());
		final MavenProjectMarker mavenMarker = MavenProjectMarker.given(fullContext);

		timeMark = Marks.timeMark(ON_TIME, fullContext, DEADLINE, this::getPenalty);
		gradeBuilder.add(timeMark);
		gradeBuilder.add(mavenMarker.atRootMark(AT_ROOT));
		gradeBuilder.add(mavenMarker.groupIdMark(GROUP_ID));
		gradeBuilder.add(commitMark());

		final PomSupplier pomSupplier = mavenMarker.getPomSupplier();
		mavenAbsoluteRoot = fullContext.getClient().getProjectDirectory()
				.resolve(pomSupplier.getForcedMavenRelativeRoot());
		final FilesSource pomMultiContent = pomSupplier.asMultiContent();
		gradeBuilder.add(Grade.binary(UTF,
				pomMultiContent.existsAndAllMatch(
						MarkingPredicates.containsOnce(Pattern.compile("<properties>" + Utils.ANY_REG_EXP
								+ "<project\\.build\\.sourceEncoding>UTF-8</project\\.build\\.sourceEncoding>"
								+ Utils.ANY_REG_EXP + "</properties>")))));
		gradeBuilder
				.add(Grade.binary(SOURCE,
						pomMultiContent.existsAndAllMatch(MarkingPredicates.containsOnce(Pattern.compile("<properties>"
								+ Utils.ANY_REG_EXP + "<maven\\.compiler\\.source>.*</maven\\.compiler\\.source>"
								+ Utils.ANY_REG_EXP + "</properties>")))));
		gradeBuilder.add(Grade.binary(NO_MISLEADING_URL,
				pomMultiContent.existsAndAllMatch(
						Predicates.contains(Pattern.compile("<url>.*\\.apache\\.org.*</url>")).negate())
						&& pomMultiContent.existsAndAllMatch(
								Predicates.contains(Pattern.compile("<url>.*example.*</url>")).negate())));
		gradeBuilder.add(Grade.binary(PDF_DEP,
				pomMultiContent.existsAndAllMatch(MarkingPredicates
						.containsOnce(Pattern.compile("<dependencies>" + Utils.ANY_REG_EXP + "<dependency>"
								+ Utils.ANY_REG_EXP + "<groupId>org\\.apache\\.pdfbox</groupId>" + Utils.ANY_REG_EXP
								+ "<artifactId>pdfbox</artifactId>" + Utils.ANY_REG_EXP + "<version>2\\.0\\.15"
								+ Utils.ANY_REG_EXP + "</version>" + "[^<]*" + "</dependency>")))));
		gradeBuilder.add(Grade.binary(SIMPLE_EXTRACTOR,
				!filesReader
						.filterOnPath(Predicate.isEqual(pomSupplier.getSrcMainJavaFolder()
								.resolve("io/github/oliviercailloux/y2019/extractor/SimpleExtractor.java")))
						.asFileContents().isEmpty()));
		gradeBuilder.add(
				Marks.packageGroupId(PREFIX, filesReader.filter((fc) -> !fc.getPath().endsWith("SimpleExtractor.java")),
						pomSupplier, mavenMarker.getPomContexter()));

		final boolean compile = new MavenManager().compile(mavenAbsoluteRoot.resolve("pom.xml"));
		gradeBuilder.add(Grade.binary(COMPILES, compile));
		gradeBuilder.add(writeMark());

		final ImmutableSet<Grade> grade = gradeBuilder.build();
		final Set<Criterion> diff = Sets.symmetricDifference(ImmutableSet.copyOf(ExExtractorCriterion.values()),
				grade.stream().map(Grade::getCriterion).collect(ImmutableSet.toImmutableSet())).immutableCopy();
		assert diff.isEmpty() : diff;
		return Grade.anonymous(grade);
	}

	Grade commitMark() {
		final Client client = fullContext.getClient();
		final Set<RevCommit> commits;
		try {
			commits = client.getAllCommits();
		} catch (IOException | GitAPIException e) {
			throw new IllegalStateException(e);
		}
		final ImmutableList<ZonedDateTime> commitDeclaredTimes = commits.stream().map(GitUtils::getCreationTime)
				.collect(ImmutableList.toImmutableList());
		LOGGER.debug("Times: {}.", commitDeclaredTimes);
		LOGGER.debug("Real times: {}.", commitsReceptionTime.values());
		final ImmutableList<RevCommit> commitsOwn = commits.stream()
				.filter((c) -> !c.getAuthorIdent().getName().equals("Olivier Cailloux"))
				.collect(ImmutableList.toImmutableList());
		LOGGER.info("All: {}; own: {}.", toOIds(commits), toOIds(commitsOwn));
		final ImmutableList<RevCommit> commitsEarly = commitsOwn.stream()
				.filter((c) -> commitsReceptionTime.get(c).isBefore(START.plus(30, ChronoUnit.MINUTES)))
				.collect(ImmutableList.toImmutableList());
		final Predicate<? super RevCommit> byGH = MarkHelper::committerIsGitHub;
		final ImmutableList<RevCommit> commitsManual = commitsOwn.stream().filter(byGH.negate())
				.collect(ImmutableList.toImmutableList());
		final String comment = (!commitsEarly.isEmpty() ? "Early enough: " + commitsEarly.iterator().next().getName()
				: "No commits early enough") + "; "
				+ (!commitsManual.isEmpty() ? "Using command line: " + commitsManual.iterator().next().getName()
						: "No commits using command line");
		final double points = (!commitsEarly.isEmpty() && !commitsManual.isEmpty()) ? COMMIT.getMaxPoints()
				: COMMIT.getMinPoints();
		final Grade commitMark = Grade.of(COMMIT, points, comment);
		return commitMark;
	}

	private ImmutableList<String> toOIds(Collection<RevCommit> commits) {
		return commits.stream().map(RevCommit::getName).collect(ImmutableList.toImmutableList());
	}

	Grade writeMark() {
		final ExExtractorCriterion criterion = IMPL;
		final Optional<SimpleExtractor> inst = newInstance();
		if (!inst.isPresent()) {
			return Grade.min(criterion, "Could not instanciate SimpleExtractor implementation.");
		}
		final SimpleExtractor instance = inst.get();
		LOGGER.info("Instantiated: {}.", instance.getClass().getName());

		final boolean testWhite;
		final boolean testNull;
		final boolean testNoSpuriousClose;
		final boolean testSimple;
		final boolean testThrows;
		final boolean testClosesOwn;
		final boolean testRestoredDefault;
		final boolean testExplicitStripper;
		final StringBuilder commentBuilder = new StringBuilder();

		try (InputStream input = getClass().getResourceAsStream("White.pdf")) {
			assert input != null;
			@SuppressWarnings("resource")
			final MyStringWriter output = new MyStringWriter();
			Throwable thrown = null;
			try {
				instance.writeText(input, output);
			} catch (Throwable t) {
				thrown = t;
			}
			final String obtained = output.toString();
			testWhite = obtained.equals("") && thrown == null;
			testNoSpuriousClose = !output.hasBeenClosed();
			commentBuilder.append("White: ");
			if (testWhite) {
				commentBuilder.append("Ok");
			} else if (thrown != null) {
				commentBuilder.append("KO – thrown " + thrown.toString());
			} else {
				commentBuilder.append(String.format("KO – obtained '%s'", obtained));
			}
			commentBuilder.append("\nDoes not close writer: ");
			if (testNoSpuriousClose) {
				commentBuilder.append("Ok");
			} else {
				commentBuilder.append("KO – writer has been closed.");
			}
		} catch (IOException e) {
			throw new GradingException(e);
		}
		try {
			final StringWriter output = new StringWriter();
			final InputStream input = null;
			instance.writeText(input, output);
			final String obtained = output.toString();
			testNull = obtained.equals("");
			commentBuilder.append("\nNull input: ");
			if (testNull) {
				commentBuilder.append("Ok");
			} else {
				commentBuilder.append(String.format("KO – obtained '%s'", obtained));
			}
		} catch (IOException e) {
			throw new GradingException(e);
		}
		try (InputStream input = getClass().getResourceAsStream("Hé.pdf")) {
			final StringWriter output = new StringWriter();
			Throwable thrown = null;
			try {
				instance.writeText(input, output);
			} catch (Throwable t) {
				thrown = t;
			}
			final String obtained = output.toString();
			testSimple = obtained.equals("Hé ̸=\n");
			commentBuilder.append("\nHé: ");
			if (testSimple) {
				commentBuilder.append("Ok");
			} else if (thrown != null) {
				commentBuilder.append("KO – thrown " + thrown.toString());
			} else {
				commentBuilder.append(String.format("KO – obtained '%s'", obtained));
			}
		} catch (IOException e) {
			throw new GradingException(e);
		}
		try (InputStream input = getClass().getResourceAsStream("Hé.pdf")) {
			final StringWriter output = new StringWriter();
			instance.setStripper(throwingStripper());
			boolean thrown = false;
			final int openBefore = MarkHelper.getOpenFileDescriptorCount();
			try {
				instance.writeText(input, output);
			} catch (IOException e) {
				assert e.getMessage().equals("Ad-hoc exception");
				thrown = true;
			}
			final int openAfter = MarkHelper.getOpenFileDescriptorCount();
			final String obtained = output.toString();
			if (!thrown && !obtained.toString().isEmpty()) {
				throw new GradingException(String.format("Did not throw, but yet obtained text: '%s'.", obtained));
			}
			testThrows = thrown;
			commentBuilder.append("\nThrows: ");
			if (testThrows) {
				commentBuilder.append("Ok");
			} else {
				commentBuilder.append(String.format("KO – obtained '%s'", obtained));
			}
			testClosesOwn = (openAfter == openBefore);
			commentBuilder.append("\nCloses own streams: ");
			if (testClosesOwn) {
				commentBuilder.append("Ok");
			} else {
				commentBuilder.append("KO");
			}
		} catch (IOException e) {
			throw new GradingException(e);
		}
		try (InputStream input = getClass().getResourceAsStream("Hé.pdf")) {
			final StringWriter output = new StringWriter();
			instance.setStripper(null);
			Throwable thrown = null;
			try {
				instance.writeText(input, output);
			} catch (Throwable t) {
				thrown = t;
			}
			final String obtained = output.toString();
			testRestoredDefault = obtained.equals("Hé ̸=\n");
			commentBuilder.append("\nRestored default: ");
			if (testRestoredDefault) {
				commentBuilder.append("Ok");
			} else if (thrown != null) {
				commentBuilder.append("KO – thrown " + thrown.toString());
			} else {
				commentBuilder.append(String.format("KO – obtained '%s'", obtained));
			}
		} catch (IOException e) {
			throw new GradingException(e);
		}
		try (InputStream input = getClass().getResourceAsStream("Hé.pdf")) {
			final StringWriter output = new StringWriter();
			instance.setStripper(new PDFTextStripper());
			Throwable thrown = null;
			try {
				instance.writeText(input, output);
			} catch (Throwable t) {
				thrown = t;
			}
			final String obtained = output.toString();
			testExplicitStripper = obtained.equals("Hé ̸=\n");
			commentBuilder.append("\nExplicit stripper: ");
			if (testExplicitStripper) {
				commentBuilder.append("Ok");
			} else if (thrown != null) {
				commentBuilder.append("KO – thrown " + thrown.toString());
			} else {
				commentBuilder.append(String.format("KO – obtained '%s'", obtained));
			}
		} catch (IOException e) {
			throw new GradingException(e);
		}

		double fracPoints = 0d;
		if ((testSimple || testExplicitStripper) && testNoSpuriousClose) {
			fracPoints += 1d / 8d;
		}
		if (testSimple && testWhite) {
			fracPoints += 1d / 8d;
		}
		if (testThrows && testClosesOwn) {
			if (testSimple) {
				fracPoints += 1d / 2d;
			} else if (testExplicitStripper) {
				fracPoints += 3d / 8d;
			}
		}
		if (testSimple && testThrows && testClosesOwn && testRestoredDefault) {
			fracPoints += 1d / 4d;
		} else if (testExplicitStripper && testThrows && testClosesOwn) {
			fracPoints += 1d / 8d;
		}
		final Grade mark = Grade.of(criterion,
				criterion.getMinPoints() + (criterion.getMaxPoints() - criterion.getMinPoints()) * fracPoints,
				commentBuilder.toString());
		return mark;
	}

	private PDFTextStripper throwingStripper() throws IOException {
		final IOException ioException = new IOException("Ad-hoc exception");
		return new PDFTextStripper() {
			@Override
			public String getText(PDDocument doc) throws IOException {
				throw ioException;
			}

			@Override
			public void writeText(PDDocument doc, Writer outputStream) throws IOException {
				throw ioException;
			}

			@Override
			public void showTextString(byte[] string) throws IOException {
				throw ioException;
			}

			@Override
			public void showTextStrings(COSArray array) throws IOException {
				throw ioException;
			}

		};
	}

	double getPenalty(Duration tardiness) {
		final double maxGrade = Stream.of(ExExtractorCriterion.values())
				.collect(Collectors.summingDouble(Criterion::getMaxPoints));

		LOGGER.debug("Tardiness: {}.", tardiness);
		final long secondsLate = tardiness.toSeconds();
		return -0.05d / 20d * maxGrade * secondsLate;
	}

	private Optional<Class<?>> getNamedClass(String simpleName) {
		requireNonNull(simpleName);
		URL url;
		try {
			url = mavenAbsoluteRoot.resolve("target/classes").toUri().toURL();
		} catch (MalformedURLException e) {
			throw new AssertionError(e);
		}
		final Class<?> classToLoad;
		try (URLClassLoader child = new URLClassLoader(new URL[] { url }, this.getClass().getClassLoader())) {
			final ClassGraph graph = new ClassGraph().overrideClassLoaders(child).enableAllInfo()
					.whitelistPackages("*");
			final String name;
			try (ScanResult scanResult = graph.scan()) {
				final ClassInfoList classes = scanResult.getAllStandardClasses();
				LOGGER.debug("Size all: {}.", classes.size());
				final ImmutableList<ClassInfo> extractorUserClasses = classes.stream()
						.filter((i) -> i.getName().endsWith("." + simpleName)).collect(ImmutableList.toImmutableList());
//				for (ClassInfo info : classes) {
//					LOGGER.info("Name: {}.", info.getName());
//				}
				if (extractorUserClasses.size() >= 2) {
					throw new GradingException("Multiple impl.");
				}
				if (extractorUserClasses.size() == 1) {
					final ClassInfo classInfo = extractorUserClasses.iterator().next();
					name = classInfo.getName();
					checkState(classInfo.getSimpleName().equals(simpleName));
				} else {
					name = "";
				}
			}

			final Optional<Class<?>> opt;
			if (name.equals("")) {
				opt = Optional.empty();
			} else {
				classToLoad = Class.forName(name, true, child);
				LOGGER.debug("Class: {}.", classToLoad.getCanonicalName());
				opt = Optional.of(classToLoad);
			}
			return opt;
		} catch (IOException | SecurityException | ClassNotFoundException e) {
			throw new GradingException(e);
		}
	}

	private Grade userWriteToFileMark() {
		final Optional<Class<?>> namedClass = getNamedClass("ExtractorUser");
		if (namedClass.isEmpty()) {
			return Grade.min(ON_TIME);
		}

		final String methodName = "writeTextToFile";
		final Class<?> cls = namedClass.get();
		final Optional<Method> methodOpt = getMethod(cls, methodName);

		final boolean writtenOk;
		final boolean throwsIOE;
		final String comment;
		if (methodOpt.isPresent()) {
			final Method method = methodOpt.get();
			final ImmutableSet<Class<?>> exc = ImmutableSet.copyOf(method.getExceptionTypes());
			throwsIOE = exc.contains(IOException.class);
			try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
				final Path outPath = fs.getPath("out.txt");
				try (InputStream input = getClass().getResourceAsStream("Hé.pdf")) {
					method.invoke(method, input, outPath);
				}
				if (!Files.exists(outPath)) {
					writtenOk = false;
				} else {
					final String written = Files.readString(outPath);
					writtenOk = written.equals("Hé ≠\n");
					if (!writtenOk) {
						throw new GradingException(String.format("Written, but not the right content: '%s'", written));
					}
				}
			} catch (IOException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new IllegalStateException(e);
			}
			comment = (writtenOk ? "" : "No file written; ")
					+ (throwsIOE ? "IOException declared ok" : "Fails to declare IOException");
		} else {
			writtenOk = false;
			throwsIOE = false;
			comment = "Method ExtractorUser#" + methodName + " not found";
		}

		return Grade.proportional(ON_TIME, Booleans.countTrue(writtenOk, throwsIOE), 2, comment);

	}

	private Optional<Method> getMethod(Class<?> cls, String methodName) {
		final Method[] methods = cls.getMethods();
		final ImmutableList<Method> writeFileMethods = Stream.of(methods).filter((m) -> m.getName().equals(methodName))
				.collect(ImmutableList.toImmutableList());
		if (writeFileMethods.size() >= 2) {
			throw new GradingException("Multiple methods.");
		}
		final Optional<Method> methodOpt;
		if (writeFileMethods.size() == 1) {
			methodOpt = Optional.of(writeFileMethods.iterator().next());
		} else {
			methodOpt = Optional.empty();
		}
		return methodOpt;
	}

	private Optional<SimpleExtractor> newInstance() {
		LOGGER.debug("Start new inst.");
		URL url;
		try {
			url = mavenAbsoluteRoot.resolve("target/classes").toUri().toURL();
		} catch (MalformedURLException e) {
			throw new AssertionError(e);
		}
		try (URLClassLoader child = new URLClassLoader(new URL[] { url }, this.getClass().getClassLoader())) {
			final ClassGraph graph = new ClassGraph().overrideClassLoaders(child).enableAllInfo()
					.whitelistPackages("*");
			final String name;
			try (ScanResult scanResult = graph.scan()) {
				final ClassInfoList impl = scanResult.getClassesImplementing(SimpleExtractor.class.getName());
				if (impl.size() >= 2) {
					throw new GradingException("Multiple impl.");
				}
				if (impl.size() == 1) {
					name = impl.iterator().next().getName();
				} else {
					name = "";
				}
				final ClassInfoList cls = scanResult.getAllClasses();
				LOGGER.debug("Size all: {}.", cls.size());
				for (ClassInfo info : cls) {
					LOGGER.debug("Name: {}.", info.getName());
				}
			}
			if (name.equals("")) {
				return Optional.empty();
			}
			/**
			 * TODO probably doesn’t work when loading a second class having the same name?
			 */
			final Class<? extends SimpleExtractor> classToLoad = Class.forName(name, true, child)
					.asSubclass(SimpleExtractor.class);
			LOGGER.debug("Class: {}.", classToLoad.getCanonicalName());
			final Constructor<? extends SimpleExtractor> constructor = classToLoad.getConstructor();
			final SimpleExtractor created = constructor.newInstance();
			return Optional.of(created);
		} catch (IOException | NoSuchMethodException | SecurityException | ClassNotFoundException
				| InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new GradingException(e);
		}
	}
}
