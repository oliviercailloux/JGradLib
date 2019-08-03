package io.github.oliviercailloux.email;

import javax.json.Json;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.xml.HtmlDocument;
import io.github.oliviercailloux.xml.XmlUtils;

public class EMailerMain {
	public static final String SENT_FOLDER = "Éléments envoyés";
	public static final String TRASH_FOLDER = "Éléments supprimés";
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(EMailerMain.class);

	public static void main(String[] args) throws Exception {
		final Document doc1 = getTestDocument("First document");
		LOGGER.info("Doc1: {}.", XmlUtils.asString(doc1));
		final Email email1 = Email.withDocumentAndFile(doc1, "data.json",
				Json.createObjectBuilder().add("jsonint", 1).build().toString(), "json");
		final InternetAddress to1 = new InternetAddress("olivier.cailloux@gmail.com", "O.C");

		final Document doc2 = getTestDocument("Second document");
		final Email email2 = Email.withDocumentAndFile(doc2, "data.json",
				Json.createObjectBuilder().add("jsonint", 2).build().toString(), "json");
		final InternetAddress to2 = new InternetAddress("oliviercailloux@gmail.com", "OC");

		// final Message sent = sendTo(email1, to1);
		final ImmutableSet<Message> sent = EMailer.send(ImmutableMap.of(email1, to1, email2, to2));

//		EMailer.saveInto(sent, SENT_FOLDER);
		EMailer.saveInto(sent, TRASH_FOLDER);
	}

	public static Document getTestDocument(String title) {
		final HtmlDocument doc = HtmlDocument.newInstance();
		doc.setTitle(title);
		final Element h1 = doc.createXhtmlElement("h1");
		doc.getBody().appendChild(h1);
		h1.appendChild(doc.getDocument().createTextNode("H One"));
		final Element p = doc.createXhtmlElement("p");
		doc.getBody().appendChild(p);
		p.appendChild(doc.getDocument().createTextNode("Content"));
		final Element ul = doc.createXhtmlElement("ul");
		doc.getBody().appendChild(ul);
		final Element li = doc.createXhtmlElement("li");
		ul.appendChild(li);
		li.appendChild(doc.getDocument().createTextNode("It1"));
		doc.validate();
		return doc.getDocument();
	}

}
