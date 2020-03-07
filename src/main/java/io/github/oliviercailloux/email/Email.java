package io.github.oliviercailloux.email;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.mail.internet.InternetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;

public class Email {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Email.class);
	private InternetAddress to;

	public static Email withDocument(Document document, InternetAddress to) {
		return new Email(document, null, null, null, to);
	}

	public static Email withDocumentAndFile(Document document, String fileName, String fileContent, String fileSubtype,
			InternetAddress to) {
		return new Email(document, fileName, fileContent, fileSubtype, to);
	}

	private Email(Document document, String fileName, String fileContent, String fileSubtype, InternetAddress to) {
		this.document = document;
		this.fileName = Strings.emptyToNull(fileName);
		this.fileContent = Strings.emptyToNull(fileContent);
		this.fileSubtype = Strings.emptyToNull(fileSubtype);
		this.to = checkNotNull(to);
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

	public boolean hasFile() {
		final boolean noFileName = fileName == null;
		final boolean noFileContent = fileContent == null;
		final boolean noFileSubtype = fileSubtype == null;
		checkArgument(noFileName == noFileContent && noFileContent == noFileSubtype);
		return !noFileName;
	}

	public String getFileName() {
		checkState(fileName != null);
		return fileName;
	}

	public String getFileContent() {
		checkState(fileContent != null);
		return fileContent;
	}

	public String getFileSubtype() {
		checkState(fileSubtype != null);
		return fileSubtype;
	}

	public InternetAddress getTo() {
		return to;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Document", document).add("File name", fileName)
				.add("File content", fileContent).add("File subtype", fileSubtype).add("To", to).toString();
	}
}
