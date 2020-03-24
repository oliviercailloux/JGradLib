package io.github.oliviercailloux.xml;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import com.google.common.base.Verify;

public class HtmlDocument {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(HtmlDocument.class);

	public static Node getOnlyElementWithLocalName(Document document, String localName) {
		final NodeList elements = document.getElementsByTagName(localName);
		Verify.verify(elements.getLength() == 1);
		return elements.item(0);
	}

	private static final String XHTML_NAME_SPACE = "http://www.w3.org/1999/xhtml";

	public static Document getNewDocument() {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		try {
			builder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new AssertionError(e);
		}
		return builder.newDocument();
	}

	public static HtmlDocument newInstance() {
		return new HtmlDocument(getNewDocument());
	}

	private final Document document;
	private Element html;
	private Element head;
	private Element meta;
	private Element title;
	private Text titleText;
	private Element body;

	private HtmlDocument(Document document) {
		this.document = checkNotNull(document);
		init();
	}

	private void init() {
		html = document.createElementNS(XHTML_NAME_SPACE, "html");
		html.setAttribute("lang", "en");
		html.setAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "xsi:schemaLocation",
				XHTML_NAME_SPACE + " " + "http://www.w3.org/2002/08/xhtml/xhtml1-strict.xsd");
		document.appendChild(html);
		head = document.createElementNS(XHTML_NAME_SPACE, "head");
		html.appendChild(head);
		meta = document.createElementNS(XHTML_NAME_SPACE, "meta");
		meta.setAttribute("http-equiv", "Content-type");
		meta.setAttribute("content", "text/html; charset=utf-8");
		head.appendChild(meta);
		body = document.createElementNS(XHTML_NAME_SPACE, "body");
		html.appendChild(body);
	}

	public Document getDocument() {
		return document;
	}

	public Element getHtml() {
		return html;
	}

	public Element getHead() {
		return head;
	}

	public Element getMeta() {
		return meta;
	}

	public Element getBody() {
		return body;
	}

	public void setTitle(String title) {
		this.title = document.createElementNS(XHTML_NAME_SPACE, "title");
		head.appendChild(this.title);
		titleText = document.createTextNode(title);
		this.title.appendChild(titleText);
	}

	public Element getTitle() {
		return title;
	}

	public Text getTitleText() {
		return titleText;
	}

	public Element createXhtmlElement(String localName) {
		return document.createElementNS(XHTML_NAME_SPACE, localName);
	}

	public Element createParagraph(String content) {
		final Element p = createXhtmlElement("p");
		p.appendChild(document.createTextNode(content));
		return p;
	}

	public Element createTitle1(String content) {
		final Element el = createXhtmlElement("h1");
		el.appendChild(document.createTextNode(content));
		return el;
	}

	public Element createTitle2(String content) {
		final Element el = createXhtmlElement("h2");
		el.appendChild(document.createTextNode(content));
		return el;
	}

	public String asString() {
		return XmlUtils.asString(document);
	}

	public void validate() {
		XmlUtils.validate(document);
	}
}
