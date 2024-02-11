package io.github.oliviercailloux.uml_graders;

import static com.google.common.base.Verify.verify;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.Graph;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.DeadlineGrader;
import io.github.oliviercailloux.grade.GitGeneralGrader;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.RepositoryFetcher;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.jaris.xml.DomHelper;
import io.github.oliviercailloux.utils.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class AdminManagesUsers {
  private static final String XMI_NS = "http://www.omg.org/spec/XMI/20131001";

  private static final String UML_NS = "http://www.eclipse.org/uml2/5.0.0/UML";

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(AdminManagesUsers.class);

  public static final String PREFIX = "admin-manages-users";

  public static final ZonedDateTime DEADLINE =
      ZonedDateTime.parse("2021-01-25T14:11:00+01:00[Europe/Paris]");

  private static DocumentBuilder builder;

  private static final DomHelper domHelper = DomHelper.domHelper();

  public static void main(String[] args) throws Exception {
    final RepositoryFetcher fetcher = RepositoryFetcher.withPrefix(PREFIX);
    // .setRepositoriesFilter(r->r.getUsername().equals(""));
    final GitGeneralGrader grader = GitGeneralGrader.using(fetcher,
        DeadlineGrader.usingPathGrader(AdminManagesUsers::grade, DEADLINE).setPenalizer(
            DeadlineGrader.LinearPenalizer.proportionalToLateness(Duration.ofSeconds(600))));
    grader.grade();
  }

  private AdminManagesUsers() {}

  public static IGrade grade(Path work) throws IOException {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

    final ImmutableSet<Path> umlPaths =
        Utils.getPathsMatching(work, (Path p) -> String.valueOf(p.getFileName()).endsWith(".uml"));
    if (umlPaths.isEmpty()) {
      return Mark.zero("No model file found.");
    }

    for (Path umlPath : umlPaths) {
      Document uml;
      IGrade grade;
      try {
        uml = toDocument(umlPath);
        grade = grade(work, uml);
      } catch (SAXException e) {
        grade = Mark.zero("Could not parse " + umlPath.toString() + ": " + e.getMessage());
      }
      gradeBuilder.add(CriterionGradeWeight
          .from(Criterion.given("Considering " + umlPath.toString()), grade, 1d));
    }

    return WeightingGrade.from(gradeBuilder.build());
  }

  private static IGrade grade(Path work, Document uml) throws IOException {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
    gradeBuilder.add(
        CriterionGradeWeight.from(Criterion.given("Required elements"), getRequired(uml), 15d));
    gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("No superfluous elements"),
        gradeSuperfluous(uml), 1.5d));
    gradeBuilder
        .add(CriterionGradeWeight.from(Criterion.given("Sorted out"), gradeSortedOut(uml), 1d));
    gradeBuilder
        .add(CriterionGradeWeight.from(Criterion.given("Model only"), gradeModelOnly(work), 1.5d));
    return WeightingGrade.from(gradeBuilder.build());
  }

  private static IGrade gradeModelOnly(Path work) throws IOException {
    final ImmutableSet<Path> umlFiles =
        Utils.getPathsMatching(work, p -> String.valueOf(p.getFileName()).endsWith(".uml"));
    final ImmutableSet<Path> filesNotUml = Utils.getPathsMatching(work,
        p -> Files.isRegularFile(p) && !p.getFileName().toString().endsWith(".uml"));
    return Mark.binary(umlFiles.size() == 1 && filesNotUml.isEmpty());
  }

  private static IGrade gradeSortedOut(Document uml) {
    final ImmutableList<Node> children = DomHelper.toList(uml.getDocumentElement().getChildNodes());
    final ImmutableList<Node> nonTextChildren = children.stream()
        .filter(n -> n.getNodeType() != Node.TEXT_NODE).collect(ImmutableList.toImmutableList());
    final boolean justOneSubject = nonTextChildren.size() == 1;

    final ImmutableList<Element> pckgEls =
        DomHelper.toElements(uml.getElementsByTagName("packagedElement"));
    final ImmutableList<Element> ownedEls =
        DomHelper.toElements(uml.getElementsByTagName("ownedUseCase"));
    final ImmutableList<Element> nestedEls =
        DomHelper.toElements(uml.getElementsByTagName("nestedClassifier"));
    final ImmutableList<Element> mainElements =
        Streams.concat(pckgEls.stream(), ownedEls.stream(), nestedEls.stream())
            .collect(ImmutableList.toImmutableList());
    final ImmutableList<Element> useCases = getType(mainElements, "uml:UseCase");
    final boolean someUseCases = useCases.size() >= 1;
    return Mark.binary(justOneSubject && someUseCases);
  }

  private static IGrade gradeSuperfluous(Document uml) {
    int expectedFoundNotRemoved = 0;
    final ImmutableSet<Element> leaves = getLeaves(uml);
    final Set<Element> remainingLeaves = new LinkedHashSet<>(leaves);
    {
      final ImmutableList<Element> elements = getType(remainingLeaves, "uml:Actor");
      final long nbFound = elements.size();
      if (nbFound >= 1) {
        remainingLeaves.removeAll(elements);
      } else {
        expectedFoundNotRemoved += nbFound;
      }
    }
    {
      final ImmutableList<Element> elements = getType(remainingLeaves, "uml:UseCase");
      final long nbFound = elements.size();
      LOGGER.debug("Found UseCases: {}.", toString(elements));
      if (nbFound >= 3) {
        remainingLeaves.removeAll(elements);
      } else {
        expectedFoundNotRemoved += nbFound;
      }
    }
    {
      final ImmutableList<Element> elements = getType(remainingLeaves, "uml:Generalization");
      final long nbFound = elements.size();
      if (nbFound >= 2) {
        remainingLeaves.removeAll(elements);
      } else {
        expectedFoundNotRemoved += nbFound;
      }
    }
    {
      final ImmutableList<Element> elements =
          getType(remainingLeaves, "ecore:EStringToStringMapEntry");
      remainingLeaves.removeAll(elements);
    }
    {
      final ImmutableList<Element> elements = getType(remainingLeaves, "uml:Property");
      final long nbFound = elements.size();
      if (nbFound >= 2) {
        remainingLeaves.removeAll(elements);
      } else {
        expectedFoundNotRemoved += nbFound;
      }
    }
    final int nbSuperfluous = remainingLeaves.size() - expectedFoundNotRemoved;
    verify(nbSuperfluous >= 0);

    final int quartersLost = Math.min(nbSuperfluous, 4);
    final IGrade superfluous;
    if (leaves.size() <= 4) {
      superfluous = Mark.zero("Not enough elements");
    } else if (quartersLost == 0) {
      superfluous = Mark.one();
    } else {
      superfluous = Mark.given((4 - quartersLost) / 4d,
          "Nb superfluous: " + nbSuperfluous + " among " + toNameAndId(remainingLeaves) + ".");
    }
    return superfluous;
  }

  private static ImmutableSet<Element> getLeaves(Document uml) {
    final Graph<Element> graph =
        Utils.<Element, Element>asGraph(
            (Element e) -> DomHelper.toList(e.getChildNodes()).stream()
                .filter(n -> n.getNodeType() == Node.ELEMENT_NODE).map(n -> (Element) n)
                .collect(ImmutableList.toImmutableList()),
            ImmutableSet.of(uml.getDocumentElement()));
    return graph.nodes().stream().filter(e -> graph.successors(e).isEmpty())
        .collect(ImmutableSet.toImmutableSet());
  }

  private static IGrade getRequired(Document uml) {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

    gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Model"),
        Mark.binary(uml.getDocumentElement().getNamespaceURI().equals(UML_NS)), 1d));

    final ImmutableList<Element> pckgEls =
        DomHelper.toElements(uml.getElementsByTagName("packagedElement"));
    final ImmutableList<Element> ownedEls =
        DomHelper.toElements(uml.getElementsByTagName("ownedUseCase"));
    final ImmutableList<Element> nestedEls =
        DomHelper.toElements(uml.getElementsByTagName("nestedClassifier"));

    final Optional<Element> subject = pckgEls.stream()
        .filter(e -> Marks.extendAll("System").matcher(e.getAttribute("name")).matches())
        .collect(Utils.singleOrEmpty());
    gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Subject name"),
        Mark.binary(subject.isPresent()), 1.5d));

    final ImmutableList<Element> mainElements =
        Streams.concat(pckgEls.stream(), ownedEls.stream(), nestedEls.stream())
            .collect(ImmutableList.toImmutableList());
    final ImmutableList<Element> useCaseEls = getType(mainElements, "uml:UseCase");

    final ImmutableSet<Element> manageUseCases = getUseCases(mainElements, "Manage\\h+user?s");
    final ImmutableSet<String> manageUseCaseIds =
        manageUseCases.stream().map(AdminManagesUsers::getId).flatMap(Optional::stream)
            .collect(ImmutableSet.toImmutableSet());
    LOGGER.debug("Manage: {}.", manageUseCaseIds);
    final Optional<Element> manageUseCase = manageUseCases.stream().collect(Utils.singleOrEmpty());
    gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Use case Manage"),
        gradeUseCases(manageUseCases), 2d));

    final ImmutableSet<Element> createUseCases = getUseCases(mainElements, "Create\\h+user");
    gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Use case Create"),
        gradeUseCases(createUseCases), 2d));

    final ImmutableSet<Element> deleteUseCases = getUseCases(mainElements, "Delete\\h+user");
    gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Use case Delete"),
        gradeUseCases(deleteUseCases), 2d));

    {
      final ImmutableSet<Element> childrenUseCases =
          Stream.concat(createUseCases.stream(), deleteUseCases.stream())
              .collect(ImmutableSet.toImmutableSet());

      final ImmutableSet<Element> useCasesWithGeneralization = useCaseEls.stream()
          .filter(u -> getTargetOfUniqueGeneralization(u, useCaseEls).isPresent())
          .collect(ImmutableSet.toImmutableSet());
      final boolean createGeneralized = !createUseCases.isEmpty() && createUseCases.stream()
          .allMatch(u -> getTargetOfUniqueGeneralization(u, useCaseEls).isPresent());
      final boolean deleteGeneralized = !deleteUseCases.isEmpty() && deleteUseCases.stream()
          .allMatch(u -> getTargetOfUniqueGeneralization(u, useCaseEls).isPresent());
      final CriterionGradeWeight nbOneOrTwo =
          CriterionGradeWeight.from(Criterion.given("Create or Delete generalizes"),
              Mark.binary(createGeneralized || deleteGeneralized), 2d);
      final CriterionGradeWeight nbTwo =
          CriterionGradeWeight.from(Criterion.given("Create and Delete generalize"),
              Mark.binary(createGeneralized && deleteGeneralized), 1d);
      final CriterionGradeWeight number = CriterionGradeWeight.from(Criterion.given("Number"),
          WeightingGrade.from(ImmutableSet.of(nbOneOrTwo, nbTwo)), 1d);

      final ImmutableSet<Element> targets = useCasesWithGeneralization.stream()
          .map(u -> getTargetOfUniqueGeneralization(u, useCaseEls)).map(Optional::get)
          .collect(ImmutableSet.toImmutableSet());
      final Optional<Element> targetIfSingle = targets.stream().collect(Utils.singleOrEmpty());
      final boolean allTargetsManage =
          targetIfSingle.isPresent() && targetIfSingle.equals(manageUseCase);
      final CriterionGradeWeight idsOneOrTwo =
          CriterionGradeWeight.from(Criterion.given("One or two"),
              Mark.binary(
                  allTargetsManage && childrenUseCases.containsAll(useCasesWithGeneralization)),
              2d);
      final CriterionGradeWeight idsTwo = CriterionGradeWeight.from(Criterion.given("Exactly two"),
          Mark.binary(allTargetsManage && childrenUseCases.equals(useCasesWithGeneralization)), 1d);
      final CriterionGradeWeight ids =
          CriterionGradeWeight.from(Criterion.given("Generalizes is the unique Manage UC"),
              WeightingGrade.from(ImmutableSet.of(idsOneOrTwo, idsTwo)), 1d);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Generalization"),
          WeightingGrade.from(ImmutableSet.of(number, ids)), 3d));
    }
    final Optional<Element> actor = Stream.concat(pckgEls.stream(), nestedEls.stream())
        .filter(e -> e.getAttributeNS(XMI_NS, "type").equals("uml:Actor"))
        .collect(Utils.singleOrEmpty());
    final Optional<String> actorId = actor.flatMap(AdminManagesUsers::getId);
    {
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Actor"), gradeActor(actor), 1d));
    }
    {
      final Optional<Element> association = Stream.concat(pckgEls.stream(), nestedEls.stream())
          .filter(e -> e.getAttributeNS(XMI_NS, "type").equals("uml:Association"))
          .collect(Utils.singleOrEmpty());
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Association"),
          gradeAssociation(association, actorId, manageUseCaseIds), 2.5d));

    }
    return WeightingGrade.from(gradeBuilder.build());
  }

  static ImmutableSet<String> toString(Collection<? extends Node> elements) {
    return elements.stream().map(domHelper::toString).collect(ImmutableSet.toImmutableSet());
  }

  static ImmutableSet<String> toNameAndId(Set<Element> elements) {
    return elements.stream().map(e -> e.getAttribute("name") + " " + e.getAttributeNS(XMI_NS, "id"))
        .collect(ImmutableSet.toImmutableSet());
  }

  private static IGrade gradeActor(Optional<Element> actor) {
    final CriterionGradeWeight actorGrade =
        CriterionGradeWeight.from(Criterion.given("Actor"), Mark.binary(actor.isPresent()), 1d);
    final CriterionGradeWeight nameGrade =
        CriterionGradeWeight.from(Criterion.given("Name"), Mark.binary(Marks.extendAll("Admin")
            .matcher(actor.map(e -> e.getAttribute("name")).orElse("")).matches()), 1d);
    return WeightingGrade.from(ImmutableSet.of(actorGrade, nameGrade));
  }

  private static IGrade gradeAssociation(Optional<Element> association, Optional<String> actorId,
      Set<String> manageUseCaseIds) {
    final CriterionGradeWeight associationGrade = CriterionGradeWeight
        .from(Criterion.given("Actor"), Mark.binary(association.isPresent()), 1d);
    final ImmutableList<Element> ownedAttributes =
        association.map(a -> DomHelper.toElements(a.getElementsByTagName("ownedAttribute")))
            .orElse(ImmutableList.of());
    final ImmutableList<Element> ownedEnds =
        association.map(a -> DomHelper.toElements(a.getElementsByTagName("ownedEnd")))
            .orElse(ImmutableList.of());
    final ImmutableList<Element> owned =
        ImmutableList.<Element>builder().addAll(ownedAttributes).addAll(ownedEnds).build();
    final ImmutableList<Element> properties =
        owned.stream().filter(a -> a.getAttributeNS(XMI_NS, "type").equals("uml:Property"))
            .collect(ImmutableList.toImmutableList());
    final ImmutableList<String> targetIds = properties.stream().map(p -> p.getAttribute("type"))
        .collect(ImmutableList.toImmutableList());
    final boolean targetsAnActor = actorId.isPresent() && targetIds.contains(actorId.get());
    final ImmutableSet<String> targettedUcIds =
        Sets.intersection(ImmutableSet.copyOf(targetIds), manageUseCaseIds).immutableCopy();
    final boolean targetsAnUc = targettedUcIds.size() == 1;
    LOGGER.debug("Targets: {}, to ucs: {}.", targetIds, targettedUcIds);
    final CriterionGradeWeight propertiesGrade = CriterionGradeWeight
        .from(Criterion.given("Properties"), Mark.binary(targetsAnActor && targetsAnUc), 1d);

    return WeightingGrade.from(ImmutableSet.of(associationGrade, propertiesGrade));
  }

  private static Optional<Element> getTargetOfUniqueGeneralization(Element useCaseEl,
      List<Element> useCaseEls) {
    final Optional<Element> generalizationEl =
        DomHelper.toElements(useCaseEl.getElementsByTagName("generalization")).stream()
            .collect(Utils.singleOrEmpty());
    final Optional<String> targetId = generalizationEl.map(g -> g.getAttribute("general"));
    final Optional<Element> useCase = targetId.flatMap(g -> getElementById(useCaseEls, g));
    return useCase;
  }

  private static Optional<Element> getElementById(List<Element> elements, String id) {
    return elements.stream().filter(e -> e.getAttributeNS(XMI_NS, "id").equals(id))
        .collect(Utils.singleOrEmpty());
  }

  private static WeightingGrade gradeUseCases(Set<Element> useCases) {
    final Set<String> useCaseIds = useCases.stream().map(AdminManagesUsers::getId)
        .flatMap(Optional::stream).collect(ImmutableSet.toImmutableSet());
    final CriterionGradeWeight ucGrade = CriterionGradeWeight.from(Criterion.given("Exists"),
        Mark.binary(!useCases.isEmpty() && useCaseIds.size() == useCases.size()), 1d);
    final CriterionGradeWeight nameGrade =
        CriterionGradeWeight.from(Criterion.given("Subject"),
            Mark.binary(
                !useCases.isEmpty() && useCases.stream().allMatch(u -> u.hasAttribute("subject"))),
            2d);
    final CriterionGradeWeight subjectGrade =
        CriterionGradeWeight.from(Criterion.given("Unique"), Mark.binary(useCases.size() == 1), 3d);
    return WeightingGrade.from(ImmutableSet.of(ucGrade, nameGrade, subjectGrade));
  }

  private static Optional<String> getId(Element element) {
    return Optional.ofNullable(Strings.emptyToNull(element.getAttributeNS(XMI_NS, "id")));
  }

  private static ImmutableSet<Element> getUseCases(Iterable<Element> elements, String namePattern) {
    final ImmutableList<Element> useCases = getType(elements, "uml:UseCase");
    return useCases.stream()
        .filter(e -> Marks.extendAll(namePattern).matcher(e.getAttribute("name")).matches())
        .collect(ImmutableSet.toImmutableSet());
  }

  private static ImmutableList<Element> getType(Iterable<Element> elements, String type) {
    return Streams.stream(elements).filter(e -> e.getAttributeNS(XMI_NS, "type").equals(type))
        .collect(ImmutableList.toImmutableList());
  }

  private static void prepareBuilder() {
    if (builder == null) {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      try {
        builder = factory.newDocumentBuilder();
      } catch (ParserConfigurationException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public static Document toDocument(Path input) throws SAXException, IOException {
    prepareBuilder();

    final Document doc;
    try (InputStream inputStream = Files.newInputStream(input)) {
      final InputSource source = new InputSource(inputStream);
      source.setSystemId(input.toUri().toString());
      doc = builder.parse(source);
    }

    final Element docE = doc.getDocumentElement();
    LOGGER.debug("Main tag name: {}.", docE.getTagName());

    return doc;
  }

  public static Document asDocument(InputSource input) {
    prepareBuilder();

    final Document doc;
    try {
      doc = builder.parse(input);
    } catch (SAXException | IOException e) {
      throw new IllegalStateException(e);
    }

    final Element docE = doc.getDocumentElement();
    LOGGER.debug("Main tag name: {}.", docE.getTagName());

    return doc;
  }
}
