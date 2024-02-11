package io.github.oliviercailloux.grade.contexters;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.grade.GradingException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenManager {

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(MavenManager.class);
  private String output;
  private static final String ANSI_ESCAPE_CODES_REG_EXP = "\u001B\\[[;\\d]*[ -/]*[@-~]";
  private Path lastPom;

  public MavenManager() {
    output = null;
  }

  public boolean compile(Path pom) throws GradingException {
    return command(pom, "compile");
  }

  public boolean test(Path pom) throws GradingException {
    return command(pom, "test");
  }

  public ImmutableList<Path> getClassPath(Path pom) throws GradingException {
    final boolean succeeded =
        command(pom, "org.apache.maven.plugins:maven-dependency-plugin:3.1.2:build-classpath");
    checkArgument(succeeded);
    final Matcher matcher =
        Pattern.compile("\\[INFO\\] Dependencies classpath:\\n(?<cp>[^\\n]*)\\n").matcher(output);
    final boolean foundFirst = matcher.find();
    checkArgument(foundFirst, output);
    final String cp = matcher.group("cp");
    final boolean foundSecond = matcher.find();
    checkArgument(!foundSecond);
    final ImmutableList<Path> paths =
        Splitter.on(':').splitToStream(cp).map(Path::of).collect(ImmutableList.toImmutableList());
    verify(!paths.isEmpty());
    return paths;
  }

  private boolean command(Path pom, String goal) throws GradingException {
    this.lastPom = checkNotNull(pom);
    InvocationRequest request = new DefaultInvocationRequest();
    request.setInputStream(new ByteArrayInputStream(new byte[] {}));
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    request.setOutputHandler(new PrintStreamHandler(new PrintStream(baos), true));
    request.setPomFile(pom.toFile());
    request.setGoals(ImmutableList.of(goal));
    // if (!enableTests) {
    // final Properties properties = new Properties();
    // properties.setProperty("skipTests", "true");
    // request.setProperties(properties);
    // }
    Invoker invoker = new DefaultInvoker();
    invoker.setLocalRepositoryDirectory(Path.of("/home/olivier/.m2/repository").toFile());
    invoker.setMavenHome(Path.of("/usr/share/maven").toFile());
    final InvocationResult result;
    try {
      result = invoker.execute(request);
    } catch (MavenInvocationException e) {
      throw new GradingException(e);
    }

    final String withAnsiEscapes = new String(baos.toByteArray(), StandardCharsets.UTF_8);
    output = withAnsiEscapes.replaceAll(ANSI_ESCAPE_CODES_REG_EXP, "");
    LOGGER.debug("Maven output: {}.", output);

    return (result.getExitCode() == 0);
  }

  public String getOutput() {
    checkState(output != null);
    return output;
  }

  public String getCensoredOutput() {
    checkState(output != null);
    verify(lastPom != null);
    return output.replaceAll(lastPom.toAbsolutePath().toString() + "/", "");
  }
}
