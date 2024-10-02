package io.github.oliviercailloux.utils;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import io.github.oliviercailloux.jaris.credentials.CredentialsReader;
import io.github.oliviercailloux.jaris.xml.DomHelper;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.transform.stream.StreamSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Moodle {
  private static final String MOODLE_SERVER =
      // "https://moodle-qualif.psl.eu/webservice/rest/server.php";
      "https://moodle-test.psl.eu/webservice/rest/server.php";

  private static final int COURSE_ID = 24705;

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(Moodle.class);

  Client client = ClientBuilder.newClient();

  public static void main(String[] args) {
    // int courseId = new Moodle().courseId("23_CIP_test_autograder");
    // checkState(courseId == COURSE_ID);
    // ImmutableSet<Integer> assignmentIds = new Moodle().assignmentIds(COURSE_ID);
    // LOGGER.info("Assignments: {}.", assignmentIds);
    Moodle moodle = new Moodle();
    ImmutableSet<JsonObject> grades = moodle.grades(9476);
    LOGGER.info("Course ID: {}.", grades);
  }

  public void plugins() {
    String plugins = queryPlugins("json");
    LOGGER.info("Plugins: {}.", plugins);
    try {
      Files.writeString(Path.of("answer.json"), plugins);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    JsonObject json;
    try (JsonReader jr = Json.createReader(new StringReader(plugins))) {
      json = jr.readObject();
    }
    checkState(json.containsKey("warnings"));
    JsonArray warnings = json.getJsonArray("warnings");
    checkState(warnings.isEmpty(), warnings);
    checkState(json.containsKey("plugins"));
    JsonArray pluginss = json.getJsonArray("plugins");
    ImmutableSet<JsonObject> pluginsSet = pluginss.stream().map(v -> (JsonObject)v).collect(ImmutableSet.toImmutableSet());
    pluginsSet.forEach(p -> LOGGER.info("Plugin: {}.", p.getString("component") + ":" + p.getString("addon")));
  }

  public ImmutableSet<JsonObject> grades(int assignmentId) {
    String grades = queryGrades(assignmentId, "json");
    try {
      Files.writeString(Path.of("answer.json"), grades);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    JsonObject json;
    try (JsonReader jr = Json.createReader(new StringReader(grades))) {
      json = jr.readObject();
    }
    checkState(json.containsKey("warnings"));
    JsonArray warnings = json.getJsonArray("warnings");
    checkState(warnings.isEmpty(), warnings);
    checkState(json.containsKey("assignments"));
    JsonArray assignments = json.getJsonArray("assignments");
    checkState(assignments.size() == 1);
    JsonObject assignment = (JsonObject) Iterables.getOnlyElement(assignments);
    checkState(assignment.containsKey("assignmentid"), assignment);
    checkState(assignment.getInt("assignmentid") == assignmentId);
    JsonArray gradesArray = assignment.getJsonArray("grades");
    return gradesArray.stream().map(g -> (JsonObject) g).collect(ImmutableSet.toImmutableSet());
  }

  public ImmutableSet<Integer> assignmentIds(int courseId) {
    String assignments = queryAssignments(courseId, "json");
    try {
      Files.writeString(Path.of("answer.json"), assignments);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    JsonObject json;
    try (JsonReader jr = Json.createReader(new StringReader(assignments))) {
      json = jr.readObject();
    }
    checkState(json.containsKey("warnings"));
    JsonArray warnings = json.getJsonArray("warnings");
    checkState(warnings.isEmpty(), warnings);
    checkState(json.containsKey("courses"));
    JsonArray coursesArray = json.getJsonArray("courses");
    checkState(coursesArray.size() == 1);
    JsonObject course = (JsonObject) Iterables.getOnlyElement(coursesArray);
    checkState(course.containsKey("id"));
    checkState(course.getInt("id") == courseId);
    JsonArray assignmentsArray = course.getJsonArray("assignments");

    final ImmutableSet.Builder<Integer> idsBuilder = new ImmutableSet.Builder<>();
    for (JsonValue jsonValue : assignmentsArray) {
      JsonObject assignment = (JsonObject) jsonValue;
      checkState(assignment.containsKey("id"));
      idsBuilder.add(assignment.getInt("id"));
    }
    ImmutableSet<Integer> ids = idsBuilder.build();
    checkState(ids.size() == assignmentsArray.size());
    return ids;
  }

  public int courseId(String shortname) {
    String courses = query(shortname, "json");
    try {
      Files.writeString(Path.of("courses.json"), courses);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    JsonObject json;
    try (JsonReader jr = Json.createReader(new StringReader(courses))) {
      json = jr.readObject();
    }
    checkState(json.containsKey("warnings"));
    JsonArray warnings = json.getJsonArray("warnings");
    checkState(warnings.isEmpty(), warnings);
    checkState(json.containsKey("courses"));
    JsonArray coursesArray = json.getJsonArray("courses");
    checkState(coursesArray.size() == 1);
    JsonObject course = (JsonObject) Iterables.getOnlyElement(coursesArray);
    checkState(course.containsKey("id"));
    int courseId = course.getInt("id");
    return courseId;
  }

  public void courseIdXml(String shortname) {
    String courses = query(shortname, "xml");
    try {
      Files.writeString(Path.of("courses.xml"), courses);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    Document coursesXml =
        DomHelper.domHelper().asDocument(new StreamSource(new StringReader(courses)));
    Element responseEl = coursesXml.getDocumentElement();
    checkState(responseEl.getTagName().equals("RESPONSE"));
    ImmutableList<Element> c1Els = DomHelper.toElements(responseEl.getChildNodes());
    checkState(c1Els.size() == 1);
    Element c1El = c1Els.get(0);
    checkState(c1El.getTagName().equals("SINGLE"));
    ImmutableList<Element> singleEls = DomHelper.toElements(c1El.getChildNodes());
    checkState(singleEls.size() == 2);
    Element coursesEl = singleEls.get(0);
    checkState(keyName(coursesEl).equals("courses"));
    Element warningsEl = singleEls.get(1);
    processWarnings(warningsEl);
  }

  private String query(String shortname, String format) {
    UriBuilder uriBuilder = UriBuilder.fromPath(MOODLE_SERVER);
    uriBuilder.queryParam("moodlewsrestformat", format);
    String apiKey = CredentialsReader.keyReader().getCredentials().API_KEY();
    uriBuilder.queryParam("wstoken", apiKey);
    uriBuilder.queryParam("wsfunction", "core_course_get_courses_by_field");
    uriBuilder.queryParam("field", "shortname");
    uriBuilder.queryParam("value", shortname);
    String courses = client.target(uriBuilder).request().get(String.class);
    return courses;
  }

  private String queryPlugins(String format) {
    UriBuilder uriBuilder = UriBuilder.fromPath(MOODLE_SERVER);
    uriBuilder.queryParam("moodlewsrestformat", format);
    String apiKey = CredentialsReader.keyReader().getCredentials().API_KEY();
    uriBuilder.queryParam("wstoken", apiKey);
    uriBuilder.queryParam("wsfunction", "tool_mobile_get_plugins_supporting_mobile");
    String reply = client.target(uriBuilder).request().get(String.class);
    return reply;
  }

  private String queryAssignments(int courseId, String format) {
    UriBuilder uriBuilder = UriBuilder.fromPath(MOODLE_SERVER);
    uriBuilder.queryParam("moodlewsrestformat", format);
    String apiKey = CredentialsReader.keyReader().getCredentials().API_KEY();
    uriBuilder.queryParam("wstoken", apiKey);
    uriBuilder.queryParam("wsfunction", "mod_assign_get_assignments");
    uriBuilder.queryParam("courseids[0]", courseId);
    String reply = client.target(uriBuilder).request().get(String.class);
    return reply;
  }

  private String queryGrades(int assignmentId, String format) {
    UriBuilder uriBuilder = UriBuilder.fromPath(MOODLE_SERVER);
    uriBuilder.queryParam("moodlewsrestformat", format);
    String apiKey = CredentialsReader.keyReader().getCredentials().API_KEY();
    uriBuilder.queryParam("wstoken", apiKey);
    uriBuilder.queryParam("wsfunction", "mod_assign_get_grades");
    uriBuilder.queryParam("assignmentids[0]", assignmentId);
    String reply = client.target(uriBuilder).request().get(String.class);
    return reply;
  }

  private void processWarnings(Element warningsEl) {
    checkState(keyName(warningsEl).equals("warnings"));
    ImmutableList<Element> warningsChildren = DomHelper.toElements(warningsEl.getChildNodes());
    checkState(warningsChildren.size() == 1);
    Element warningsChild = warningsChildren.get(0);
    checkState(warningsChild.getTagName().equals("MULTIPLE"));
    String warningsText = warningsChild.getTextContent();
    if (!warningsText.isEmpty()) {
      LOGGER.warn(warningsText);
    }
  }

  private String keyName(Element coursesEl) {
    checkState(coursesEl.getTagName().equals("KEY"));
    String keyName = coursesEl.getAttribute("name");
    checkState(!keyName.isEmpty());
    return keyName;
  }
}
