package io.github.oliviercailloux.st_projects.ex1;

import static com.google.common.base.Preconditions.checkState;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.AFTER_MERGE1;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.AFTER_MERGE1_BOLD;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.ALTERNATIVE_APPROACH;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.CONTAINS_START;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.CURL;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.CURL_BASICS;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.CURL_CMD;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.CURL_DATE;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.CURL_FORMAT;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.CURL_LINE;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.CURL_PROPS;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.CURL_START;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.DEV2_EXISTS;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.DEV_CONTAINS_BOLD;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.DEV_EXISTS;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.HELLO2;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.MERGE1_COMMIT;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.MERGE1_CONTAINS_BOLD;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.MERGE2_COMMIT;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.MERGED1;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.ON_TIME;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.REPO_EXISTS;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.TRY_1;
import static io.github.oliviercailloux.st_projects.ex1.Ex1Criterion.USER_NAME;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Streams;
import com.google.common.graph.Traverser;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.v3.Event;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.git.git_hub.utils.Utils;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;
import io.github.oliviercailloux.st_projects.services.git.Client;
import io.github.oliviercailloux.st_projects.services.git.GitHistory;

public class Ex1Grader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Ex1Grader.class);

	private final EnumMap<Ex1Criterion, Double> penalties;
	private final Set<Ex1Criterion> pass;

	private Client client;

	private StudentOnGitHub student;

	private GitHistory historyMaster;

	private RepositoryCoordinates coordinates;

	private Optional<Instant> deadline;

	private boolean hasContent;

	private final EnumMap<Ex1Criterion, String> comments;

	private Supplier<GitHubFetcherV3> fetcherSupplier;

	public Ex1Grader() {
		comments = new EnumMap<>(Ex1Criterion.class);
		penalties = new EnumMap<>(Ex1Criterion.class);
		pass = EnumSet.noneOf(Ex1Criterion.class);
		client = null;
		historyMaster = null;
		coordinates = null;
		student = null;
		deadline = Optional.empty();
		fetcherSupplier = () -> {
			try {
				return GitHubFetcherV3.using(Utils.getToken());
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		};
	}

	public void grade(@SuppressWarnings("hiding") RepositoryCoordinates coordinates,
			@SuppressWarnings("hiding") StudentOnGitHub student, boolean submittedUsername)
			throws IllegalStateException, GitAPIException, IOException {
		this.coordinates = requireNonNull(coordinates);
		this.student = requireNonNull(student);
		hasContent = init();
		grade(submittedUsername);
	}

	private void grade(boolean submittedUsername) throws IOException, GitAPIException {
		if (!submittedUsername) {
			penalties.put(Ex1Criterion.SUBMITTED_GITHUB_USER_NAME, 3d);
		} else {
			pass.add(Ex1Criterion.SUBMITTED_GITHUB_USER_NAME);
		}

		if (!hasContent) {
			return;
		}
		final Instant last = getLastEvent();
		final Optional<Duration> tardinessOpt = deadline.map(d -> Duration.between(d, last)).map(d -> d.minusMinutes(2))
				.filter(d -> !d.isNegative());
		LOGGER.debug("Last: {}, deadline: {}, tardiness: {}.", last, deadline, tardinessOpt);
		if (tardinessOpt.isPresent()) {
			LOGGER.warn("Last event after deadline: {}.", last);
			final long hoursLate = tardinessOpt.get().toHours() + 1;
			comments.put(ON_TIME, "Last event after deadline: "
					+ ZonedDateTime.ofInstant(last, ZoneId.of("Europe/Paris")) + ", " + hoursLate + " hours late.");
			penalties.put(ON_TIME, 3d * 1.5 * hoursLate);
		} else {
			pass.add(ON_TIME);
		}

		final Set<RevCommit> firstCommits = historyMaster.getFirsts();
		LOGGER.debug("First commits: {}.", firstCommits);
		if (firstCommits.size() > 1) {
			penalties.put(Ex1Criterion.SINGLE_ROOT_COMMIT, 3d);
			comments.put(Ex1Criterion.SINGLE_ROOT_COMMIT, "First commits: " + firstCommits + ".");
		} else {
			pass.add(Ex1Criterion.SINGLE_ROOT_COMMIT);
		}

		final Optional<RevCommit> minOpt = firstCommits.stream().min(Comparator.comparing(this::getCreatedAt));
		assert minOpt.isPresent();
		final RevCommit firstCommit = minOpt.get();
		LOGGER.debug("First commit: {}, name: {}.", firstCommit, firstCommit.name());
		client.checkout(firstCommit);

		final PersonIdent author = firstCommit.getAuthorIdent();
		final PersonIdent committer = firstCommit.getCommitterIdent();
		checkState(author.equals(committer));
		final String contributerName = author.getName();
		if (contributerName.toLowerCase().contains(student.getGitHubUsername().toLowerCase())
				|| student.hasStudentOnMyCourse()
						&& contributerName.toLowerCase().contains(student.getLastName().get().toLowerCase())
				|| student.hasStudentOnMyCourse()
						&& contributerName.toLowerCase().contains(student.getMyCourseUsername().get())) {
			if (!contributerName.equalsIgnoreCase(student.getGitHubUsername())) {
				/** NB this is not the right check. */
				LOGGER.debug("Contributer name {} slightly unexpected: expected {}.", contributerName, student);
			}
			pass.add(USER_NAME);
		} else {
			comments.put(USER_NAME, "Wrong contributer name: " + contributerName + " (" + student.getGitHubUsername()
					+ ", " + student.getLastName() + ", " + student.getMyCourseUsername() + ").");
		}

		final Path dir = client.getProjectDirectory();
		{
			final Optional<Path> fileOpt = Files.list(dir).filter((f) -> f.getFileName().toString().equals("start.txt"))
					.collect(MoreCollectors.toOptional());
			final boolean containsStart = fileOpt.isPresent();
			if (containsStart) {
				pass.add(CONTAINS_START);
				final Path startFile = fileOpt.get();
				final String content = new String(Files.readAllBytes(startFile), StandardCharsets.UTF_8);
				if (content.contains("hello2")) {
					pass.add(HELLO2);
				} else {
					comments.put(HELLO2, "String 'hello2' not found in 'start.txt'.");
				}
			} else {
				comments.put(CONTAINS_START, "First commit (" + firstCommit.name() + ") does not contain 'start.txt'.");
			}
		}
		// repo exists: 2
		// commit right user name: 2
		// first commit contains start.txt: 1
		// first commit contains a blob “hello2” in start.txt: 1

		final ImmutableList<Ref> branches = client.listBranches();
		final Optional<Ref> devOpt = branches.stream().filter((r) -> r.getName().equals("refs/remotes/origin/dev"))
				.collect(MoreCollectors.toOptional());
		if (devOpt.isPresent()) {
			pass.add(DEV_EXISTS);
			client.checkout("origin/dev");
			final Optional<Path> fileOpt = Files.list(dir).filter((f) -> f.getFileName().toString().equals("bold.txt"))
					.collect(MoreCollectors.toOptional());
			final boolean containsFile = fileOpt.isPresent();
			if (containsFile) {
				pass.add(DEV_CONTAINS_BOLD);
				final Path file = fileOpt.get();
				final String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
				if (content.contains("try 1")) {
					pass.add(TRY_1);
				} else {
					comments.put(TRY_1, "String 'try 1' not found in 'bold.txt'.");
				}
			} else {
				comments.put(DEV_CONTAINS_BOLD, "Branch dev does not contain 'bold.txt'.");
			}
		} else {
			comments.put(DEV_EXISTS, "Branch dev not found.");
		}
		final Optional<Ref> dev2Opt = branches.stream().filter((r) -> r.getName().equals("refs/remotes/origin/dev2"))
				.collect(MoreCollectors.toOptional());
		if (dev2Opt.isPresent()) {
			pass.add(DEV2_EXISTS);
			client.checkout("origin/dev2");
			final Optional<Path> fileOpt = Files.list(dir).filter((f) -> f.getFileName().toString().equals("bold.txt"))
					.collect(MoreCollectors.toOptional());
			final boolean containsFile = fileOpt.isPresent();
			if (containsFile) {
				final Path file = fileOpt.get();
				final String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
				if (content.contains("alternative approach")) {
					pass.add(ALTERNATIVE_APPROACH);
				}
			}
			if (!pass.contains(ALTERNATIVE_APPROACH)) {
				comments.put(ALTERNATIVE_APPROACH, "String 'alternative approach' not found in 'bold.txt'.");
			}
		} else {
			comments.put(DEV2_EXISTS, "Branch dev2 not found.");
		}
		// branch dev exists: 1
		// branch dev contains “try 1” in bold.txt: 1
		// branch dev2 exists: 1
		// branch dev2 contains “alternative approach” in bold.txt: 1

		final Set<RevCommit> nodes = historyMaster.getGraph().nodes();
		final ImmutableSet<RevCommit> mergeCommits = nodes.stream()
				.filter((c) -> isMergeCommit1(c, devOpt, dev2Opt, firstCommit)).collect(ImmutableSet.toImmutableSet());
		switch (mergeCommits.size()) {
		case 0:
			comments.put(MERGE1_COMMIT, "No merge 1 commit found.");
			break;
		case 1:
			pass.add(MERGE1_COMMIT);
			final RevCommit mergeCommit = Iterables.getOnlyElement(mergeCommits);
			client.checkout(mergeCommit);
			final String contentBoldMerge;
			final Optional<Path> fileBoldMergeOpt = Files.list(dir)
					.filter((f) -> f.getFileName().toString().equals("bold.txt")).collect(MoreCollectors.toOptional());
			final boolean containsFileBoldMerge = fileBoldMergeOpt.isPresent();
			if (containsFileBoldMerge) {
				pass.add(MERGE1_CONTAINS_BOLD);
				final Path fileBoldMerge = fileBoldMergeOpt.get();
				contentBoldMerge = new String(Files.readAllBytes(fileBoldMerge), StandardCharsets.UTF_8);
				if (!contentBoldMerge.matches("<<<|===|>>>")) {
					pass.add(MERGED1);
				} else {
					comments.put(MERGED1, "Content of 'bold.txt' has not been merged.");
				}
			} else {
				comments.put(MERGE1_CONTAINS_BOLD, "Merged commit does not contain 'bold.txt'.");
				contentBoldMerge = "";
			}
			final Set<RevCommit> then = historyMaster.getGraph().predecessors(mergeCommit);
			switch (then.size()) {
			case 0:
				comments.put(AFTER_MERGE1, "Commit after merged1 not found.");
				break;
			case 1:
				pass.add(AFTER_MERGE1);
				break;
			default:
				comments.put(AFTER_MERGE1, "Multiple commits exist having merged1 as parent.");
				break;
			}
			if (pass.contains(AFTER_MERGE1) && pass.contains(MERGED1)) {
				final RevCommit thenCommit = Iterables.getOnlyElement(then);
				client.checkout(thenCommit);
				final Optional<Path> fileOpt = Files.list(dir)
						.filter((f) -> f.getFileName().toString().equals("bold.txt"))
						.collect(MoreCollectors.toOptional());
				final boolean containsFile = fileOpt.isPresent();
				if (containsFile) {
					final Path file = fileOpt.get();
					final String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
					if (!content.equals(contentBoldMerge)) {
						pass.add(AFTER_MERGE1_BOLD);
					} else {
						comments.put(AFTER_MERGE1_BOLD,
								"Content of 'bold.txt' has not changed in commit after merge: before '"
										+ contentBoldMerge + "', after '" + content + "'.");
					}
				} else {
					comments.put(AFTER_MERGE1_BOLD, "No file 'bold.txt' in commit after merge.");
				}
			}
			break;
		default:
			comments.put(MERGE1_COMMIT, "Multiple merge 1 commits found.");
			break;
		}
		// a single commit exists with exactly two parents, both being parent of the
		// first commit or being the branch dev and dev2: 2
		// merged commit contains bold.txt: 1
		// merged commit contains neither “<<<” nor “===” nor “>>>”: 1
		// commit exists after merged one: 0.5
		// commit after merged one contains a blob in bold.txt strict superset of the
		// one in merged: 1.5

		final Traverser<RevCommit> traverser = Traverser.forGraph(historyMaster.getGraph());

		final ImmutableSet<RevCommit> mergeCommits2 = historyMaster.getGraph().nodes().stream()
				.filter((c) -> c.getParentCount() == 2 && ImmutableSet.copyOf(c.getParents()).stream()
						.allMatch((p) -> isAwayFromAtLeast(traverser, p, 4, firstCommit)))
				.collect(ImmutableSet.toImmutableSet());
		switch (mergeCommits2.size()) {
		case 0:
			comments.put(MERGE2_COMMIT, "No merge 2 commit found.");
			break;
		case 1:
			pass.add(MERGE2_COMMIT);
			break;
		default:
			final String allFound = mergeCommits2.stream().map(RevCommit::getName).collect(Collectors.joining(", "));
			comments.put(MERGE2_COMMIT, "Multiple merge 2 commits found: [" + allFound + "].");
			break;
		}
		// some commit is parent of two commits each having at least four ascendents: 4

		final Optional<Ref> curlOpt = branches.stream().filter((r) -> r.getName().equals("refs/remotes/origin/curl"))
				.collect(MoreCollectors.toOptional());
		if (curlOpt.isPresent()) {
			pass.add(CURL);
			client.checkout("origin/curl");
			final Optional<Path> fileOpt = Files.list(dir).filter((f) -> f.getFileName().toString().equals("curl.txt"))
					.collect(MoreCollectors.toOptional());
			final boolean containsFile = fileOpt.isPresent();
			if (containsFile) {
				final Path file = fileOpt.get();
				final String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
				// wanted: content then (content|hspace)* then (space)*, with content=\S, hspace
				// =\h.
				final Pattern pattern = Pattern.compile("\\s*(\\S[\\S\\h]*)\\s*");
				final Matcher matcher = pattern.matcher(content);
				final boolean matches = matcher.find();
				if (matches) {
					pass.add(CURL_LINE);
					final String lineContent = matcher.group(1);
					if (lineContent.contains("curl ")) {
						pass.add(CURL_CMD);
					} else {
						comments.put(CURL_CMD, "Missing curl command.");
					}
					if (lineContent.contains("curl ")) {
						pass.add(CURL_CMD);
					} else {
						comments.put(CURL_CMD, "Missing curl command.");
					}
					if (lineContent.contains("https://en.wikipedia.org/w/api.php?")) {
						pass.add(CURL_START);
						if (lineContent.contains("action=query") && lineContent.contains("&titles=Bertrand")
								&& lineContent.contains("&prop=revisions")) {
							pass.add(CURL_BASICS);
						} else {
							comments.put(CURL_BASICS,
									"Basics missing in url: “action=query”, “&titles=Bertrand”, and “&prop=revisions”.");
						}
					} else {
						comments.put(CURL_START, "Missing start url: 'https://en.wikipedia.org/w/api.php?'.");
					}
					if (pass.contains(CURL_BASICS)) {
						if (lineContent.matches(
								".*&rvprop=(ids|timestamp|user)(\\||%7C)(ids|timestamp|user)(\\||%7C)(ids|timestamp|user)[^\\|%].*")) {
							pass.add(CURL_PROPS);
						}
						if (lineContent.contains("&rvstart=01082017") || lineContent.contains("&rvstart=2017")) {
							pass.add(CURL_DATE);
						}
						if (lineContent.contains("&format=json")) {
							pass.add(CURL_FORMAT);
						}
					}
				} else {
					comments.put(CURL_LINE, "The file 'curl.txt' does not contain a single command.");
				}
			} else {
				comments.put(CURL_LINE, "No file 'curl.txt' in branch 'curl'.");
			}
		}
		// "a branch curl exists", 0.5
		// "a file curl.txt exists in that branch with a single line", 0.5
		// "the line contains “curl ”", 1
		// "the line contains “https://en.wikipedia.org/w/api.php?”", 1
		// "the line contains the basics: “https://en.wikipedia.org/w/api.php?”,
		// “action=query”, “&titles=Bertrand”, and “&prop=revisions”", 1
		// "the line contains, in supplement to the basics:
		// “&rvprop=ids|timestamp|user”", 2
		// "the line contains, in supplement to the basics: “&rvstart=01082017”", 2
		// "the line contains, in supplement to the basics: “&format=json”", 2
		// check that unique (bold.txt final?)
		// also: on time?
		// TODO tell them to protect with quotes!
	}

	private Instant getCreatedAt(RevCommit commit) {
		final PersonIdent authorIdent = commit.getAuthorIdent();
		final Date when = authorIdent.getWhen();
//		final TimeZone tz = authorIdent.getTimeZone();
		return when.toInstant();
	}

	private boolean init() throws GitAPIException, IOException, CheckoutConflictException {
		comments.clear();
		pass.clear();

		client = Client.about(coordinates);
		{
			final boolean exists = client.retrieve();
			if (!exists) {
				comments.put(REPO_EXISTS, "Repository not found");
				return false;
			}
		}
		{
			final boolean contains = client.hasContent();
			if (contains) {
				pass.add(REPO_EXISTS);
			} else {
				comments.put(REPO_EXISTS, "Repository found but is empty");
				return false;
			}
		}

		client.checkout("origin/master");
		historyMaster = client.listCommits(false);
		return true;
	}

	public String getEvaluation() {
		final Stream<String> evaluations = Arrays.stream(Ex1Criterion.values()).map(this::getEvaluation);
		final String joined = evaluations.collect(Collectors.joining("</td></tr><tr><td>"));
		return "<p><table><tbody><tr><td>" + joined + "</tr></tbody></table></p><p>" + "Grade: " + getGrade() + "/"
				+ getMaxGrade() + ".</p>";
	}

	public double getMaxGrade() {
		return Arrays.stream(Ex1Criterion.values()).collect(Collectors.summingDouble(Ex1Criterion::getPoints));
	}

	private String getEvaluation(Ex1Criterion criterion) {
		final StringBuilder builder = new StringBuilder();
		builder.append(criterion.toString());
		builder.append(" ");
		builder.append("(");
		builder.append(criterion.getExpl());
		builder.append(")");
		builder.append("</td><td>");
		if (pass.contains(criterion)) {
			checkState(!penalties.containsKey(criterion));
			if (criterion.getPoints() == 0d) {
				builder.append("OK");
			} else {
				builder.append(criterion.getPoints());
				builder.append("/");
				builder.append(criterion.getPoints());
			}
		} else if (penalties.containsKey(criterion)) {
			checkState(criterion.getPoints() == 0d);
			builder.append("−");
			builder.append(penalties.get(criterion));
			if (comments.containsKey(criterion)) {
				builder.append(", reason: ");
				builder.append("'");
				builder.append(comments.get(criterion));
				builder.append("'");
			}
			builder.append(".");
		} else {
			builder.append("0");
			builder.append("/");
			builder.append(criterion.getPoints());
			if (comments.containsKey(criterion)) {
				builder.append(", reason: ");
				builder.append("'");
				builder.append(comments.get(criterion));
				builder.append("'");
			}
			builder.append(".");
		}
		final String evaluation = builder.toString();
		return evaluation;
	}

	public Instant getLastEvent() {
		final Instant createdAt;
		try (GitHubFetcherV3 fetcher = fetcherSupplier.get()) {
			final ImmutableList<Event> events = fetcher.getEvents(coordinates);
			final Event last = events.get(events.size() - 1);
			LOGGER.debug("Last: {}.", last);
			createdAt = last.getCreatedAt();
		}
		return createdAt;
	}

	void setRawGitHubFetcherSupplier(Supplier<GitHubFetcherV3> supplier) {
		fetcherSupplier = supplier;
	}

	private boolean isAwayFromAtLeast(Traverser<RevCommit> traverser, RevCommit start, int distance, RevCommit target) {
		final boolean reached = Streams.stream(traverser.depthFirstPreOrder(start)).limit(distance)
				.anyMatch(Predicates.equalTo(target));
		LOGGER.debug("Checking is away ({}): {}, {} → reached? {}.", distance, start, target, reached);
		return !reached;
	}

	private boolean isMergeCommit1(RevCommit commit, Optional<Ref> devOpt, Optional<Ref> dev2Opt,
			RevCommit firstCommit) {
		if (commit.getParentCount() != 2) {
			return false;
		}
		final ImmutableSet<RevCommit> parents = ImmutableSet.copyOf(commit.getParents());

		final boolean parentsAreDev = devOpt.isPresent() && dev2Opt.isPresent()
				&& parents.equals(ImmutableSet.of(devOpt.get().getObjectId(), dev2Opt.get().getObjectId()));
		final boolean parentsAreSecond = parents.stream()
				.allMatch((c) -> c.getParentCount() == 1 && c.getParent(0).equals(firstCommit));
		return parentsAreDev || parentsAreSecond;
	}

	public EnumMap<Ex1Criterion, String> getComments() {
		return comments;
	}

	public EnumMap<Ex1Criterion, Double> getPenalties() {
		return penalties;
	}

	public double getGrade() {
		final double positive = pass.stream().collect(Collectors.summingDouble(Ex1Criterion::getPoints));
		final double penalts = penalties.values().stream().collect(Collectors.summingDouble(Double::doubleValue));
		return Math.max(0d, positive - penalts);
	}

	public Instant getLastPushDate() {
		final Instant createdAt;
		try (GitHubFetcherV3 fetcher = fetcherSupplier.get()) {
			final ImmutableList<Event> pushEvents = fetcher.getPushEvents(coordinates);
			final Event last = pushEvents.get(pushEvents.size() - 1);
			createdAt = last.getCreatedAt();
		}
		return createdAt;
	}

	public void setDeadline(Instant deadline) {
		this.deadline = Optional.of(deadline);
	}

	public Set<Ex1Criterion> getPass() {
		return pass;
	}
}
