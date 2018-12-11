package io.github.oliviercailloux.st_projects.ex2;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.xml.ws.Response;

public class WF {
	public static void main(String[] args) {
		new WF().proceed();
	}

	private void proceed() {

	}

	private void proceedWrongly() {
		Object feature = null;
//		feature = HttpAuthenticationFeature.digest("olivier", "…");
		Client client = ClientBuilder.newClient();
		client.register(feature);
		Entity<SimpleOperation> operation = Entity.entity(
				new SimpleOperation("read-resource", true, false, "subsystem", "undertow", "server", "default-server"),
				MediaType.APPLICATION_JSON_TYPE);
		WebTarget managementResource = client.target("http://localhost:9990/management");
		Response response = managementResource.request(MediaType.APPLICATION_JSON_TYPE)
				.header("Content-type", MediaType.APPLICATION_JSON).post(operation, Response.class);
		System.out.println(response);

	}
}
