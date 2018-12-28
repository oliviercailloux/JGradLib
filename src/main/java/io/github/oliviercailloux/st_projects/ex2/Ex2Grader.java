package io.github.oliviercailloux.st_projects.ex2;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.StudentGrade;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;
import io.github.oliviercailloux.st_projects.services.grading.BoxSupplier;
import io.github.oliviercailloux.st_projects.services.grading.ContextInitializer;
import io.github.oliviercailloux.st_projects.services.grading.CriterionGrader;
import io.github.oliviercailloux.st_projects.services.grading.GitGrader;
import io.github.oliviercailloux.st_projects.services.grading.GitToMultipleSourcer;
import io.github.oliviercailloux.st_projects.services.grading.GitToSourcer;
import io.github.oliviercailloux.st_projects.services.grading.Graders;
import io.github.oliviercailloux.st_projects.services.grading.GradingException;
import io.github.oliviercailloux.st_projects.services.grading.PomContexter;
import io.github.oliviercailloux.st_projects.services.grading.TimeGrader;

public class Ex2Grader {
	private StudentOnGitHub student;
	private ImmutableSet.Builder<CriterionGrade> gradesBuilder;

	public Ex2Grader() {
		gradesBuilder = null;
		student = null;
		deadline = Instant.MAX;
		context = null;
		coordinatesSupplier = new BoxSupplier();
	}

	private void grade(RepositoryCoordinates coordinates) throws IOException, GradingException {
		MutableGraph<Object> g = GraphBuilder.directed().build();
		g.addNode(context);
		final TimeGrader timeGrader = TimeGrader.given(Ex2Criterion.ON_TIME, context, deadline, MAX_GRADE);
		g.putEdge(context, timeGrader);
		final CriterionGrader repoGrader = GitGrader.repoGrader(Ex2Criterion.REPO_EXISTS, context);
		g.putEdge(context, repoGrader);
		final GitToSourcer pomSupplier = new GitToSourcer(context, Paths.get("pom.xml"));
		g.putEdge(context, pomSupplier);
		final PomContexter pomContext = new PomContexter(pomSupplier);
		g.putEdge(pomSupplier, pomContext);
		final CriterionGrader pomGrader = Graders.groupIdGrader(Ex2Criterion.GROUP_ID, pomContext);
		g.putEdge(pomContext, pomGrader);
		final CriterionGrader icalGrader;
		{
			final Pattern pattern = Pattern
					.compile("<dependencies>" + ANY + "<dependency>" + ANY + "<groupId>org\\.mnode\\.ical4j</groupId>"
							+ ANY + "<artifactId>ical4j</artifactId>" + ANY + "<version>3\\.0\\.2</version>");
			icalGrader = Graders.predicateGrader(Ex2Criterion.ICAL, pomSupplier, Graders.containsOnce(pattern));
		}
		g.putEdge(pomSupplier, icalGrader);
		final CriterionGrader utfGrader;
		{
			final Pattern pattern = Pattern.compile("<properties>" + ANY
					+ "<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>" + ANY + "</properties>");
			utfGrader = Graders.predicateGrader(Ex2Criterion.UTF, pomSupplier, Graders.containsOnce(pattern));
		}
		g.putEdge(pomSupplier, utfGrader);
		final CriterionGrader sourceGrader;
		{
			final Pattern pattern = Pattern.compile(
					"<properties>" + ANY + "<maven.compiler.source>.*</maven.compiler.source>" + ANY + "</properties>");
			sourceGrader = Graders.predicateGrader(Ex2Criterion.SOURCE, pomSupplier, Graders.containsOnce(pattern));
		}
		g.putEdge(pomSupplier, sourceGrader);
		final CriterionGrader urlGrader;
		{
			final Pattern pattern = Pattern.compile("<url>.*\\.apache\\.org.*</url>");
			urlGrader = Graders.predicateGrader(Ex2Criterion.NO_MISLEADING_URL, pomSupplier,
					Predicates.contains(pattern).negate(), 0d, -1d);
		}
		g.putEdge(pomSupplier, urlGrader);
		final CriterionGrader warGrader;
		{
			final Pattern pattern = Pattern.compile("<packaging>war</packaging>");
			warGrader = Graders.predicateGrader(Ex2Criterion.WAR, pomSupplier, Graders.containsOnce(pattern));
		}
		g.putEdge(pomSupplier, warGrader);
		final CriterionGrader prefixGrader = Graders.packageGroupIdGrader(Ex2Criterion.PREFIX, context, pomContext);
		g.putEdge(context, prefixGrader);
		g.putEdge(pomContext, prefixGrader);
		final CriterionGrader mavenCompileGrader = Graders.mavenCompileGrader(Ex2Criterion.COMPILE, context);
		final GitToMultipleSourcer servletSourcer = new GitToMultipleSourcer(context, Paths.get("src/main/java"),
				(p) -> p.getFileName().equals(Paths.get("AdditionerServlet.java")));
		final CriterionGrader doGetGrader = Graders.predicateGraderWithComment(Ex2Criterion.DO_GET, servletSourcer,
				Graders.containsOnce(Pattern.compile("void\\s*doGet\\s*\\(\\s*(final)?\\s*HttpServletRequest .*\\)")));
		final CriterionGrader encGrader = Graders.predicateGrader(Ex2Criterion.SRC_ENC, servletSourcer,
				Predicates.contains(Pattern.compile("Exécution impossible")));

		coordinatesSupplier.set(coordinates);

		context.clear();
		context.init();
		putGrade(timeGrader.grade());
		putGrade(repoGrader.grade());

		pomSupplier.clear();
		pomSupplier.init();
		pomContext.clear();
		pomContext.init();

		putGrade(pomGrader.grade());
		putGrade(icalGrader.grade());
		putGrade(utfGrader.grade());
		putGrade(sourceGrader.grade());
		putGrade(urlGrader.grade());
		putGrade(warGrader.grade());
		putGrade(prefixGrader.grade());
		putGrade(mavenCompileGrader.grade());

		servletSourcer.clear();
		servletSourcer.init();

		putGrade(doGetGrader.grade());
		putGrade(encGrader.grade());

		final Client client = context.getClient();
		final String servletContent = servletSourcer.getContent();

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
			final String pomContent = pomSupplier.getContent();
			final boolean finalFound = Pattern
					.compile("<build>" + ANY + "<finalName>additioner</finalName>" + ANY + "</build>")
					.matcher(pomContent).find();
			final boolean cRootFound = Pattern.compile("<context-root>additioner</context-root>")
					.matcher(read(Paths.get("src/main/webapp/WEB-INF/jboss-web.xml"))).find();
			final boolean cRootIncorrectFound = Pattern.compile("<context-root>/additioner</context-root>")
					.matcher(read(Paths.get("src/main/webapp/WEB-INF/jboss-web.xml"))).find();
			Ex2Criterion criterion = Ex2Criterion.FINAL_NAME;

			final CriterionGrade thisGrade;
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
			final CriterionGrade thisGrade;
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
		final CriterionGrade thisGrade;
		if (result) {
			thisGrade = CriterionGrade.max(criterion);
		} else {
			thisGrade = CriterionGrade.of(criterion, failPoints, commentIfFail);
		}
		putGrade(thisGrade);
	}

	private void putGrade(CriterionGrade grade) {
		requireNonNull(grade);
		gradesBuilder.add(grade);
	}

	public void setIgnoreAfter(Instant ignoreAfter) {
		this.ignoreAfter = requireNonNull(ignoreAfter);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Ex2Grader.class);
	private Instant deadline;
	private Instant ignoreAfter;
	private static final double MAX_GRADE = 20d;

	private static final String ANY = "(.|\\v)*";
	private ContextInitializer context;
	private final BoxSupplier coordinatesSupplier;

	public StudentGrade grade(RepositoryCoordinates coordinates, @SuppressWarnings("hiding") StudentOnGitHub student)
			throws GradingException, IOException {
		setContextInitializer(ContextInitializer.ignoreAfter(coordinatesSupplier, ignoreAfter));
		this.student = requireNonNull(student);

		gradesBuilder = ImmutableSet.builder();
		grade(coordinates);
		return StudentGrade.of(student, gradesBuilder.build());
	}

	public void setContextInitializer(final ContextInitializer contextInitializer) {
		context = contextInitializer;
	}

	public void setDeadline(Instant deadline) {
		this.deadline = requireNonNull(deadline);
	}
}
