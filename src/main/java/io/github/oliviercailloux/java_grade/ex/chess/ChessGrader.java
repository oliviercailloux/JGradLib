package io.github.oliviercailloux.java_grade.ex.chess;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.URI_UNCHECKER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.contexters.MavenManager;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.jaris.exceptions.Try;
import io.github.oliviercailloux.jaris.exceptions.TryVoid;
import io.github.oliviercailloux.java_grade.GraderOrchestrator;
import io.github.oliviercailloux.java_grade.JavaCriterion;
import io.github.oliviercailloux.java_grade.JavaGradeUtils;
import io.github.oliviercailloux.java_grade.bytecode.Compiler;
import io.github.oliviercailloux.java_grade.bytecode.Compiler.CompilationResult;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.java_grade.utils.Summarize;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.samples.chess.ChessBoard;
import io.github.oliviercailloux.samples.chess.Piece;
import io.github.oliviercailloux.utils.Utils;

public class ChessGrader {
	public static final double COMPILE_POINTS = 1.5d / 25d;

	static enum LocalCriterion implements Criterion {
		MAVEN_TESTS, IMPL, FACTORY, BOARD_THROWS_ON_NULL, BOARD_THROWS_ON_ONE_KING, BOARD_THROWS_ON_MULT_KINGS,
		BOARD_THROWS_ON_ILLEGAL_PIECE, NO_CHANGE_STR_INIT, CHANGES_STR, NO_CHANGE_PIECES_INIT, CHANGES_PIECES,
		GET_POS_MAP_STRING, GET_POS_MAP_PIECES, GET_PIECE_ILLEGAL, GET_PIECE_EMPTY, GET_PIECE_PRESENT, GET_PIECES,
		GET_ORDERED, MOVE_THROWS, MOVE;

		@Override
		public String getName() {
			return toString();
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ChessGrader.class);

	private static final String PREFIX = "chess";

	private static final boolean FAKE = false;

	private static final Instant DEADLINE = ZonedDateTime.parse("2020-05-25T00:00:00+02:00").toInstant();

	/**
	 * NB for this to work, we need to have the interfaces in the class path with
	 * the same name as the one used by the student’s projects, namely, …samples.…
	 *
	 */
	public static void main(String[] args) throws Exception {
		final Path outDir = Paths.get("../../Java L3/");
		final Path projectsBaseDir = outDir.resolve(PREFIX);

		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", PREFIX);
		}
//		repositories = ImmutableList.of(RepositoryCoordinatesWithPrefix.from("oliviercailloux-org", PREFIX, "…"));

		final ChessGrader grader = new ChessGrader();
		if (FAKE) {
			grader.deadline = Instant.MAX;
		}

		final Map<String, IGrade> gradesB = new LinkedHashMap<>();
		for (RepositoryCoordinatesWithPrefix repository : repositories) {
			final Path projectDir = projectsBaseDir.resolve(repository.getRepositoryName());
			gradesB.put(repository.getUsername(), grader.grade(repository, projectDir));
			Files.writeString(outDir.resolve("all grades " + PREFIX + "-homework" + ".json"),
					JsonbUtils.toJsonObject(gradesB, JsonGrade.asAdapter()).toString());
			Summarize.summarize(PREFIX, outDir);
		}
	}

	Instant deadline;

	ChessGrader() {
		deadline = DEADLINE;
	}

	public IGrade grade(RepositoryCoordinatesWithPrefix coord, Path projectDir) throws IOException {
		new GitCloner().download(GitUri.fromUri(coord.asURI()), projectDir);

		try (GitFileSystem fs = GitFileSystemProvider.getInstance()
				.newFileSystemFromGitDir(projectDir.resolve(".git"))) {
			final GitHubHistory gitHubHistory = GraderOrchestrator.getGitHubHistory(coord);
			final IGrade grade = grade(coord.getUsername(), fs, gitHubHistory);
			LOGGER.info("Grade {}: {}.", coord, grade);
			return grade;
		}
	}

	public IGrade grade(String owner, GitFileSystem fs, GitHubHistory gitHubHistory) throws IOException {
		checkArgument(gitHubHistory.getPatchedKnowns().nodes().isEmpty());

		final Optional<Instant> lastCommitInstantBeforeDeadlineOpt = gitHubHistory.getCommitDates().values().stream()
				.filter(i -> i.isBefore(deadline)).max(Comparator.naturalOrder());
		if (lastCommitInstantBeforeDeadlineOpt.isEmpty()) {
			return Mark.zero("Found no master commit.");
		}

		final Instant lastCommitGitHubInstantBeforeDeadline = lastCommitInstantBeforeDeadlineOpt.get();
		final ImmutableSet<ObjectId> lastCommitsBeforeDeadline = gitHubHistory.getCommitDates().asMultimap().inverse()
				.get(lastCommitGitHubInstantBeforeDeadline);
		verify(!lastCommitsBeforeDeadline.isEmpty());
		final GitLocalHistory history = fs.getHistory();
		final Optional<ObjectId> lastCommitBeforeDeadlineOpt = lastCommitsBeforeDeadline.stream()
				.max(Comparator.comparing(o -> history.getCommitDateById(o)));
		verify(lastCommitBeforeDeadlineOpt.isPresent());
		final ObjectId lastCommitBeforeDeadline = lastCommitBeforeDeadlineOpt.get();
		final Instant lastCommitInstantBeforeDeadline = history.getCommitDateById(lastCommitBeforeDeadline);
		verify(lastCommitsBeforeDeadline.stream().allMatch(o -> o.equals(lastCommitBeforeDeadline)
				|| history.getCommitDateById(o).isBefore(lastCommitInstantBeforeDeadline)));

		final GitPath gitSourcePath = fs.getAbsolutePath(lastCommitBeforeDeadline.getName());

		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();

		{
			final RevCommit master = fs.getCachedHistory().getCommit(lastCommitBeforeDeadline);
			gradeBuilder.put(JavaCriterion.ID, Mark.binary(JavaMarkHelper.committerAndAuthorIs(master, owner)));
		}

		final Path fileSourcePath = Utils.getTempUniqueDirectory("src-" + owner);
		Utils.copyRecursively(gitSourcePath, fileSourcePath, StandardCopyOption.REPLACE_EXISTING);

		final MavenManager mavenManager = new MavenManager();
		final boolean compiled = mavenManager.compile(fileSourcePath);
		if (compiled) {
			gradeBuilder.put(JavaCriterion.COMPILE, Mark.binary(compiled));

			{
				final Path testsSource = Path
						.of(URI_UNCHECKER.getUsing(() -> getClass().getResource("MyChessBoardTests.java").toURI()));
				final Path testJava = fileSourcePath.resolve("src/test/java/");
				final Path testDestPath = testJava
						.resolve("io/github/oliviercailloux/samples/chess/MyChessBoardTests.java");
				Files.createDirectories(testDestPath.getParent());
				Files.copy(testsSource, testDestPath, StandardCopyOption.REPLACE_EXISTING);

				final boolean tested = mavenManager.test(fileSourcePath);
				gradeBuilder.put(LocalCriterion.MAVEN_TESTS, Mark.binary(tested, "", mavenManager.getCensoredOutput()));
			}

			final ImmutableList<Path> classPath = mavenManager.getClassPath(fileSourcePath);
			checkArgument(classPath.size() >= 2);
			final Path java = fileSourcePath.resolve("src/main/java/");
			final ImmutableList<Path> depsAndItself = ImmutableList.<Path>builder().add(java).addAll(classPath).build();

			final Path effectiveSourcePath = java.resolve("io/github/oliviercailloux/samples/chess/MyChessBoard.java");
			final boolean suppressed = Files.readString(effectiveSourcePath).contains("@SuppressWarnings");
			final CompilationResult eclipseResult = Compiler.eclipseCompile(depsAndItself,
					ImmutableSet.of(effectiveSourcePath));
			verify(eclipseResult.compiled, eclipseResult.err);
			gradeBuilder.put(JavaCriterion.NO_WARNINGS, Mark.binary(!suppressed && (eclipseResult.countWarnings() == 0),
					"", eclipseResult.err.replaceAll(fileSourcePath.toAbsolutePath().toString() + "/", "")));

			final IGrade implGrade = JavaGradeUtils.gradeSecurely(fileSourcePath.resolve(Path.of("target/classes/")),
					ChessBoard.class, this::grade);
			gradeBuilder.put(LocalCriterion.IMPL, implGrade);
		} else {
			gradeBuilder.put(JavaCriterion.COMPILE, Mark.binary(compiled, "", mavenManager.getCensoredOutput()));
			gradeBuilder.put(LocalCriterion.MAVEN_TESTS, Mark.zero());
			gradeBuilder.put(JavaCriterion.NO_WARNINGS, Mark.zero());
			gradeBuilder.put(LocalCriterion.IMPL, Mark.zero());
		}

		final ImmutableMap<Criterion, IGrade> subGrades = gradeBuilder.build();
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(JavaCriterion.ID, 0.5d);
		builder.put(JavaCriterion.COMPILE, COMPILE_POINTS * 25d);
		builder.put(LocalCriterion.MAVEN_TESTS, 0d);
		builder.put(JavaCriterion.NO_WARNINGS, 1.0d);
		builder.put(LocalCriterion.IMPL, 22d);
		return WeightingGrade.from(subGrades, builder.build(), "Using commit " + lastCommitBeforeDeadline.getName());
	}

	private IGrade grade(Supplier<ChessBoard> factory) {
		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();

		try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
			final boolean setInitReturns;
			{
				final ChessBoard instance = factory.get();
				final Try<Boolean> setInit = Try.of(() -> instance.setBoardByString(getBoardInit()));
				LOGGER.info("Set init: {}.", setInit);
				setInitReturns = setInit.isSuccess();
			}
			final boolean noChange;
			{
				final ChessBoard instance = factory.get();
				final boolean setBKReturns = Try.of(() -> instance.setBoardByString(getBoardBK())).isSuccess();
				final Try<Boolean> setAgain = Try.of(() -> instance.setBoardByString(getBoardBK()));
				noChange = setBKReturns && setAgain.equals(Try.success(false));
			}
			final boolean changed;
			{
				final ChessBoard instance = factory.get();
				final boolean setBKReturns = Try.of(() -> instance.setBoardByString(getBoardBK())).isSuccess();
				final Try<Boolean> setAgain = Try.of(() -> instance.setBoardByString(getBoardBK2()));
				changed = setBKReturns && setAgain.equals(Try.success(true));
			}
			final boolean noChangePieces;
			{
				final ChessBoard instance = factory.get();
				final boolean setBKReturns = Try.of(() -> instance.setBoardByPieces(asPieces(getBoardBK())))
						.isSuccess();
				final Try<Boolean> setAgain = Try.of(() -> instance.setBoardByPieces(asPieces(getBoardBK())));
				noChangePieces = setBKReturns && setAgain.equals(Try.success(false));
			}
			final boolean changedPieces;
			{
				final ChessBoard instance = factory.get();
				final boolean setBKReturns = Try.of(() -> instance.setBoardByPieces(asPieces(getBoardBK())))
						.isSuccess();
				final Try<Boolean> setAgain = Try.of(() -> instance.setBoardByPieces(asPieces(getBoardBK2())));
				changedPieces = setBKReturns && setAgain.equals(Try.success(true));
			}

			{
				final ChessBoard instance = factory.get();
				final boolean thrown = JavaGradeUtils.doesThrow(() -> instance.setBoardByString(null),
						e -> e instanceof NullPointerException);
				gradeBuilder.put(LocalCriterion.BOARD_THROWS_ON_NULL, Mark.binary(setInitReturns && thrown));
			}
			{
				final ChessBoard instance = factory.get();
				boolean thrown = JavaGradeUtils.doesThrow(() -> instance.setBoardByString(getBoardOneK()),
						e -> e instanceof IllegalArgumentException);
				gradeBuilder.put(LocalCriterion.BOARD_THROWS_ON_ONE_KING, Mark.binary(setInitReturns && thrown));
			}
			{
				final ChessBoard instance = factory.get();
				final boolean thrown = JavaGradeUtils.doesThrow(() -> instance.setBoardByString(getBoardThreeK()),
						e -> e instanceof IllegalArgumentException);
				gradeBuilder.put(LocalCriterion.BOARD_THROWS_ON_MULT_KINGS, Mark.binary(setInitReturns && thrown));
			}
			{
				final ChessBoard instance = factory.get();
				final boolean thrown = JavaGradeUtils.doesThrow(() -> instance.setBoardByString(getBoardIllegalPiece()),
						e -> e instanceof IllegalArgumentException);
				gradeBuilder.put(LocalCriterion.BOARD_THROWS_ON_ILLEGAL_PIECE, Mark.binary(setInitReturns && thrown));
			}
			{
				final ChessBoard instance = factory.get();
				final Try<Boolean> init = Try.of(() -> instance.setBoardByString(getBoardInit()));
				gradeBuilder.put(LocalCriterion.NO_CHANGE_STR_INIT,
						Mark.binary(changed && init.equals(Try.success(false))));
			}
			{
				gradeBuilder.put(LocalCriterion.CHANGES_STR, Mark.binary(changed && noChange));
			}
			{
				final ChessBoard instance = factory.get();
				final Try<Boolean> init = Try.of(() -> instance.setBoardByPieces(asPieces(getBoardInit())));
				gradeBuilder.put(LocalCriterion.NO_CHANGE_PIECES_INIT,
						Mark.binary(changedPieces && init.equals(Try.success(false))));
			}
			{
				gradeBuilder.put(LocalCriterion.CHANGES_PIECES, Mark.binary(changedPieces && noChangePieces));
			}

			{
				final ChessBoard instance = factory.get();
				Try.of(() -> instance.setBoardByString(getBoardBK()));
				final Try<ImmutableMap<String, String>> byPos = Try.of(() -> instance.getStringPiecesByPosition());
				gradeBuilder.put(LocalCriterion.GET_POS_MAP_STRING,
						Mark.binary(byPos.equals(Try.success(getBoardBKAsMap()))));
			}
			{
				final ChessBoard instance = factory.get();
				Try.of(() -> instance.setBoardByString(getBoardBK()));
				final Try<ImmutableMap<String, Piece>> byPos = Try.of(() -> instance.getPiecesByPosition());
				gradeBuilder.put(LocalCriterion.GET_POS_MAP_PIECES,
						Mark.binary(byPos.equals(Try.success(asPieces(getBoardBKAsMap())))));
			}
			{
				final ChessBoard instance = factory.get();
				Try.of(() -> instance.setBoardByString(getBoardBK()));
				final Try<Optional<Piece>> a9 = Try.of(() -> instance.getPieceByPosition("a9"));
				final Try<Optional<Piece>> i1 = Try.of(() -> instance.getPieceByPosition("i1"));
				gradeBuilder.put(LocalCriterion.GET_PIECE_ILLEGAL,
						Mark.binary(a9.isFailure() && a9.getCause() instanceof IllegalArgumentException
								&& i1.isFailure() && i1.getCause() instanceof IllegalArgumentException));
			}
			{
				final ChessBoard instance = factory.get();
				Try.of(() -> instance.setBoardByString(getBoardBK()));
				final Try<Optional<Piece>> a2 = Try.of(() -> instance.getPieceByPosition("a2"));
				gradeBuilder.put(LocalCriterion.GET_PIECE_EMPTY,
						Mark.binary(a2.isSuccess() && Optional.empty().equals(a2.get())));
			}
			final Piece WB = Piece.bishop("W");
			final Piece WK = Piece.king("W");
			final Piece BK = Piece.king("B");
			{
				final ChessBoard instance = factory.get();
				Try.of(() -> instance.setBoardByString(getBoardBK()));
				final Try<Optional<Piece>> b2 = Try.of(() -> instance.getPieceByPosition("b2"));
				final Try<Optional<Piece>> d2 = Try.of(() -> instance.getPieceByPosition("d2"));
				final Try<Optional<Piece>> e2 = Try.of(() -> instance.getPieceByPosition("e2"));
				final Try<Optional<Piece>> e8 = Try.of(() -> instance.getPieceByPosition("e8"));
				final boolean b2Ok = b2.isSuccess() && Optional.of(WB).equals(b2.get());
				final boolean d2Ok = d2.isSuccess() && Optional.of(WK).equals(d2.get());
				final boolean e2Ok = e2.isSuccess() && Optional.of(WB).equals(e2.get());
				final boolean e8Ok = e8.isSuccess() && Optional.of(BK).equals(e8.get());
				gradeBuilder.put(LocalCriterion.GET_PIECE_PRESENT, Mark.binary(b2Ok && d2Ok && e2Ok && e8Ok));
			}
			{
				final ChessBoard instance = factory.get();
				Try.of(() -> instance.setBoardByString(getBoardBK()));
				final Try<ImmutableSet<Piece>> white = Try.of(() -> instance.getPieces("W"));
				final Try<ImmutableSet<Piece>> black = Try.of(() -> instance.getPieces("B"));
				gradeBuilder.put(LocalCriterion.GET_PIECES,
						Mark.binary(white.equals(Try.success(ImmutableSet.of(WB, WK)))
								&& black.equals(Try.success(ImmutableSet.of(BK)))));
			}
			{
				final ChessBoard instance = factory.get();
				Try.of(() -> instance.setBoardByString(getBoardBK()));
				final Try<ImmutableList<Piece>> white = Try.of(() -> instance.getOrderedPieces("W"));
				final Try<ImmutableList<Piece>> black = Try.of(() -> instance.getOrderedPieces("B"));
				gradeBuilder.put(LocalCriterion.GET_ORDERED,
						Mark.binary(white.equals(Try.success(ImmutableList.of(WB, WB, WK)))
								&& black.equals(Try.success(ImmutableList.of(BK)))));
			}
			{
				final ChessBoard instance = factory.get();
				Try.of(() -> instance.setBoardByString(getBoardBK()));
				final TryVoid move = TryVoid.run(() -> instance.movePiece("a1", "d7"));
				gradeBuilder.put(LocalCriterion.MOVE_THROWS,
						Mark.binary(move.isFailure() && (move.getCause() instanceof IllegalArgumentException
								|| move.getCause() instanceof IllegalStateException)));
			}
			{
				final ChessBoard instance = factory.get();
				Try.of(() -> instance.setBoardByString(getBoardBK()));
				final TryVoid move = TryVoid.run(() -> instance.movePiece("b2", "c3"));
				final Try<Optional<Piece>> b2 = Try.of(() -> instance.getPieceByPosition("b2"));
				final Try<Optional<Piece>> c3 = Try.of(() -> instance.getPieceByPosition("c3"));
				final Try<Optional<Piece>> d2 = Try.of(() -> instance.getPieceByPosition("d2"));
				final Try<Optional<Piece>> e2 = Try.of(() -> instance.getPieceByPosition("e2"));
				final Try<Optional<Piece>> e8 = Try.of(() -> instance.getPieceByPosition("e8"));
				final boolean b2Ok = b2.isSuccess() && Optional.empty().equals(b2.get());
				final boolean c3Ok = c3.isSuccess() && Optional.of(WB).equals(c3.get());
				final boolean d2Ok = d2.isSuccess() && Optional.of(WK).equals(d2.get());
				final boolean e2Ok = e2.isSuccess() && Optional.of(WB).equals(e2.get());
				final boolean e8Ok = e8.isSuccess() && Optional.of(BK).equals(e8.get());
				gradeBuilder.put(LocalCriterion.MOVE,
						Mark.binary(move.isSuccess() && b2Ok && c3Ok && d2Ok && e2Ok && e8Ok));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		final ImmutableMap<Criterion, IGrade> grade = gradeBuilder.build();

		final ImmutableMap.Builder<Criterion, Double> weightsBuilder = ImmutableMap.builder();
		weightsBuilder.put(LocalCriterion.BOARD_THROWS_ON_NULL, 1d);
		weightsBuilder.put(LocalCriterion.BOARD_THROWS_ON_ONE_KING, 1d);
		weightsBuilder.put(LocalCriterion.BOARD_THROWS_ON_MULT_KINGS, 1d);
		weightsBuilder.put(LocalCriterion.BOARD_THROWS_ON_ILLEGAL_PIECE, 1d);
		weightsBuilder.put(LocalCriterion.NO_CHANGE_STR_INIT, 1d);
		weightsBuilder.put(LocalCriterion.CHANGES_STR, 1d);
		weightsBuilder.put(LocalCriterion.NO_CHANGE_PIECES_INIT, 1d);
		weightsBuilder.put(LocalCriterion.CHANGES_PIECES, 1d);
		weightsBuilder.put(LocalCriterion.GET_POS_MAP_STRING, 2d);
		weightsBuilder.put(LocalCriterion.GET_POS_MAP_PIECES, 2d);
		weightsBuilder.put(LocalCriterion.GET_PIECE_ILLEGAL, 0.5d);
		weightsBuilder.put(LocalCriterion.GET_PIECE_EMPTY, 1.5d);
		weightsBuilder.put(LocalCriterion.GET_PIECE_PRESENT, 1.5d);
		weightsBuilder.put(LocalCriterion.GET_PIECES, 2d);
		weightsBuilder.put(LocalCriterion.GET_ORDERED, 2d);
		weightsBuilder.put(LocalCriterion.MOVE_THROWS, 1d);
		weightsBuilder.put(LocalCriterion.MOVE, 1.5d);
		return WeightingGrade.from(grade, weightsBuilder.build());
	}

	private ImmutableList<Optional<Piece>> asPieces(List<String> board) {
		return board.stream()
				.map(s -> s.isEmpty() ? Optional.<Piece>empty()
						: Optional.<Piece>of(Piece.given(s.substring(0, 1), s.substring(1, 2))))
				.collect(ImmutableList.toImmutableList());
	}

	private ImmutableMap<String, Piece> asPieces(Map<String, String> board) {
		return board.entrySet().stream().collect(ImmutableMap.toImmutableMap(Map.Entry::getKey,
				e -> Piece.given(e.getValue().substring(0, 1), e.getValue().substring(1, 2))));
	}

	private ImmutableList<String> getBoardInit() {
		final ImmutableList<String> row8 = ImmutableList.of("", "", "", "", "BK", "", "", "");
		final ImmutableList<String> row7 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row6 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row5 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row4 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row3 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row2 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row1 = ImmutableList.of("", "", "", "", "WK", "", "", "");
		return ImmutableList.of(row1, row2, row3, row4, row5, row6, row7, row8).stream().flatMap(ImmutableList::stream)
				.collect(ImmutableList.toImmutableList());
	}

	private ImmutableList<String> getBoardBK() {
		final ImmutableList<String> row8 = ImmutableList.of("", "", "", "", "BK", "", "", "");
		final ImmutableList<String> row7 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row6 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row5 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row4 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row3 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row2 = ImmutableList.of("", "WB", "", "WK", "WB", "", "", "");
		final ImmutableList<String> row1 = ImmutableList.of("", "", "", "", "", "", "", "");
		return ImmutableList.of(row1, row2, row3, row4, row5, row6, row7, row8).stream().flatMap(ImmutableList::stream)
				.collect(ImmutableList.toImmutableList());
	}

	private ImmutableList<String> getBoardBK2() {
		final ImmutableList<String> row8 = ImmutableList.of("", "", "", "", "BK", "", "", "");
		final ImmutableList<String> row7 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row6 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row5 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row4 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row3 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row2 = ImmutableList.of("", "", "", "WK", "WB", "", "", "");
		final ImmutableList<String> row1 = ImmutableList.of("", "WB", "", "", "", "", "", "");
		return ImmutableList.of(row1, row2, row3, row4, row5, row6, row7, row8).stream().flatMap(ImmutableList::stream)
				.collect(ImmutableList.toImmutableList());
	}

	private ImmutableList<String> getBoardOneK() {
		final ImmutableList<String> row8 = ImmutableList.of("", "", "", "", "BK", "", "", "");
		final ImmutableList<String> row7 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row6 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row5 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row4 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row3 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row2 = ImmutableList.of("", "WB", "", "", "WB", "", "", "");
		final ImmutableList<String> row1 = ImmutableList.of("", "", "", "", "", "", "", "");
		return ImmutableList.of(row1, row2, row3, row4, row5, row6, row7, row8).stream().flatMap(ImmutableList::stream)
				.collect(ImmutableList.toImmutableList());
	}

	private ImmutableList<String> getBoardThreeK() {
		final ImmutableList<String> row8 = ImmutableList.of("", "", "", "", "BK", "", "", "");
		final ImmutableList<String> row7 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row6 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row5 = ImmutableList.of("", "", "", "", "", "", "WK", "");
		final ImmutableList<String> row4 = ImmutableList.of("", "", "", "", "WK", "", "", "");
		final ImmutableList<String> row3 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row2 = ImmutableList.of("", "WB", "", "", "WB", "", "", "");
		final ImmutableList<String> row1 = ImmutableList.of("", "", "", "", "", "", "", "");
		return ImmutableList.of(row1, row2, row3, row4, row5, row6, row7, row8).stream().flatMap(ImmutableList::stream)
				.collect(ImmutableList.toImmutableList());
	}

	private ImmutableList<String> getBoardIllegalPiece() {
		final ImmutableList<String> row8 = ImmutableList.of("", "", "", "", "BK", "", "", "");
		final ImmutableList<String> row7 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row6 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row5 = ImmutableList.of("", "", "", "", "", "", "RN", "");
		final ImmutableList<String> row4 = ImmutableList.of("", "", "", "", "WK", "", "", "");
		final ImmutableList<String> row3 = ImmutableList.of("", "", "", "", "", "", "", "");
		final ImmutableList<String> row2 = ImmutableList.of("", "WB", "", "", "WB", "", "", "");
		final ImmutableList<String> row1 = ImmutableList.of("", "", "", "", "", "", "", "");
		return ImmutableList.of(row1, row2, row3, row4, row5, row6, row7, row8).stream().flatMap(ImmutableList::stream)
				.collect(ImmutableList.toImmutableList());
	}

	private ImmutableMap<String, String> getBoardBKAsMap() {
		return ImmutableMap.<String, String>of("b2", "WB", "d2", "WK", "e2", "WB", "e8", "BK");
	}

}
