package io.github.oliviercailloux.java_grade.graders;

import java.io.IOException;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.resource.UMLResource;
import org.eclipse.uml2.uml.resources.util.UMLResourcesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UmlStuff {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(UmlStuff.class);

	public static void main(String[] args) throws Exception {
		save(createModel("ploum"), org.eclipse.emf.common.util.URI.createFileURI(args[0]).appendSegment("ExtendedPO2")
				.appendFileExtension(UMLResource.FILE_EXTENSION));
	}

	protected static Model createModel(String name) {
		Model model = UMLFactory.eINSTANCE.createModel();
		model.setName(name);

		LOGGER.info("Model {} created.", model.getQualifiedName());

		return model;
	}

	protected static void save(org.eclipse.uml2.uml.Package package_, org.eclipse.emf.common.util.URI uri)
			throws IOException {
		// Create a resource-set to contain the resource(s) that we are saving
		ResourceSet resourceSet = new ResourceSetImpl();

		// Initialize registrations of resource factories, library models,
		// profiles, Ecore metadata, and other dependencies required for
		// serializing and working with UML resources. This is only necessary in
		// applications that are not hosted in the Eclipse platform run-time, in
		// which case these registrations are discovered automatically from
		// Eclipse extension points.
		UMLResourcesUtil.init(resourceSet);

		// Create the output resource and add our model package to it.
		Resource resource = resourceSet.createResource(uri);
		resource.getContents().add(package_);

		// And save
		resource.save(null); // no save options needed
		LOGGER.info("Done.");
	}
}
