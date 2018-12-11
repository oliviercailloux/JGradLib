package io.github.oliviercailloux.st_projects.ex2;

import java.util.Arrays;
import java.util.List;

import javax.json.bind.annotation.JsonbProperty;

public class SimpleOperation {
	private String operation;
	@JsonbProperty("include-runtime")
	private boolean includeRuntime;
	public boolean recursive;
	private List<String> address;

	public SimpleOperation(String operation, boolean includeRuntime, boolean recursive, String... address) {
		this.operation = operation;
		this.includeRuntime = includeRuntime;
		this.address = Arrays.asList(address);
	}

	// getters and setters.
}