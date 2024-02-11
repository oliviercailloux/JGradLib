package io.github.oliviercailloux.grade;

import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.grade.DeadlineGrader.LOGGER;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.jaris.collections.CollectionUtils;
import io.github.oliviercailloux.jaris.exceptions.CheckedStream;
import io.github.oliviercailloux.jaris.io.PathUtils;
import io.github.oliviercailloux.java_grade.JavaGradeUtils;
import io.github.oliviercailloux.java_grade.bytecode.Compiler;
import io.github.oliviercailloux.java_grade.bytecode.Compiler.CompilationResult;
import io.github.oliviercailloux.java_grade.bytecode.Compiler.CompilationResultExt;
import io.github.oliviercailloux.java_grade.bytecode.MyCompiler;
import io.github.oliviercailloux.utils.Utils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MavenCodeGrader<X extends Exception> implements PathGrader<X> {
  public static enum WarningsBehavior {
    DO_NOT_PENALIZE, PENALIZE_WARNINGS_AND_NOT_SUPPRESSED, PENALIZE_WARNINGS_AND_SUPPRESS;

    public boolean penalizeWarnings() {
      return ImmutableSet.of(PENALIZE_WARNINGS_AND_NOT_SUPPRESSED, PENALIZE_WARNINGS_AND_SUPPRESS)
          .contains(this);
    }
  }

  public static <X extends Exception> MavenCodeGrader<X> penal(CodeGrader<X> g,
      Function<IOException, X> wrapper, WarningsBehavior w) {
    return new MavenCodeGrader<>(g, wrapper, w, new BasicCompiler());
  }

  public static <X extends Exception> MavenCodeGrader<X> basic(CodeGrader<X> g,
      Function<IOException, X> wrapper) {
    return new MavenCodeGrader<>(g, wrapper, WarningsBehavior.PENALIZE_WARNINGS_AND_SUPPRESS,
        new BasicCompiler());
  }

  public static <X extends Exception> MavenCodeGrader<X> complex(CodeGrader<X> g,
      Function<IOException, ? extends X> wrapper, boolean considerSuppressed, MyCompiler compiler) {
    return new MavenCodeGrader<>(g, wrapper,
        considerSuppressed ? WarningsBehavior.PENALIZE_WARNINGS_AND_SUPPRESS
            : WarningsBehavior.PENALIZE_WARNINGS_AND_NOT_SUPPRESSED,
        compiler);
  }

  public static class BasicCompiler implements MyCompiler {

    @Override
    public CompilationResultExt compile(Path compiledDir, Set<Path> javaPaths) throws IOException {
      final CompilationResult eclipseResult =
          Compiler.eclipseCompileUsingOurClasspath(javaPaths, compiledDir);
      final Pattern pathPattern = Pattern.compile("/tmp/sources[0-9]*/");
      final String eclipseStr = pathPattern.matcher(eclipseResult.err).replaceAll("/…/");
      final int nbSuppressed = (int) CheckedStream.<Path, IOException>wrapping(javaPaths.stream())
          .map(p -> Files.readString(p))
          .flatMap(s -> Pattern.compile("@SuppressWarnings").matcher(s).results()).count();
      final CompilationResultExt transformedResult = Compiler.CompilationResultExt
          .given(eclipseResult.compiled, eclipseResult.out, eclipseStr, nbSuppressed);
      return transformedResult;
    }
  }

  private final CodeGrader<X> g;
  private final Function<IOException, ? extends X> wrapper;

  public static final Criterion WARNING_CRITERION = Criterion.given("Warnings");
  public static final Criterion CODE_CRITERION = Criterion.given("Code");
  private ImmutableMap<Path, MarksTree> gradedProjects;
  private final MyCompiler basicCompiler;
  private WarningsBehavior w;

  private MavenCodeGrader(CodeGrader<X> g, Function<IOException, ? extends X> wrapper,
      WarningsBehavior w, MyCompiler basicCompiler) {
    this.g = g;
    gradedProjects = null;
    this.wrapper = wrapper;
    this.w = w;
    this.basicCompiler = basicCompiler;
  }

  @Override
  public MarksTree grade(Path workDir) throws X {
    try {
      final ImmutableSet<Path> possibleDirs = MavenCodeGrader.possibleDirs(workDir);
      gradedProjects = CollectionUtils.toMap(possibleDirs, this::gradePomProjectWrapping);
      verify(!gradedProjects.isEmpty());
      final ImmutableMap<Criterion, MarksTree> gradedProjectsByCrit = CollectionUtils
          .transformKeys(gradedProjects, p -> Criterion.given("Using project dir " + p.toString()));
      return MarksTree.composite(gradedProjectsByCrit);
    } catch (IOException e) {
      throw wrapper.apply(e);
    }
  }

  private MarksTree gradePomProjectWrapping(Path pomDirectory) throws X {
    try {
      return gradePomProject(pomDirectory);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private MarksTree gradePomProject(Path pomDirectory) throws IOException, X {
    final Path compiledDir = Utils.getTempUniqueDirectory("compile");
    final Path srcDir;
    final boolean hasPom = Files.exists(pomDirectory.resolve("pom.xml"));
    if (hasPom) {
      srcDir = pomDirectory.resolve("src/main/java/");
    } else {
      // srcDir = projectDirectory.resolve("src/main/java/");
      srcDir = pomDirectory;
    }
    final ImmutableSet<Path> javaPaths =
        Files.exists(srcDir)
            ? PathUtils.getMatchingChildren(srcDir,
                p -> String.valueOf(p.getFileName()).endsWith(".java"))
            : ImmutableSet.of();
    final CompilationResultExt result = basicCompiler.compile(compiledDir, javaPaths);

    final MarksTree projectGrade;
    if (result.countErrors() > 0) {
      projectGrade = Mark.zero(result.err);
    } else if (javaPaths.isEmpty()) {
      LOGGER.debug("No java files at {}.", srcDir);
      projectGrade = Mark.zero("No java files found");
    } else {
      final MarksTree codeGrade = JavaGradeUtils.markSecurely(compiledDir, g::gradeCode);
      if (w.penalizeWarnings()) {
        final Mark weightingMark;
        {
          final int nbCountedSW =
              (w == WarningsBehavior.PENALIZE_WARNINGS_AND_SUPPRESS) ? result.nbSuppressWarnings
                  : 0;
          final String comment;
          {
            final ImmutableSet.Builder<String> commentsBuilder = ImmutableSet.builder();
            if (result.countWarnings() > 0) {
              commentsBuilder.add(result.err);
            }
            if (nbCountedSW > 0) {
              commentsBuilder.add("Found " + result.nbSuppressWarnings + " suppressed warnings");
            }
            comment = commentsBuilder.build().stream().collect(Collectors.joining(". "));
          }
          final double penalty = Math.min((result.countWarnings() + nbCountedSW) * 0.05d, 0.1d);
          final double weightingScore = 1d - penalty;
          weightingMark = Mark.given(weightingScore, comment);
        }
        projectGrade = MarksTree.composite(
            ImmutableMap.of(WARNING_CRITERION, weightingMark, CODE_CRITERION, codeGrade));
      } else {
        projectGrade = codeGrade;
      }
    }
    return projectGrade;
  }

  @Override
  public GradeAggregator getAggregator() {
    if (w.penalizeWarnings()) {
      return GradeAggregator.min(
          GradeAggregator.parametric(CODE_CRITERION, WARNING_CRITERION, g.getCodeAggregator()));
    }
    return GradeAggregator.min(g.getCodeAggregator());
  }

  public static ImmutableSet<Path> possibleDirs(Path projectPath) throws IOException {
    final ImmutableSet<Path> poms =
        PathUtils.getMatchingChildren(projectPath, p -> p.endsWith("pom.xml"));
    LOGGER.debug("Poms: {}.", poms);
    final ImmutableSet<Path> pomsWithJava;
    pomsWithJava = CheckedStream.<Path, IOException>wrapping(poms.stream())
        .filter(p -> !PathUtils
            .getMatchingChildren(p, s -> String.valueOf(s.getFileName()).endsWith(".java"))
            .isEmpty())
        .collect(ImmutableSet.toImmutableSet());
    LOGGER.debug("Poms with java: {}.", pomsWithJava);
    final ImmutableSet<Path> possibleDirs;
    if (pomsWithJava.isEmpty()) {
      possibleDirs = ImmutableSet.of(projectPath);
    } else {
      possibleDirs =
          pomsWithJava.stream().map(Path::getParent).collect(ImmutableSet.toImmutableSet());
    }
    return possibleDirs;
  }

  public ImmutableMap<Path, MarksTree> getGradedProjects() {
    return gradedProjects;
  }
}
