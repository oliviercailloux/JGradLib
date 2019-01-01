package io.github.oliviercailloux.st_projects.ex2;

import static io.github.oliviercailloux.st_projects.utils.Utils.ANY;
import static java.util.Objects.requireNonNull;

import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.st_projects.GraderOrchestrator;
import io.github.oliviercailloux.st_projects.model.ContentSupplier;
import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.MultiContentSupplier;
import io.github.oliviercailloux.st_projects.model.StudentGrade;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHubKnown;
import io.github.oliviercailloux.st_projects.services.grading.ContextInitializer;
import io.github.oliviercailloux.st_projects.services.grading.CriterionGrader;
import io.github.oliviercailloux.st_projects.services.grading.GitGrader;
import io.github.oliviercailloux.st_projects.services.grading.GitToMultipleSourcer;
import io.github.oliviercailloux.st_projects.services.grading.GitToSourcer;
import io.github.oliviercailloux.st_projects.services.grading.Graders;
import io.github.oliviercailloux.st_projects.services.grading.GradingExecutor;
import io.github.oliviercailloux.st_projects.services.grading.PomContexter;
import io.github.oliviercailloux.st_projects.services.grading.TimeGrader;

public class Ex2GradingBuilder {
	private ContextInitializer contextInitializer;

	public Ex2GradingBuilder() {
		contextInitializer = null;
//		deadline = Instant.MAX;
		deadline = ZonedDateTime.parse("2018-12-05T23:59:59+01:00").toInstant();
		ignoreAfter = ZonedDateTime.parse("2018-12-06T07:00:00+01:00").toInstant();
	}

	private Instant deadline;
	private Instant ignoreAfter;
	private static final double MAX_GRADE = 20d;

	public GradingExecutor build() {
		final GradingExecutor executor = new GradingExecutor();
		final Supplier<RepositoryCoordinates> initialSupplier = executor.getInitialSupplier();
		contextInitializer = ContextInitializer.withIgnore(initialSupplier, ignoreAfter);
		MutableGraph<Object> g = GraphBuilder.directed().build();
		g.addNode(initialSupplier);
		g.putEdge(initialSupplier, contextInitializer);
		final TimeGrader timeGrader = TimeGrader.given(Ex2Criterion.ON_TIME, contextInitializer, deadline, MAX_GRADE);
		g.putEdge(contextInitializer, timeGrader);
		final CriterionGrader repoGrader = GitGrader.repoGrader(Ex2Criterion.REPO_EXISTS, contextInitializer);
		g.putEdge(contextInitializer, repoGrader);
		final GitToSourcer pomSupplier = new GitToSourcer(contextInitializer, Paths.get("pom.xml"));
		g.putEdge(contextInitializer, pomSupplier);
		final PomContexter pomContexter = new PomContexter(pomSupplier);
		g.putEdge(pomSupplier, pomContexter);
		final CriterionGrader pomGrader = Graders.groupIdGrader(Ex2Criterion.GROUP_ID, pomContexter);
		g.putEdge(pomContexter, pomGrader);
		g.putEdge(pomSupplier,
				Graders.predicateGrader(Ex2Criterion.ICAL, pomSupplier,
						Graders.containsOnce(Pattern.compile("<dependencies>" + ANY + "<dependency>" + ANY
								+ "<groupId>org\\.mnode\\.ical4j</groupId>" + ANY + "<artifactId>ical4j</artifactId>"
								+ ANY + "<version>3\\.0\\.2</version>"))));
		g.putEdge(pomSupplier,
				Graders.predicateGrader(Ex2Criterion.UTF, pomSupplier,
						Graders.containsOnce(Pattern.compile("<properties>" + ANY
								+ "<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>" + ANY
								+ "</properties>"))));
		g.putEdge(pomSupplier,
				Graders.predicateGrader(Ex2Criterion.SOURCE, pomSupplier,
						Graders.containsOnce(Pattern.compile("<properties>" + ANY
								+ "<maven.compiler.source>.*</maven.compiler.source>" + ANY + "</properties>"))));
		g.putEdge(pomSupplier, Graders.predicateGrader(Ex2Criterion.NO_MISLEADING_URL, pomSupplier,
				Predicates.contains(Pattern.compile("<url>.*\\.apache\\.org.*</url>")).negate()));
		g.putEdge(pomSupplier, Graders.predicateGrader(Ex2Criterion.WAR, pomSupplier,
				Graders.containsOnce(Pattern.compile("<packaging>war</packaging>"))));
//		final CriterionGrader packageGroupIdGrader = Graders.packageGroupIdGrader(Ex2Criterion.PREFIX,
//				contextInitializer, pomContexter);
//		g.putEdge(contextInitializer, packageGroupIdGrader);
//		g.putEdge(pomContexter, packageGroupIdGrader);
		g.putEdge(contextInitializer, (CriterionGrader) (() -> CriterionGrade.binary(Ex2Criterion.COMPILE,
				new MavenManager().compile(contextInitializer.getClient().getProjectDirectory().resolve("pom.xml")))));
		final GitToMultipleSourcer servletSourcer = GitToMultipleSourcer.satisfyingPath(contextInitializer,
				(p) -> p.startsWith("src/main/java") && p.getFileName().equals(Paths.get("AdditionerServlet.java")));
		g.putEdge(contextInitializer, servletSourcer);
		final CriterionGrader doGetGrader = Graders.predicateGraderWithComment(Ex2Criterion.DO_GET, servletSourcer,
				Graders.containsOnce(Pattern.compile("void\\s*doGet\\s*\\(\\s*(final)?\\s*HttpServletRequest .*\\)")));
		g.putEdge(servletSourcer, doGetGrader);
		final CriterionGrader encGrader = Graders.predicateGrader(Ex2Criterion.SRC_ENC, servletSourcer,
				Predicates.contains(Pattern.compile("Exécution impossible")));
		g.putEdge(servletSourcer, encGrader);
		g.putEdge(servletSourcer,
				Graders.predicateGrader(Ex2Criterion.AUTO, servletSourcer,
						Predicates.contains(Pattern.compile("Auto-generated")).negate()
								.and(Predicates.contains(Pattern.compile("@see HttpServlet#doGet")).negate())));
		g.putEdge(servletSourcer, Graders.predicateGrader(Ex2Criterion.EXC, servletSourcer,
				Predicates.contains(Pattern.compile("printStackTrace")).negate()));
		g.putEdge(servletSourcer, Graders.predicateGrader(Ex2Criterion.ENC, servletSourcer,
				Predicates.contains(Pattern.compile("setCharacterEncoding"))));
		g.putEdge(servletSourcer, Graders.predicateGrader(Ex2Criterion.LOC, servletSourcer,
				Predicates.contains(Pattern.compile("setLocale.+FRENCH"))));
		g.putEdge(servletSourcer, Graders.predicateGrader(Ex2Criterion.MTYPE, servletSourcer,
				Predicates.contains(Pattern.compile("setContentType.+PLAIN"))));
		g.putEdge(servletSourcer, Graders.predicateGrader(Ex2Criterion.ANNOT, servletSourcer,
				Predicates.contains(Pattern.compile("@WebServlet.*\\(.*/add\".*\\)"))));
		final GitToSourcer jbossSupplier = new GitToSourcer(contextInitializer,
				Paths.get("src/main/webapp/WEB-INF/jboss-web.xml"));
		g.putEdge(contextInitializer, jbossSupplier);
		final CriterionGrader contextRootGrader = () -> gradeContextRoot(pomSupplier, jbossSupplier);
		g.putEdge(pomSupplier, contextRootGrader);
		g.putEdge(jbossSupplier, contextRootGrader);
		final CriterionGrader errorStatusGrader = () -> gradeErrorStatus(servletSourcer);
		g.putEdge(servletSourcer, errorStatusGrader);
		g.putEdge(servletSourcer, Graders.predicateGrader(Ex2Criterion.PARAM, servletSourcer,
				Predicates.contains(Pattern.compile("\\.getParameter\\s*\\(.*\\)"))));
		g.putEdge(contextInitializer, Graders.gradeOnlyOrig(Ex2Criterion.ONLY_ORIG, contextInitializer));
		final CriterionGrader staticGrader1 = () -> CriterionGrade.min(Ex2Criterion.GET_SIMPLE, "Todo");
		g.addNode(staticGrader1);
		final CriterionGrader staticGrader2 = () -> CriterionGrade.min(Ex2Criterion.DEFAULT_PARAM, "Todo");
		g.addNode(staticGrader2);

		executor.setGraph(g);
		return executor;
	}

	private CriterionGrade gradeContextRoot(ContentSupplier pomSupplier, ContentSupplier jbossSupplier) {
		final CriterionGrade thisGrade;
		final String pomContent = pomSupplier.getContent();
		final String jbossDescriptor = jbossSupplier.getContent();
		final boolean finalFound = Pattern
				.compile("<build>" + ANY + "<finalName>additioner</finalName>" + ANY + "</build>").matcher(pomContent)
				.find();
		final boolean cRootFound = Pattern.compile("<context-root>additioner</context-root>").matcher(jbossDescriptor)
				.find();
		final boolean cRootIncorrectFound = Pattern.compile("<context-root>/additioner</context-root>")
				.matcher(jbossDescriptor).find();
		Ex2Criterion criterion = Ex2Criterion.FINAL_NAME;

		if (finalFound) {
			thisGrade = CriterionGrade.max(criterion);
		} else if (cRootFound) {
			thisGrade = CriterionGrade.of(criterion, criterion.getMaxPoints() / 2d, "Non-portable solution");
		} else if (cRootIncorrectFound) {
			thisGrade = CriterionGrade.of(criterion, criterion.getMaxPoints() / 4d,
					"Non-portable solution and incorrect format");
		} else {
			thisGrade = CriterionGrade.min(criterion);
		}
		return thisGrade;
	}

	private CriterionGrade gradeErrorStatus(MultiContentSupplier servletSourcer) {
		final ImmutableCollection<String> contents = servletSourcer.getContents().values();
		Ex2Criterion criterion = Ex2Criterion.ERROR_STATUS;
		if (contents.size() != 1) {
			return CriterionGrade.min(criterion);
		}

		final String servletContent = contents.iterator().next();
		final boolean sendError = Pattern.compile("sendError\\(.*SC_BAD_REQUEST.*,.*\\)").matcher(servletContent)
				.find();
		final boolean setStatus = Pattern.compile("setStatus\\(.*SC_BAD_REQUEST.*\\)").matcher(servletContent).find();
		final boolean sendError400 = Pattern.compile("sendError\\(.*400.*,.*\\)").matcher(servletContent).find();
		final boolean setStatus400 = Pattern.compile("setStatus\\(.*400.*\\)").matcher(servletContent).find();
		final CriterionGrade thisGrade;
		if (sendError || setStatus) {
			thisGrade = CriterionGrade.max(criterion);
		} else if (sendError400 || setStatus400) {
			thisGrade = CriterionGrade.of(criterion, criterion.getMaxPoints() / 2d,
					"Error sent with literal constant instead of symbolic constant.");
		} else {
			thisGrade = CriterionGrade.min(criterion);
		}
		return thisGrade;
	}

	public void setDeadline(Instant deadline) {
		this.deadline = requireNonNull(deadline);
	}

	public void setIgnoreAfter(Instant ignoreAfter) {
		this.ignoreAfter = requireNonNull(ignoreAfter);
	}

	public static void main(String[] args) throws Exception {
		final Ex2GradingBuilder grader = new Ex2GradingBuilder();
		grader.proceed();
		// orch.jsonToMyCourse();
		// orch.writeCsv();
	}

	public void proceed() throws Exception {
		final GraderOrchestrator orch = new GraderOrchestrator("servlet");
		orch.readUsernames();

		orch.readRepositories();
		// orch.setSingleRepo("Cocolollipop");

		final GradingExecutor executor = build();

		final ImmutableMap<StudentOnGitHubKnown, RepositoryCoordinates> repositories = orch
				.getRepositoriesByStudentKnown();
		final ImmutableSet<StudentGrade> grades = executor.gradeAll(repositories);

		orch.writeCsv(grades);
		// writeJson();
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Ex2GradingBuilder.class);
}
