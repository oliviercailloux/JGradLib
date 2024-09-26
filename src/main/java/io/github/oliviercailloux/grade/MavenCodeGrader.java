package io.github.oliviercailloux.grade;

import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.grade.DeadlineGrader.LOGGER;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.MavenCodeHelper.BasicCompiler;
import io.github.oliviercailloux.grade.MavenCodeHelper.WarningsBehavior;
import io.github.oliviercailloux.jaris.collections.CollectionUtils;
import io.github.oliviercailloux.jaris.exceptions.CheckedStream;
import io.github.oliviercailloux.jaris.io.PathUtils;
import io.github.oliviercailloux.javagrade.JavaGradeUtils;
import io.github.oliviercailloux.javagrade.bytecode.Compiler;
import io.github.oliviercailloux.javagrade.bytecode.Compiler.CompilationResult;
import io.github.oliviercailloux.javagrade.bytecode.Compiler.CompilationResultExt;
import io.github.oliviercailloux.javagrade.bytecode.MyCompiler;
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

  private final MavenCodeHelper<X> helper;
  private final Function<IOException, ? extends X> wrapper;
  private ImmutableMap<Path, MarksTree> gradedProjects;
  private final CodeGrader<X> grader;

  private MavenCodeGrader(CodeGrader<X> g, Function<IOException, ? extends X> wrapper,
      WarningsBehavior w, MyCompiler basicCompiler) {
    gradedProjects = null;
    this.wrapper = wrapper;
    this.grader = g;
    this.helper = new MavenCodeHelper<>(w, basicCompiler);
  }

  @Override
  public MarksTree grade(Path workDir) throws X {
    try {
      final ImmutableSet<Path> possibleDirs = MavenCodeHelper.possibleDirs(workDir);
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
      final CompilationResultExt result = helper.compile(pomDirectory);
      final MarksTree projectGrade = grade(result);
      return projectGrade;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private MarksTree grade(CompilationResultExt result) throws X {
    return helper.grade(result, grader);
  }

  @Override
  public GradeAggregator getAggregator() {
    return helper.getAggregator(grader);
  }

  public ImmutableMap<Path, MarksTree> getGradedProjects() {
    return gradedProjects;
  }
}
