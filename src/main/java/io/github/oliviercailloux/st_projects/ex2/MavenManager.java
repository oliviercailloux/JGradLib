package io.github.oliviercailloux.st_projects.ex2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class MavenManager {
	public boolean compile(Path pom) {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setInputStream(new ByteArrayInputStream(new byte[] {}));
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		request.setOutputHandler(new PrintStreamHandler(new PrintStream(baos), true));
		request.setPomFile(pom.toFile());
		request.setGoals(ImmutableList.of("compile"));
		Invoker invoker = new DefaultInvoker();
		invoker.setLocalRepositoryDirectory(new File("/home/olivier/.m2/repository"));
		invoker.setMavenHome(new File("/usr/share/maven"));
		final InvocationResult result;
		try {
			result = invoker.execute(request);
		} catch (MavenInvocationException e) {
			throw new IllegalStateException(e);
		}

		LOGGER.debug("Maven output: {}.", new String(baos.toByteArray(), StandardCharsets.UTF_8));

		return (result.getExitCode() == 0);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(MavenManager.class);
}
