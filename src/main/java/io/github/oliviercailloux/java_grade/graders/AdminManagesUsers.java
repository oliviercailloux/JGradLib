package io.github.oliviercailloux.java_grade.graders;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.graph.Graph;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.DeadlineGrader;
import io.github.oliviercailloux.grade.GitGeneralGrader;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.RepositoryFetcher;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.jaris.xml.XmlUtils;
import io.github.oliviercailloux.utils.Utils;

public class AdminManagesUsers {
	private static class UmlDocument {
		Document uml;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(AdminManagesUsers.class);

	private static final String XMI_NS = "http://www.omg.org/spec/XMI/20131001";

	private static final String UML_NS = "http://www.eclipse.org/uml2/5.0.0/UML";

	public static final String PREFIX = "admin-manages-users";

	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2021-01-14T23:00:00+01:00[Europe/Paris]");

	private Path work;

	private static DocumentBuilder builder;

	public static void main(String[] args) throws Exception {
		final RepositoryFetcher fetcher = RepositoryFetcher.withPrefix(PREFIX);
		GitGeneralGrader.using(fetcher, DeadlineGrader.given(new AdminManagesUsers(), DEADLINE)).grade();
	}

	AdminManagesUsers() {
	}

	public WeightingGrade grade(Path work) throws IOException {
		this.work = work;

		final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

//		{
//			final Mark hasCommit = Mark.binary(!history.getGraph().nodes().isEmpty());
//			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("At least one"), hasCommit, 1d));
//		}

		final ImmutableSet<Path> umlPaths = Utils.getPathsMatching(work,
				(Path p) -> p.getFileName().toString().endsWith(".uml"));
		for (Path umlPath : umlPaths) {
			final Document uml = toDocument(umlPath);
			final IGrade grade = grade(work, uml);
			gradeBuilder
					.add(CriterionGradeWeight.from(Criterion.given("Considering " + umlPath.toString()), grade, 2.5d));
		}

		LOGGER.info("Grading compile.");

		return WeightingGrade.from(gradeBuilder.build());
	}

	private static IGrade grade(Path work, Document uml) throws IOException {
		final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

		final UmlDocument doc = new UmlDocument();
		doc.uml = uml;
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Required elements"), getRequired(doc), 2.5d));

		/** Get leaves. All should be useful. */
		// if ≤3 A and expect 3 A, remove them. If too many, leave them from both sides.
		int expected = 0;
		final ImmutableSet<Element> leaves = getLeaves(uml);

	}

	private static ImmutableSet<Element> getLeaves(Document uml) {
		final Graph<Element> graph = Utils.<Element, Element>asGraph((Element e) -> XmlUtils.toList(e.getChildNodes())
				.stream().filter(n -> n.getNodeType() == Node.ELEMENT_NODE).map(n -> (Element) n)
				.collect(ImmutableList.toImmutableList()), ImmutableSet.of(uml.getDocumentElement()));
		return graph.nodes().stream().filter(e -> graph.successors(e).isEmpty()).collect(ImmutableSet.toImmutableSet());
	}

	private static IGrade getRequired(UmlDocument doc) {
		final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

		final ImmutableList<Element> modelEls = XmlUtils.toElements(doc.uml.getElementsByTagNameNS(UML_NS, "Model"));
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Model"), Mark.binary(modelEls.size() == 1), 2.5d));

		final ImmutableList<Element> pckgEls = XmlUtils.toElements(doc.uml.getElementsByTagName("packagedElement"));
		final ImmutableList<Element> ownedEls = XmlUtils.toElements(doc.uml.getElementsByTagName("ownedUseCase"));

		final Optional<Element> subject = pckgEls.stream()
				.filter(e -> Marks.extendAll("System").matcher(e.getAttribute("name")).matches())
				.collect(Utils.singleOrEmpty());
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Subject"), Mark.binary(subject.isPresent()), 2.5d));

		final ImmutableList<Element> useCaseEls = Stream.concat(pckgEls.stream(), ownedEls.stream())
				.filter(e -> e.getAttributeNS(XMI_NS, "type").equals("uml:UseCase"))
				.collect(ImmutableList.toImmutableList());

		final Optional<Element> manageUseCase = getUseCase(pckgEls, ownedEls, "Manage\\h+users");
		final Optional<String> manageUseCaseId = manageUseCase.flatMap(AdminManagesUsers::getId);
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Use case Manage"),
				gradeUseCase(manageUseCaseId, useCaseEls), 2.5d));

		final Optional<Element> createUseCase = getUseCase(pckgEls, ownedEls, "Create\\h+user");
		final Optional<String> createUseCaseId = createUseCase.flatMap(AdminManagesUsers::getId);
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Use case Create"),
				gradeUseCase(createUseCaseId, useCaseEls), 2.5d));

		final Optional<Element> deleteUseCase = getUseCase(pckgEls, ownedEls, "Delete\\h+user");
		final Optional<String> deleteUseCaseId = deleteUseCase.flatMap(AdminManagesUsers::getId);
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Use case Delete"),
				gradeUseCase(deleteUseCaseId, useCaseEls), 2.5d));

		{
			final ImmutableSet<Element> childrenUseCases = ImmutableSet.of(createUseCase, deleteUseCase).stream()
					.filter(Optional::isPresent).map(Optional::get).collect(ImmutableSet.toImmutableSet());

			final ImmutableSet<Element> useCasesWithGeneralization = useCaseEls.stream()
					.filter(u -> getTargetOfUniqueGeneralization(u, useCaseEls).isPresent())
					.collect(ImmutableSet.toImmutableSet());
			final CriterionGradeWeight nbOneOrTwo = CriterionGradeWeight.from(Criterion.given("One or two"),
					Mark.binary(useCasesWithGeneralization.size() == 1 || useCasesWithGeneralization.size() == 2), 2d);
			final CriterionGradeWeight nbTwo = CriterionGradeWeight.from(Criterion.given("Exactly two"),
					Mark.binary(useCasesWithGeneralization.size() == 2), 1d);
			final CriterionGradeWeight number = CriterionGradeWeight.from(Criterion.given("Number"),
					WeightingGrade.from(ImmutableSet.of(nbOneOrTwo, nbTwo)), 1d);

			final ImmutableSet<Optional<Element>> targets = useCaseEls.stream()
					.map(u -> getTargetOfUniqueGeneralization(u, useCaseEls)).collect(ImmutableSet.toImmutableSet());
			final boolean allTargetsManage = targets.size() == 1 && Iterables.getOnlyElement(targets).isPresent()
					&& Iterables.getOnlyElement(targets).equals(manageUseCase);
			final CriterionGradeWeight idsOneOrTwo = CriterionGradeWeight.from(Criterion.given("One or two"),
					Mark.binary(allTargetsManage && childrenUseCases.containsAll(useCasesWithGeneralization)), 2d);
			final CriterionGradeWeight idsTwo = CriterionGradeWeight.from(Criterion.given("Exactly two"),
					Mark.binary(allTargetsManage && childrenUseCases.equals(useCasesWithGeneralization)), 1d);
			final CriterionGradeWeight ids = CriterionGradeWeight.from(Criterion.given("Identities"),
					WeightingGrade.from(ImmutableSet.of(idsOneOrTwo, idsTwo)), 1d);
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Generalization"),
					WeightingGrade.from(ImmutableSet.of(number, ids)), 1d));
		}
		{
			final ImmutableList<Element> nestedEls = XmlUtils
					.toElements(doc.uml.getElementsByTagName("nestedClassifier"));
			final Optional<Element> actor = Stream.concat(pckgEls.stream(), nestedEls.stream())
					.filter(e -> e.getAttributeNS(XMI_NS, "type").equals("uml:Actor")).collect(Utils.singleOrEmpty());
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Actor"), gradeActor(actor), 2.5d));
		}
		return WeightingGrade.from(gradeBuilder.build());
	}

	private static IGrade gradeActor(Optional<Element> actor) {
		final CriterionGradeWeight actorGrade = CriterionGradeWeight.from(Criterion.given("Actor"),
				Mark.binary(actor.isPresent()), 1d);
		final CriterionGradeWeight nameGrade = CriterionGradeWeight.from(Criterion.given("Name"),
				Mark.binary(
						Marks.extendAll("Admin").matcher(actor.map(e -> e.getAttribute("name")).orElse("")).matches()),
				1d);
		return WeightingGrade.from(ImmutableSet.of(actorGrade, nameGrade));
	}

	private static Optional<Element> getTargetOfUniqueGeneralization(Element useCaseEl, List<Element> useCaseEls) {
		final Optional<Element> generalizationEl = XmlUtils.toElements(useCaseEl.getElementsByTagName("generalization"))
				.stream().collect(Utils.singleOrEmpty());
		final Optional<String> targetId = generalizationEl.map(g -> g.getAttribute("general"));
		final Optional<Element> useCase = targetId.flatMap(g -> getElementById(useCaseEls, g));
		return useCase;
	}

	private static Optional<Element> getElementById(List<Element> elements, String id) {
		return elements.stream().filter(e -> e.getAttributeNS(XMI_NS, "id").equals(id)).collect(Utils.singleOrEmpty());
	}

	private static WeightingGrade gradeUseCase(Optional<String> useCaseId, Collection<Element> useCaseEls) {
		final boolean typeAndName = useCaseId.isPresent();
		final CriterionGradeWeight nameGrade = CriterionGradeWeight.from(Criterion.given("Name"),
				Mark.binary(typeAndName), 2.5d);
		final CriterionGradeWeight ucGrade = CriterionGradeWeight.from(Criterion.given("Use case"),
				Mark.binary(typeAndName || useCaseEls.size() == 3), 2.5d);
		return WeightingGrade.from(ImmutableSet.of(nameGrade, ucGrade));
	}

	private static Optional<String> getId(Element element) {
		return Optional.ofNullable(Strings.emptyToNull(element.getAttributeNS(XMI_NS, "id")));
	}

	private static Optional<Element> getUseCase(Iterable<Element> pckgEls, Iterable<Element> ownedEls,
			final String namePattern) {
		return Stream.concat(Streams.stream(pckgEls), Streams.stream(ownedEls))
				.filter(e -> e.getAttributeNS(XMI_NS, "type").equals("uml:UseCase")
						&& Marks.extendAll(namePattern).matcher(e.getAttribute("name")).matches())
				.collect(Utils.singleOrEmpty());
	}

	private static void prepareBuilder() {
		if (builder == null) {
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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
