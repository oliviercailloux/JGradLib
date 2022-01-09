package io.github.oliviercailloux.grade.contexters;

import com.google.common.base.MoreObjects;
import com.google.common.io.CharStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ProcessRunner {
	public static ProcessOutput run(File workingDirectory, List<String> toRun) {
		final ProcessBuilder builder = new ProcessBuilder();
		builder.directory(workingDirectory);
		builder.command(toRun);

		final Process process;
		try {
			process = builder.start();
			process.getOutputStream().close();
			process.waitFor(20, TimeUnit.SECONDS);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
		final InputStream inputStream = process.getInputStream();
		final InputStream errorStream = process.getErrorStream();
		final String output;
		final String error;
		try {
			try (InputStreamReader r = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
					InputStreamReader rError = new InputStreamReader(errorStream, StandardCharsets.UTF_8)) {
				output = CharStreams.toString(r);
				error = CharStreams.toString(rError);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return new ProcessOutput(output, error);
	}

	public static class ProcessOutput {
		private String output;
		private String error;

		ProcessOutput(String output, String error) {
			this.output = output;
			this.error = error;
		}

		public String getOutput() {
			return output;
		}

		public String getError() {
			return error;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this).add("output", output).add("error", error).toString();
		}
	}
}
