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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git_hub.RepositoryCoordinates;
import io.github.oliviercailloux.git_hub.low.Event;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;
import io.github.oliviercailloux.st_projects.services.git.Client;
import io.github.oliviercailloux.st_projects.services.git.GitHistory;
import io.github.oliviercailloux.st_projects.services.git_hub.RawGitHubFetcher;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class Ex2Grader {
	private StudentOnGitHub student;
	private ImmutableMap.Builder<GradeCriterion, SingleGrade> gradesBuilder;
	private Instant ignoreAfter;
	private Supplier<RawGitHubFetcher> fetcherSupplier;
	private Client client;

	public Ex2Grader() {
		gradesBuilder = null;
		student = null;
		client = null;
		deadline = Instant.MAX;
		ignoreAfter = Instant.MAX;
		fetcherSupplier = () -> {
			try {
				return RawGitHubFetcher.using(Utils.getToken());
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
		gradesBuilder.put(grade.getCriterion(), grade);
	}

	public void setIgnoreAfter(Instant ignoreAfter) {
		this.ignoreAfter = requireNonNull(ignoreAfter);
	}

	void setRawGitHubFetcherSupplier(Supplier<RawGitHubFetcher> supplier) {
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
		Event lastConsideredEvent;
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
			checkState(Comparators.isInOrder(commitsOnTime,
					Comparator.<RevCommit, Instant>comparing((c) -> receivedAt.get(c.getId())).reversed()));
			final RevCommit lastCommitOnTime = commitsOnTime.get(commitsOnTime.size() - 1);

			final ImmutableList<Event> eventsNotIgnored = allEvents.stream()
					.filter((e) -> e.getCreatedAt().compareTo(ignoreAfter) <= 0)
					.collect(ImmutableList.toImmutableList());
			/** FIXME Those events could be empty. */
			checkState(eventsNotIgnored.size() >= 1);

			final Optional<Event> lastConsideredPushEvent = eventsNotIgnored.stream()
					.filter((e) -> e.getPushPayload().isPresent())
					.filter((e) -> e.getPushPayload().get().getHead().isPresent()).findFirst();
			/**
			 * A push may have as only effect a move of head, so we need to consider head
			 * and not the commits inside (otherwise we may miss a backwards head move and
			 * stick to a commit that has been cancelled).
			 */
//					.filter((e) -> e.getPushPayload().get().getCommits().size() >= 1).findFirst();
			if (!lastConsideredPushEvent.isPresent()) {
				lastConsideredEvent = eventsNotIgnored.get(0);
				LOGGER.debug("No push; considering as last: {}.", lastConsideredEvent);
				LOGGER.debug("Events: {}.", allEvents);
				final ObjectId id;
				/**
				 * Let’s try to take the just-too-late-event and use its commit before
				 * (sometimes works).
				 */
				final Optional<Event> justTooLate = allEvents.stream()
						.filter((e) -> e.getCreatedAt().compareTo(ignoreAfter) > 0).findFirst();
				final Optional<Event> justTooLatePush = justTooLate.filter((e) -> e.getPushPayload().isPresent())
						.filter((e) -> e.getPushPayload().get().getBefore().isPresent());
				if (justTooLatePush.isPresent()) {
					id = justTooLatePush.get().getPushPayload().get().getBefore().get();
				} else {
					/**
					 * Let’s try to get the nodes from the graph, which should be ordered by time
					 * (most recent first).
					 */
					final ImmutableList<RevCommit> listCommits = ImmutableList.copyOf(commits);
					checkState(Comparators.isInOrder(listCommits,
							Comparator.<RevCommit, Instant>comparing((c) -> client.getCreationTime(c)).reversed()));
					final Optional<RevCommit> ceilingCommit = listCommits.stream()
							.filter((c) -> client.getCreationTime(c).compareTo(ignoreAfter) <= 0).findFirst();
					checkState(ceilingCommit.isPresent());
					id = ceilingCommit.get().getId();
//					final Set<RevCommit> firsts = history.getFirsts();
//					checkState(firsts.size() == 1);
//					id = firsts.stream().collect(MoreCollectors.onlyElement());
				}
				// FIXME should specify somewhere the commit that is used.
				client.checkout(id.getName());
			} else {
				lastConsideredEvent = lastConsideredPushEvent.get();
//				final List<PayloadCommitDescription> commits = lastConsideredEvent.getPushPayload().get().getCommits();
//				final PayloadCommitDescription commit = commits.get(commits.size() - 1);
//				final ObjectId sha = commit.getSha();
				final ObjectId sha = lastConsideredEvent.getPushPayload().get().getHead().get();
				LOGGER.info("Checking out {}.", sha);
				client.checkout(sha.getName());
			}
			submitted = lastConsideredEvent.getCreatedAt();
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
		gradesBuilder = ImmutableMap.builder();
		this.student = requireNonNull(student);
		init(coordinates);
		grade();
//		return Grade.of(student, new HashSet<>(gradesBuilder.build().values()));
		return Grade.of(student, gradesBuilder.build());
	}

	public List<Event> getEvents() {
		// FIXME: two push events at the same second break the map.
		checkState(client.hasCachedContent());
		try (RawGitHubFetcher fetcher = fetcherSupplier.get()) {
			allEvents = fetcher.getEvents(client.getCoordinates());
//			eventsS = allEvents.stream().filter((e) -> e.getPushPayload().isPresent()).collect(
//					ImmutableSortedMap.toImmutableSortedMap(Comparator.naturalOrder(), Event::getCreatedAt, (e) -> e));
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
