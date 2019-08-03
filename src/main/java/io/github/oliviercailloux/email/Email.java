package io.github.oliviercailloux.email;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.StringWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import com.google.common.base.Strings;
import com.google.common.base.Verify;

public class Email {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Email.class);

	public static Email withDocument(Document document) {
		return new Email(document, null, null, null);
	}

	public static Email withDocumentAndFile(Document document, String fileName, String fileContent,
			String fileSubtype) {
		return new Email(document, fileName, fileContent, fileSubtype);
	}

	private Email(Document document, String fileName, String fileContent, String fileSubtype) {
		this.document = document;
		this.fileName = Strings.emptyToNull(fileName);
		this.fileContent = Strings.emptyToNull(fileContent);
		this.fileSubtype = Strings.emptyToNull(fileSubtype);
		final boolean noFileName = fileName == null;
		final boolean noFileContent = fileContent == null;
		final boolean noFileSubtype = fileSubtype == null;
		checkArgument(noFileName == noFileContent && noFileContent == noFileSubtype);
	}

	private final Document document;
	private final String fileName;
	private final String fileContent;
	private final String fileSubtype;

	public Document getDocument() {
		return document;
	}

	public String getFileName() {
		return fileName;
	}

	public String getFileContent() {
		return fileContent;
	}

	public String getFileSubtype() {
		return fileSubtype;
	}
}
