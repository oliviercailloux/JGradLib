package io.github.oliviercailloux.javagrade.graders;

import static io.github.oliviercailloux.grade.GitGrader.Functions.resolve;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.compose;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.contentMatches;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.isRefBranch;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.graph.ImmutableGraph;
import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.gitjfs.GitPathRootRef;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.DeadlineGrader;
import io.github.oliviercailloux.grade.GitGeneralGrader;
import io.github.oliviercailloux.grade.GitGrader;
import io.github.oliviercailloux.grade.GitWork;
import io.github.oliviercailloux.grade.GradeUtils;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.RepositoryFetcher;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.WeightingGrader;
import io.github.oliviercailloux.grade.WeightingGrader.CriterionGraderWeight;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.jaris.throwing.TFunction;
import io.github.oliviercailloux.jaris.throwing.TPredicate;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitBranching implements GitGrader {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(GitBranching.class);

    public static final String PREFIX = "git-branching";

    public static final ZonedDateTime DEADLINE =
            ZonedDateTime.parse("2021-01-13T14:17:00+01:00[Europe/Paris]");

    public static void main(String[] args) throws Exception {
        final RepositoryFetcher fetcher = RepositoryFetcher.withPrefix(PREFIX);
        GitGeneralGrader.using(fetcher, DeadlineGrader.usingGitGrader(new GitBranching(), DEADLINE))
                .grade();
    }

    GitBranching() {}

    @Override
    public WeightingGrade grade(GitWork work) throws IOException {
        final GitHistorySimple history = work.getHistory();
        final ImmutableSet<GitPathRootRef> refs = history.fs().refs();

        final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

        {
            final Mark hasCommit = Mark.binary(!history.graph().nodes().isEmpty());
            gradeBuilder
                    .add(CriterionGradeWeight.from(Criterion.given("At least one"), hasCommit, 1d));
        }

        final Function<GitPathRootShaCached, Set<GitPathRootShaCached>> graphSiblings =
                r -> getSingleParent(history, r).map(p -> history.graph().successors(p))
                        .orElse(ImmutableSet.of(r));
        final TFunction<GitPathRootShaCached, Set<GitPathRootShaCached>, IOException> siblings =
                r -> graphSiblings.apply(r);
        final TPredicate<GitPathRootShaCached, IOException> hasAFewSiblings =
                compose(siblings, s -> 2 <= s.size() && s.size() <= 3);
        final CriterionGraderWeight<GitPathRootShaCached> graderTopoSiblings = CriterionGraderWeight
                .given(Criterion.given("Father has 2 or 3 children"), hasAFewSiblings, 2d);
        final ImmutableGraph<GitPathRootShaCached> graph = history.graph();
        // final TPredicate<GitPathRootSha, IOException> aBranch = compose(history::getRefsTo,
        // anyMatch(isBranch("br1")));
        final TPredicate<GitPathRootShaCached, IOException> aBranch =
                compose(p -> GradeUtils.getRefsTo(refs, p), anyMatch(isRefBranch("br1")));
        {
            final Pattern helloPattern = Marks.extendWhite("hello\\h+world");
            final CriterionGraderWeight<GitPathRootShaCached> graderBr1 =
                    CriterionGraderWeight.given(Criterion.given("Branch br1"), aBranch, 2d);
            final CriterionGraderWeight<GitPathRootShaCached> graderContent =
                    CriterionGraderWeight.given(Criterion.given("first.txt content"),
                            compose(resolve("first.txt"), contentMatches(helloPattern)), 1d);

            final IGrade grade =
                    GradeUtils.getBestGrade(
                            graph.nodes(), WeightingGrader.getGrader(ImmutableList
                                    .of(graderTopoSiblings, graderBr1, graderContent))::getGrade,
                            1d);

            final CriterionGradeWeight aGrade =
                    CriterionGradeWeight.from(Criterion.given("Commit A"), grade, 5d);
            gradeBuilder.add(aGrade);
        }
        {
            final Pattern coucouPattern = Marks.extendWhite("coucou\\h+monde");
            final CriterionGraderWeight<GitPathRootShaCached> graderContent =
                    CriterionGraderWeight.given(Criterion.given("first.txt content"),
                            compose(resolve("first.txt"), contentMatches(coucouPattern)), 1d);

            final IGrade grade =
                    GradeUtils.getBestGrade(graph.nodes(),
                            WeightingGrader.getGrader(
                                    ImmutableList.of(graderTopoSiblings, graderContent))::getGrade,
                            1d);

            final CriterionGradeWeight bGrade =
                    CriterionGradeWeight.from(Criterion.given("Commit B"), grade, 3d);
            gradeBuilder.add(bGrade);
        }
        final TFunction<GitPathRootShaCached, Set<GitPathRootShaCached>,
                IOException> fatherSiblings = r -> getSingleParent(history, r).map(graphSiblings)
                        .orElse(ImmutableSet.of());
        final TPredicate<GitPathRootShaCached, IOException> hasCTopo =
                compose(fatherSiblings, s -> 2 <= s.size() && s.size() <= 3);
        final TPredicate<GitPathRootShaCached, IOException> cBranch =
                compose(p -> GradeUtils.getRefsTo(refs, p), anyMatch(isRefBranch("br2")));
        {
            final CriterionGraderWeight<GitPathRootShaCached> graderTopoC = CriterionGraderWeight
                    .given(Criterion.given("Grand-father has 2 or 3 children"), hasCTopo, 2d);
            final CriterionGraderWeight<GitPathRootShaCached> graderBr2 =
                    CriterionGraderWeight.given(Criterion.given("Branch br2"), cBranch, 2d);
            final CriterionGraderWeight<GitPathRootShaCached> graderContent = CriterionGraderWeight
                    .given(Criterion.given("content"), compose(resolve("a/b/c/x/z/some file.txt"),
                            contentMatches(Marks.extendWhite("2021"))), 1d);

            final IGrade grade =
                    GradeUtils
                            .getBestGrade(
                                    graph.nodes(), WeightingGrader.getGrader(ImmutableList
                                            .of(graderTopoC, graderBr2, graderContent))::getGrade,
                                    1d);

            final CriterionGradeWeight cGrade =
                    CriterionGradeWeight.from(Criterion.given("Commit C"), grade, 5d);
            gradeBuilder.add(cGrade);
        }
        {
            /**
             * Topo D: has exactly two parents; one which is a C (right branch OR C Topo) and one
             * which is is an A (right branch or A topo). Thus the parents must include at least one
             * A, at least one C, and no nothing.
             */
            final TPredicate<GitPathRootShaCached, IOException> isA = aBranch.or(hasAFewSiblings);
            final TPredicate<GitPathRootShaCached, IOException> isC = cBranch.or(hasCTopo);
            final TFunction<GitPathRootShaCached, ImmutableSet<GitPathRootShaCached>,
                    IOException> parents = r -> ImmutableSet.copyOf(graph.predecessors(r));
            final TPredicate<ImmutableSet<GitPathRootShaCached>, IOException> aAndC =
                    s -> s.size() == 2
                            && (isA.test(s.asList().get(0)) && isC.test(s.asList().get(1)));
            final TPredicate<ImmutableSet<GitPathRootShaCached>, IOException> cAndA =
                    s -> s.size() == 2
                            && (isC.test(s.asList().get(0)) && isA.test(s.asList().get(1)));
            final TPredicate<GitPathRootShaCached, IOException> twoRightParents =
                    compose(parents, aAndC.or(cAndA));
            final TPredicate<GitPathRootShaCached, IOException> dBranch =
                    compose(p -> GradeUtils.getRefsTo(refs, p), anyMatch(isRefBranch("br3")));

            final CriterionGraderWeight<GitPathRootShaCached> graderTopo = CriterionGraderWeight
                    .given(Criterion.given("Has apparent A and C parents"), twoRightParents, 2d);
            final CriterionGraderWeight<GitPathRootShaCached> graderBr3 =
                    CriterionGraderWeight.given(Criterion.given("Branch br3"), dBranch, 2d);
            final CriterionGraderWeight<GitPathRootShaCached> graderContentSomeFile =
                    CriterionGraderWeight.given(Criterion.given("some file"),
                            compose(resolve("a/b/c/x/z/some file.txt"),
                                    contentMatches(Marks.extendWhite("2021"))),
                            1d);
            final TPredicate<Path, IOException> merged =
                    contentMatches(Marks.extendAll("<<<<<<<")).negate();
            final TPredicate<Path, IOException> matchesApprox =
                    contentMatches(Marks.extendAll("hello\\h+world"))
                            .or(contentMatches(Marks.extendAll("coucou\\h+monde")));
            final CriterionGraderWeight<GitPathRootShaCached> graderContentFirstApprox =
                    CriterionGraderWeight.given(Criterion.given("approx"),
                            compose(resolve("first.txt"), matchesApprox.and(merged)), 1d);
            final CriterionGraderWeight<GitPathRootShaCached> graderContentFirstExact =
                    CriterionGraderWeight.given(Criterion.given("exact"),
                            compose(resolve("first.txt"),
                                    contentMatches(
                                            Pattern.compile("hello world\\v+coucou monde\\v*"))
                                                    .and(merged)),
                            1d);
            final CriterionGraderWeight<GitPathRootShaCached> graderContentFirst =
                    CriterionGraderWeight.given(Criterion.given("content"),
                            WeightingGrader.getGrader(ImmutableList.of(graderContentFirstApprox,
                                    graderContentFirstExact))::getGrade,
                            1d);
            final CriterionGraderWeight<
                    GitPathRootShaCached> graderContent =
                            CriterionGraderWeight.given(Criterion.given("content"),
                                    WeightingGrader.getGrader(ImmutableList.of(
                                            graderContentSomeFile, graderContentFirst))::getGrade,
                                    2d);

            final TFunction<Optional<GitPathRootShaCached>, IGrade, IOException> grader =
                    WeightingGrader.getGrader(
                            ImmutableList.of(graderTopo, graderBr3, graderContent))::getGrade;
            final IGrade grade = GradeUtils.getBestGrade(graph.nodes(), grader, 1d);

            final CriterionGradeWeight dGrade =
                    CriterionGradeWeight.from(Criterion.given("Commit D"), grade, 6d);
            gradeBuilder.add(dGrade);
        }

        return WeightingGrade.from(gradeBuilder.build());
    }

    private TPredicate<Set<GitPathRootRef>, IOException>
            anyMatch(TPredicate<GitPathRootRef, IOException> predicate) {
        final TPredicate<Set<GitPathRootRef>, IOException> anyMatchPredicate = s -> {
            for (GitPathRootRef r : s) {
                if (predicate.test(r)) {
                    return true;
                }
            }
            return false;
        };
        return anyMatchPredicate;
    }

    private Optional<GitPathRootShaCached> getSingleParent(GitHistorySimple history,
            GitPathRootShaCached r) {
        final Set<GitPathRootShaCached> parents =
                history.graph().predecessors(r).stream().collect(ImmutableSet.toImmutableSet());
        if (parents.size() == 1) {
            return Optional.of(Iterables.getOnlyElement(parents));
        }
        return Optional.empty();
    }
}
