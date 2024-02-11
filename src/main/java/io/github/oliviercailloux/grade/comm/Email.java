package io.github.oliviercailloux.grade.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import io.github.oliviercailloux.email.EmailAddressAndPersonal;
import io.github.oliviercailloux.xml.HtmlDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class Email {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(Email.class);
  private EmailAddressAndPersonal to;

  public static Email withDocument(Document document, EmailAddressAndPersonal to) {
    return new Email(document, null, null, null, to);
  }

  public static Email withDocumentAndFile(Document document, String fileName, String fileContent,
      String fileSubtype, EmailAddressAndPersonal to) {
    return new Email(document, fileName, fileContent, fileSubtype, to);
  }

  private Email(Document document, String fileName, String fileContent, String fileSubtype,
      EmailAddressAndPersonal to) {
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

  public String getSubject() {
    return HtmlDocument.getOnlyElementWithLocalName(getDocument(), "title").getTextContent();
  }

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

  public EmailAddressAndPersonal getTo() {
    return to;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("Document", document).add("File name", fileName)
        .add("File content", fileContent).add("File subtype", fileSubtype).add("To", to).toString();
  }
}
