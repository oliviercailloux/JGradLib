package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.jimfs.Jimfs;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import io.github.oliviercailloux.gitjfs.GitPath;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.CodeGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcher;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.GitFsGraderUsingLast;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.MavenCodeGrader;
import io.github.oliviercailloux.grade.MavenCodeGrader.BasicCompiler;
import io.github.oliviercailloux.grade.utils.LogCaptor;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.jaris.throwing.TConsumer;
import io.github.oliviercailloux.java_grade.bytecode.Compiler.CompilationResultExt;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import io.github.oliviercailloux.java_grade.bytecode.MyCompiler;
import io.github.oliviercailloux.vexam.Various;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraderVarious implements CodeGrader<RuntimeException> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GraderVarious.class);

	public GraderVarious() {
	}

	private TryCatchAll<Various> newInstance(Instanciator instanciator) {
		final TryCatchAll<Various> tryTarget = TryCatchAll.get(() -> instanciator.getInstanceOrThrow(Various.class));
		final TryCatchAll<Various> instance = tryTarget.andApply(
				target -> null);
		return instance;
	}

	@Override
	public MarksTree gradeCode(Instanciator instanciator) {

		final TryCatchAll<Various> v0 = newInstance(instanciator);
		final String invocationErrorStr = v0.map(r -> "", c -> "Invocation failed: %s".formatted(c));
		if (!invocationErrorStr.isEmpty()) {
			return Mark.zero(invocationErrorStr);
		}

		{
			final TryCatchAll<Various> v = newInstance(instanciator);
			TConsumer<? super Various, ?> consumer = Various::log;
			final TryCatchAll<Various> got = v.andConsume(consumer);
			LOGGER.info("Got: {}.", got);
		}

		return null;
	}

	@Override
	public GradeAggregator getCodeAggregator() {
		return null;
	}

	public void close() {
		
	}
}
