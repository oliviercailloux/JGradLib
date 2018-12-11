package io.github.oliviercailloux.st_projects.servlets;

import java.util.Map;

import javax.json.stream.JsonGenerator;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import com.google.common.collect.ImmutableMap;

@ApplicationPath("v1")
public class MyJaxRsApp extends Application {
	@Override
	public Map<String, Object> getProperties() {
		return ImmutableMap.of(JsonGenerator.PRETTY_PRINTING, true);
	}
}
