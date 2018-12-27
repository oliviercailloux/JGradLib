package io.github.oliviercailloux.st_projects.ex2;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.GradeCriterion;
import io.github.oliviercailloux.st_projects.model.StudentGrade;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;
import io.github.oliviercailloux.st_projects.services.grading.ContextInitializer;
import io.github.oliviercailloux.st_projects.services.grading.CriterionGrader;
import io.github.oliviercailloux.st_projects.services.grading.SimpleGrader;
import io.github.oliviercailloux.st_projects.services.grading.TimeGrader;

public class Ex2Grader {
	private StudentOnGitHub student;
	private ImmutableSet.Builder<CriterionGrade<Ex2Criterion>> gradesBuilder;

	public Ex2Grader() {
		gradesBuilder = null;
		student = null;
		deadline = Instant.MAX;
		pomContent = null;
		mavenManager = new MavenManager();
		context = null;
	}

	private void grade() throws IOException {
		final String groupId;
		final List<String> groupIdElements;
		pomContent = read(Paths.get("pom.xml"));
		final String servletContent;

		final TimeGrader<Ex2Criterion> grader = TimeGrader.given(Ex2Criterion.ON_TIME, context, deadline, MAX_GRADE);
		putGrade(grader.grade());

		final CriterionGrader<GradeCriterion> repoGrader = SimpleGrader.using(context, (c) -> {
			final Client client = c.getClient();

			{
				final CriterionGrade<Ex2Criterion> grade;
				if (!client.existsCached()) {
					grade = CriterionGrade.zero(Ex2Criterion.REPO_EXISTS, "Repository not found");
				} else if (!client.hasContentCached()) {
					grade = CriterionGrade.zero(Ex2Criterion.REPO_EXISTS, "Repository found but is empty");
				} else if (!c.getMainCommit().isPresent()) {
					grade = CriterionGrade.zero(Ex2Criterion.REPO_EXISTS,
							"Repository found with content but no suitable commit found");
				} else {
					grade = CriterionGrade.max(Ex2Criterion.REPO_EXISTS);
				}
				return grade;
			}
		});
		putGrade(repoGrader.grade());

		final Client client = context.getClient();
		{
			final Matcher matcher = Pattern.compile("<groupId>(io\\.github\\..*)</groupId>").matcher(pomContent);
			final boolean found = matcher.find();
			if (found) {
				groupId = matcher.group(1);
				groupIdElements = Arrays.asList(groupId.split("\\."));
				assert groupIdElements.size() >= 1 : groupId;
			} else {
				groupId = null;
				groupIdElements = null;
			}
			binaryCriterion(Ex2Criterion.GROUP_ID, () -> found);
		}
		{
			final boolean found = Pattern
					.compile("<dependencies>" + ANY + "<dependency>" + ANY + "<groupId>org\\.mnode\\.ical4j</groupId>"
							+ ANY + "<artifactId>ical4j</artifactId>" + ANY + "<version>3\\.0\\.2</version>")
					.matcher(pomContent).find();
			binaryCriterion(Ex2Criterion.ICAL, () -> found);
		}
		{
			final boolean found = Pattern.compile("<properties>" + ANY
					+ "<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>" + ANY + "</properties>")
					.matcher(pomContent).find();
			binaryCriterion(Ex2Criterion.UTF, () -> found);
		}
		{
			final boolean found = Pattern.compile(
					"<properties>" + ANY + "<maven.compiler.source>.*</maven.compiler.source>" + ANY + "</properties>")
					.matcher(pomContent).find();
			binaryCriterion(Ex2Criterion.SOURCE, () -> found);
		}
		{
			final boolean found = Pattern.compile("<url>.*\\.apache\\.org.*</url>").matcher(pomContent).find();
			binaryCriterion(Ex2Criterion.NO_MISLEADING_URL, () -> !found, -1d);
		}
		{
			final boolean found = Pattern.compile("<packaging>war</packaging>").matcher(pomContent).find();
			binaryCriterion(Ex2Criterion.WAR, () -> found);
		}
		{
			if (groupIdElements == null) {
				putGrade(CriterionGrade.zero(Ex2Criterion.PREFIX, "Unknown group id"));
			} else {
				Path currentSegment = Paths.get("src/main/java");
				boolean allMatch = true;
				for (String element : groupIdElements) {
					LOGGER.debug("Checking for element {}.", element);
					final Path current = client.getProjectDirectory().resolve(currentSegment);
					final Path nextSegment = currentSegment.resolve(element);
					final Path next = client.getProjectDirectory().resolve(nextSegment);
					final boolean onlyRightName = Files.list(current).allMatch(Predicate.isEqual(next));
					if (!onlyRightName) {
						allMatch = false;
						break;
					}
					currentSegment = nextSegment;
				}
				final boolean allMatchFinal = allMatch;
				binaryCriterion(Ex2Criterion.PREFIX, () -> allMatchFinal);
			}
		}
		{
			final boolean compile = mavenManager.compile(client.getProjectDirectory().resolve("pom.xml"));
			binaryCriterion(Ex2Criterion.COMPILE, () -> compile);
		}
		{
			final Set<Path> sources;
			if (!client.hasContentCached()) {
				sources = ImmutableSet.of();
			} else {
				final String mavenStart = "src/main/java";
				final Path start = client.getProjectDirectory().resolve(mavenStart);
				if (!Files.isDirectory(start)) {
					LOGGER.info("No directory " + mavenStart + ".");
					sources = ImmutableSet.of();
				} else {
					sources = Files.walk(start)
							.filter((p) -> p.getFileName().equals(Paths.get("AdditionerServlet.java")))
							.collect(Collectors.toSet());
					LOGGER.debug("Sources: {}.", sources);
				}
			}
			final boolean found;
			final String comment;
			if (sources.size() == 1) {
				final Path servlet = sources.iterator().next();
				servletContent = read(servlet);
				found = Pattern.compile("void\\s*doGet\\s*\\(\\s*(final)?\\s*HttpServletRequest .*\\)")
						.matcher(servletContent).find();
				comment = "";
			} else {
				servletContent = "";
				found = false;
				if (sources.isEmpty()) {
					comment = "AdditionerServlet not found.";
				} else {
					comment = "Found " + sources.size() + " AdditionerServlet: " + sources + ".";
				}
			}
			binaryCriterion(Ex2Criterion.DO_GET, found, 0, comment);
		}
		{
			final boolean found = Pattern.compile("Exécution impossible").matcher(servletContent).find();
			binaryCriterion(Ex2Criterion.SRC_ENC, () -> found);
		}
		{
			final boolean foundAuto = Pattern.compile("Auto-generated").matcher(servletContent).find();
			final boolean foundSee = Pattern.compile("@see HttpServlet#doGet").matcher(servletContent).find();
			if (foundAuto || foundSee) {
				putGrade(CriterionGrade.of(Ex2Criterion.AUTO, -1d, ""));
			} else {
				putGrade(CriterionGrade.of(Ex2Criterion.AUTO, 0, ""));
			}
		}
		{
			final boolean found = Pattern.compile("printStackTrace").matcher(servletContent).find();
			binaryCriterion(Ex2Criterion.EXC, () -> !found);
		}
		{
			final boolean found = Pattern.compile("setCharacterEncoding").matcher(servletContent).find();
			binaryCriterion(Ex2Criterion.ENC, () -> found);
		}
		{
			final boolean found = Pattern.compile("setLocale.+FRENCH").matcher(servletContent).find();
			binaryCriterion(Ex2Criterion.LOC, () -> found);
		}
		{
			final boolean found = Pattern.compile("setContentType.+PLAIN").matcher(servletContent).find();
			binaryCriterion(Ex2Criterion.MTYPE, () -> found);
		}
		{
			final boolean found = Pattern.compile("@WebServlet.*\\(.*/add\".*\\)").matcher(servletContent).find();
			binaryCriterion(Ex2Criterion.ANNOT, () -> found);
		}
		{
			final boolean finalFound = Pattern
					.compile("<build>" + ANY + "<finalName>additioner</finalName>" + ANY + "</build>")
					.matcher(pomContent).find();
			final boolean cRootFound = Pattern.compile("<context-root>additioner</context-root>")
					.matcher(read(Paths.get("src/main/webapp/WEB-INF/jboss-web.xml"))).find();
			final boolean cRootIncorrectFound = Pattern.compile("<context-root>/additioner</context-root>")
					.matcher(read(Paths.get("src/main/webapp/WEB-INF/jboss-web.xml"))).find();
			Ex2Criterion criterion = Ex2Criterion.FINAL_NAME;

			final CriterionGrade<Ex2Criterion> thisGrade;
			if (finalFound) {
				thisGrade = CriterionGrade.max(criterion);
			} else if (cRootFound) {
				thisGrade = CriterionGrade.of(criterion, criterion.getMaxPoints() / 2d, "Non-portable solution");
			} else if (cRootIncorrectFound) {
				thisGrade = CriterionGrade.of(criterion, criterion.getMaxPoints() / 4d,
						"Non-portable solution and incorrect format");
			} else {
				thisGrade = CriterionGrade.zero(criterion);
			}
			putGrade(thisGrade);
		}
		{
			final boolean sendError = Pattern.compile("sendError\\(.*SC_BAD_REQUEST.*,.*\\)").matcher(servletContent)
					.find();
			final boolean setStatus = Pattern.compile("setStatus\\(.*SC_BAD_REQUEST.*\\)").matcher(servletContent)
					.find();
			final boolean sendError400 = Pattern.compile("sendError\\(.*400.*,.*\\)").matcher(servletContent).find();
			final boolean setStatus400 = Pattern.compile("setStatus\\(.*400.*\\)").matcher(servletContent).find();
			Ex2Criterion criterion = Ex2Criterion.ERROR_STATUS;
			final CriterionGrade<Ex2Criterion> thisGrade;
			if (sendError || setStatus) {
				thisGrade = CriterionGrade.max(criterion);
			} else if (sendError400 || setStatus400) {
				thisGrade = CriterionGrade.of(criterion, criterion.getMaxPoints() / 2d,
						"Error sent with literal constant instead of symbolic constant.");
			} else {
				thisGrade = CriterionGrade.of(criterion, 0d, "");
			}
			putGrade(thisGrade);
		}
		{
			final boolean getParamFound = Pattern.compile("\\.getParameter\\s*\\(.*\\)").matcher(servletContent).find();
//			final boolean queryFound = Pattern.compile("getQueryString\\s*\\(\\)").matcher(servletContent).find();
			binaryCriterion(Ex2Criterion.PARAM, () -> getParamFound);
		}
//		{
//		ignoreContent = read(Paths.get(".gitignore"));
//			final boolean classpathFound = Pattern.compile(".classpath").matcher(ignoreContent).find();
//			final boolean settingsFound = Pattern.compile(".settings/").matcher(ignoreContent).find();
//			final boolean projectFound = Pattern.compile(".project").matcher(ignoreContent).find();
//			final boolean classFound = Pattern.compile("(.class)|(target/)").matcher(ignoreContent).find();
//			final double points = ImmutableList.of(classpathFound, settingsFound, projectFound, classFound).stream()
//					.collect(Collectors.summingDouble((b) -> b ? Ex2Criterion.IGNORE.getMaxPoints() / 4 : 0));
//			putGrade(SingleGrade.of(Ex2Criterion.IGNORE, points, ""));
//		}
		{
			final ObjectId master = client.resolve("master");
			final Optional<AnyObjectId> classpathId = client.getBlobId(master, Paths.get(".classpath"));
			final Optional<AnyObjectId> settingsId = client.getBlobId(master, Paths.get(".settings/"));
			final Optional<AnyObjectId> projectId = client.getBlobId(master, Paths.get(".project"));
			final Optional<AnyObjectId> targetId = client.getBlobId(master, Paths.get("target/"));
			LOGGER.debug("Found settings? {}.", settingsId);
			final double points = ImmutableList.of(classpathId, settingsId, projectId, targetId).stream().collect(
					Collectors.summingDouble((o) -> !o.isPresent() ? Ex2Criterion.ONLY_ORIG.getMaxPoints() / 4 : 0));
			putGrade(CriterionGrade.of(Ex2Criterion.ONLY_ORIG, points, ""));
		}

		// TODO check that eclipse and .class files are not in the repo
	}

	private String read(Path relativePath) throws IOException {
		final Client client = context.getClient();

		if (!client.hasContentCached()) {
			return "";
		}
		final String content;
		final Path fullPath = client.getProjectDirectory().resolve(relativePath);
		if (!Files.exists(fullPath)) {
			content = "";
			LOGGER.info("Not found: {}.", relativePath);
		} else {
			content = new String(Files.readAllBytes(fullPath), StandardCharsets.UTF_8);
			LOGGER.debug("Read from {}: {}.", relativePath, content);
		}
		return content;
	}

	private void binaryCriterion(Ex2Criterion criterion, Supplier<Boolean> tester) {
		binaryCriterion(criterion, tester, 0d);
	}

	private void binaryCriterion(Ex2Criterion criterion, Supplier<Boolean> tester, double failPoints) {
		binaryCriterion(criterion, tester.get(), failPoints, "");
	}

	private void binaryCriterion(Ex2Criterion criterion, boolean result, double failPoints, String commentIfFail) {
		final CriterionGrade<Ex2Criterion> thisGrade;
		if (result) {
			thisGrade = CriterionGrade.max(criterion);
		} else {
			thisGrade = CriterionGrade.of(criterion, failPoints, commentIfFail);
		}
		putGrade(thisGrade);
	}

	private void putGrade(CriterionGrade<Ex2Criterion> grade) {
		requireNonNull(grade);
		gradesBuilder.add(grade);
	}

	public void setIgnoreAfter(Instant ignoreAfter) {
		this.ignoreAfter = requireNonNull(ignoreAfter);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Ex2Grader.class);
	private String pomContent;
	private final MavenManager mavenManager;
	private Instant deadline;
	private Instant ignoreAfter;
	private static final double MAX_GRADE = 20d;

	private static final String ANY = "(.|\\v)*";
	private ContextInitializer context;

	public StudentGrade<Ex2Criterion> grade(RepositoryCoordinates coordinates,
			@SuppressWarnings("hiding") StudentOnGitHub student) throws GitAPIException, IOException {
		setContextInitializer(ContextInitializer.ignoreAfter(ignoreAfter));
		this.student = requireNonNull(student);

		gradesBuilder = ImmutableSet.builder();
		context.init(coordinates);
		grade();
//		return Grade.of(student, new HashSet<>(gradesBuilder.build().values()));
		return StudentGrade.of(student, gradesBuilder.build());
	}

	public void setContextInitializer(final ContextInitializer contextInitializer) {
		context = contextInitializer;
	}

	public void setDeadline(Instant deadline) {
		this.deadline = requireNonNull(deadline);
	}
}
