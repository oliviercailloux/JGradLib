package io.github.oliviercailloux.xml;

import com.google.common.base.Verify;
import java.io.IOException;
import java.io.StringWriter;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

public class XmlUtils {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(XmlUtils.class);
  private static Schema xhtmlSchema;

  public static String asString(Document doc) {
    final DOMImplementationRegistry registry;
    try {
      registry = DOMImplementationRegistry.newInstance();
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
        | ClassCastException e) {
      throw new AssertionError(e);
    }
    final DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");
    final LSSerializer ser = impl.createLSSerializer();
    LOGGER.debug("Using {}.", ser.getClass());
    ser.getDomConfig().setParameter("format-pretty-print", true);
    /** Unsupported. */
    // ser.getDomConfig().setParameter("validate", true);
    /** Do not use ser.writeToString: it uses UTF-16. */
    final LSOutput output = impl.createLSOutput();
    final StringWriter writer = new StringWriter();
    output.setCharacterStream(writer);
    final boolean wasWritten = ser.write(doc, output);
    Verify.verify(wasWritten);
    return writer.toString();
  }

  public static void validate(Document document) {
    if (xhtmlSchema == null) {
      try {
        LOGGER.info("Loading schema.");
        /** Takes 15 seconds. */
        xhtmlSchema = SchemaFactory.newDefaultInstance()
            .newSchema(XmlUtils.class.getResource("xhtml1-strict.xsd"));
        LOGGER.info("Loaded schema.");
      } catch (SAXException e) {
        throw new AssertionError(e);
      }
    }

    final DOMSource docAsSource = new DOMSource();
    docAsSource.setNode(document);
    try {
      xhtmlSchema.newValidator().validate(docAsSource);
      LOGGER.info("Validated.");
    } catch (IOException | SAXException e) {
      throw new AssertionError(e);
    }
  }
}
