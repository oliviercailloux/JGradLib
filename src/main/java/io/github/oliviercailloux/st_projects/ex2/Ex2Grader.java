package io.github.oliviercailloux.st_projects.ex2;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.v3.Event;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;
import io.github.oliviercailloux.st_projects.services.git.Client;
import io.github.oliviercailloux.st_projects.services.git.GitHistory;

public class Ex2Grader {
	private StudentOnGitHub student;
	private ImmutableSet.Builder<SingleGrade> gradesBuilder;
	private Instant ignoreAfter;
	private Supplier<GitHubFetcherV3> fetcherSupplier;
	private Client client;

	public Ex2Grader() {
		gradesBuilder = null;
		student = null;
		client = null;
		deadline = Instant.MAX;
		ignoreAfter = Instant.MAX;
		fetcherSupplier = () -> {
			try {
				return GitHubFetcherV3.using(GitHubToken.getRealInstance());
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		};
		pomContent = null;
		mavenManager = new MavenManager();
		allEvents = null;
		submitted = null;
	}

	private void grade() throws IOException {
		final String any = "(.|\\v)*";
		final String groupId;
		final List<String> groupIdElements;
		pomContent = read(Paths.get("pom.xml"));
		final String servletContent;

		onTime();

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
					.compile("<dependencies>" + any + "<dependency>" + any + "<groupId>org\\.mnode\\.ical4j</groupId>"
							+ any + "<artifactId>ical4j</artifactId>" + any + "<version>3\\.0\\.2</version>")
					.matcher(pomContent).find();
			binaryCriterion(Ex2Criterion.ICAL, () -> found);
		}
		{
			final boolean found = Pattern.compile("<properties>" + any
					+ "<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>" + any + "</properties>")
					.matcher(pomContent).find();
			binaryCriterion(Ex2Criterion.UTF, () -> found);
		}
		{
			final boolean found = Pattern.compile(
					"<properties>" + any + "<maven.compiler.source>.*</maven.compiler.source>" + any + "</properties>")
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
				putGrade(SingleGrade.zero(Ex2Criterion.PREFIX, "Unknown group id"));
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
				final boolean allMatch2 = allMatch;
				binaryCriterion(Ex2Criterion.PREFIX, () -> allMatch2);
			}
		}
		{
			final boolean compile = mavenManager.compile(client.getProjectDirectory().resolve("pom.xml"));
			binaryCriterion(Ex2Criterion.COMPILE, () -> compile);
		}
		{
			final Set<Path> sources;
			if (!client.hasCachedContent()) {
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
				putGrade(SingleGrade.of(Ex2Criterion.AUTO, -1d, ""));
			} else {
				putGrade(SingleGrade.of(Ex2Criterion.AUTO, 0, ""));
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
					.compile("<build>" + any + "<finalName>additioner</finalName>" + any + "</build>")
					.matcher(pomContent).find();
			final boolean cRootFound = Pattern.compile("<context-root>additioner</context-root>")
					.matcher(read(Paths.get("src/main/webapp/WEB-INF/jboss-web.xml"))).find();
			final boolean cRootIncorrectFound = Pattern.compile("<context-root>/additioner</context-root>")
					.matcher(read(Paths.get("src/main/webapp/WEB-INF/jboss-web.xml"))).find();
			GradeCriterion criterion = Ex2Criterion.FINAL_NAME;

			final SingleGrade thisGrade;
			if (finalFound) {
				thisGrade = SingleGrade.max(criterion);
			} else if (cRootFound) {
				thisGrade = SingleGrade.of(criterion, criterion.getMaxPoints() / 2d, "Non-portable solution");
			} else if (cRootIncorrectFound) {
				thisGrade = SingleGrade.of(criterion, criterion.getMaxPoints() / 4d,
						"Non-portable solution and incorrect format");
			} else {
				thisGrade = SingleGrade.zero(criterion);
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
			GradeCriterion criterion = Ex2Criterion.ERROR_STATUS;
			final SingleGrade thisGrade;
			if (sendError || setStatus) {
				thisGrade = SingleGrade.max(criterion);
			} else if (sendError400 || setStatus400) {
				thisGrade = SingleGrade.of(criterion, criterion.getMaxPoints() / 2d,
						"Error sent with literal constant instead of symbolic constant.");
			} else {
				thisGrade = SingleGrade.of(criterion, 0d, "");
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
			final Optional<AnyObjectId> classpathId = client.getBlobId("master", ".classpath");
			final Optional<AnyObjectId> settingsId = client.getBlobId("master", ".settings/");
			final Optional<AnyObjectId> projectId = client.getBlobId("master", ".project");
			final Optional<AnyObjectId> targetId = client.getBlobId("master", "target/");
			LOGGER.debug("Found settings? {}.", settingsId);
			final double points = ImmutableList.of(classpathId, settingsId, projectId, targetId).stream().collect(
					Collectors.summingDouble((o) -> !o.isPresent() ? Ex2Criterion.ONLY_ORIG.getMaxPoints() / 4 : 0));
			putGrade(SingleGrade.of(Ex2Criterion.ONLY_ORIG, points, ""));
		}

		// TODO check that eclipse and .class files are not in the repo
	}

	private String read(Path relativePath) throws IOException {
		if (!client.hasCachedContent()) {
			return "";
		}
		String content;
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

	private void binaryCriterion(GradeCriterion criterion, Supplier<Boolean> tester) {
		binaryCriterion(criterion, tester, 0d);
	}

	private void binaryCriterion(GradeCriterion criterion, Supplier<Boolean> tester, double failPoints) {
		binaryCriterion(criterion, tester.get(), failPoints, "");
	}

	private void binaryCriterion(GradeCriterion criterion, boolean result, double failPoints, String commentIfFail) {
		final SingleGrade thisGrade;
		if (result) {
			thisGrade = SingleGrade.max(criterion);
		} else {
			thisGrade = SingleGrade.of(criterion, failPoints, commentIfFail);
		}
		putGrade(thisGrade);
	}

	private void putGrade(SingleGrade grade) {
		requireNonNull(grade);
		gradesBuilder.add(grade);
	}

	public void setIgnoreAfter(Instant ignoreAfter) {
		this.ignoreAfter = requireNonNull(ignoreAfter);
	}

	void setRawGitHubFetcherSupplier(Supplier<GitHubFetcherV3> supplier) {
		fetcherSupplier = supplier;
	}

	private boolean init(RepositoryCoordinates coordinates)
			throws GitAPIException, IOException, CheckoutConflictException {
		client = Client.about(coordinates);
		{
			final boolean exists = client.retrieve();
			if (!exists) {
				putGrade(SingleGrade.zero(Ex2Criterion.REPO_EXISTS, "Repository not found"));
			} else if (!client.hasContent()) {
				putGrade(SingleGrade.zero(Ex2Criterion.REPO_EXISTS, "Repository found but is empty"));
			} else {
				putGrade(SingleGrade.max(Ex2Criterion.REPO_EXISTS));
			}
		}
		if (client.hasCachedContent()) {
			client.checkout("master");
			getEvents();
			final GitHistory history = client.listCommits(false);
			final Map<ObjectId, Instant> receivedAt = new GitAndGitHub().check(client, allEvents);
			/** Now we want to discard all commits that have a commit too late as parent. */
			final Set<RevCommit> commits = history.getGraph().nodes();
			final ImmutableList<RevCommit> commitsOnTime = commits.stream()
					.filter((c) -> !receivedAt.get(c.getId()).isAfter(ignoreAfter))
					.collect(ImmutableList.toImmutableList());
			LOGGER.info("Commits: {}; on time: {}.", commits, commitsOnTime);
			checkState(Comparators.isInOrder(commitsOnTime,
					Comparator.<RevCommit, Instant>comparing((c) -> receivedAt.get(c.getId())).reversed()));
			final RevCommit lastCommitOnTime = commitsOnTime.get(0);

			client.checkout(lastCommitOnTime);
			submitted = receivedAt.get(lastCommitOnTime);
		}

		return client.hasCachedContent();
	}

	private void onTime() {
		if (!client.hasCachedContent()) {
			putGrade(SingleGrade.of(Ex2Criterion.ON_TIME, 0d, ""));
			return;
		}

		final Duration tardiness = Duration.between(deadline, submitted).minusMinutes(2);

		LOGGER.debug("Last: {}, deadline: {}, tardiness: {}.", submitted, deadline, tardiness);
		if (!tardiness.isNegative()) {
			LOGGER.warn("Last event after deadline: {}.", submitted);
			final long hoursLate = tardiness.toHours() + 1;
			putGrade(SingleGrade.of(Ex2Criterion.ON_TIME, -3d / 20d * MAX_GRADE * hoursLate,
					"Last event after deadline: " + ZonedDateTime.ofInstant(submitted, ZoneId.of("Europe/Paris")) + ", "
							+ hoursLate + " hours late."));
		} else {
			putGrade(SingleGrade.of(Ex2Criterion.ON_TIME, 0d, ""));
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Ex2Grader.class);
	private String pomContent;
	private final MavenManager mavenManager;
	private Instant deadline;
	private static final double MAX_GRADE = 20d;

	private ImmutableList<Event> allEvents;
	private Instant submitted;

	public Grade grade(RepositoryCoordinates coordinates, @SuppressWarnings("hiding") StudentOnGitHub student)
			throws GitAPIException, IOException {
		gradesBuilder = ImmutableSet.builder();
		this.student = requireNonNull(student);
		init(coordinates);
		grade();
//		return Grade.of(student, new HashSet<>(gradesBuilder.build().values()));
		return Grade.of(student, gradesBuilder.build());
	}

	public List<Event> getEvents() {
		checkState(client.hasCachedContent());
		try (GitHubFetcherV3 fetcher = fetcherSupplier.get()) {
			allEvents = fetcher.getEvents(client.getCoordinates());
		}
		checkState(allEvents.size() >= 1);
		final Stream<Instant> eventsCreation = allEvents.stream().map(Event::getCreatedAt);
		checkState(Comparators.isInOrder(eventsCreation::iterator, Comparator.<Instant>naturalOrder().reversed()));
		return allEvents;
	}

	public void setDeadline(Instant deadline) {
		this.deadline = requireNonNull(deadline);
	}
}
