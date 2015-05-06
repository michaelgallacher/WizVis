package com.sonos;

import org.w3c.dom.ls.*;
import org.xml.sax.*;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.*;
import java.io.*;

public class SCXMLValidator {

	public static void validate(String pathToInputXML) throws SAXException, IOException {
		// Create the Validator.
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		factory.setResourceResolver(new ResourceResolver());

		String xsdPath = "/scxml.xsd";
		InputStream resourceAsStream = SCXMLValidator.class.getResourceAsStream(xsdPath);

		Schema schema = factory.newSchema(new StreamSource(resourceAsStream));
		Validator validator = schema.newValidator();

		System.out.println("Validating: " + pathToInputXML);
		StreamSource source = new StreamSource(new File(pathToInputXML));

		BaseErrorHandler errorHander = new CommandLineErrorHandler(new PrintWriter(System.err, true));
		validator.setErrorHandler(errorHander);
		validator.validate(source);

		if (errorHander.getErrorCount() == 0) {
			System.out.println("Success!");
		}
	}

	private static abstract class BaseErrorHandler implements org.xml.sax.ErrorHandler {
		protected PrintWriter _errorWriter = null;
		protected int errorCount = 0;

		public BaseErrorHandler(PrintWriter errorWriter) {
			_errorWriter = errorWriter;
		}

		public int getErrorCount() {
			return errorCount;
		}

		@Override
		public void warning(SAXParseException exception) throws SAXException {
			_errorWriter.println("SAXParseException warning: " + exception.getMessage());
		}

		@Override
		public void fatalError(SAXParseException exception) throws SAXException {
			_errorWriter.println("FATAL ERROR: " + exception.getMessage());
			errorCount++;
		}
	}

	private static class CommandLineErrorHandler extends BaseErrorHandler {
		public CommandLineErrorHandler(PrintWriter errorWriter) {
			super(errorWriter);
		}

		@Override
		public void error(SAXParseException exception) throws SAXException {
			String fullMessage = exception.getMessage();
			String liteMessage = fullMessage.replace("\"http://www.w3.org/2005/07/scxml\":", "");
			liteMessage = liteMessage.replace(", WC[##other:\"http://www.w3.org/2005/07/scxml\"", "");
			_errorWriter.println(String.format("error at (%d, %d): %s", exception.getLineNumber(), exception.getColumnNumber(), liteMessage));
			errorCount++;
		}
	}

	private static class WebErrorHandler extends BaseErrorHandler {
		public WebErrorHandler(PrintWriter errorWriter) {
			super(errorWriter);
		}

		@Override
		public void error(SAXParseException exception) throws SAXException {
			String fullMessage = exception.getMessage();
			String liteMessage = fullMessage.replace("\"http://www.w3.org/2005/07/scxml\":", "");
			liteMessage = liteMessage.replace(", WC[##other:\"http://www.w3.org/2005/07/scxml\"", "");
			_errorWriter.print(String.format("<Result style=\"color:red\">error at (%d, %d): %s</Result><br>", exception.getLineNumber(), exception.getColumnNumber(), liteMessage));
			errorCount++;
		}
	}

	/**
	 * Custom implementation to find embedded XSD resources.
	 */
	private static class ResourceResolver implements LSResourceResolver {

		public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
			// note: in this sample, the XSD's are expected to be in the root of the classpath
			InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(systemId);
			return new Input(publicId, systemId, resourceAsStream);
		}

	}

	/**
	 * Custom implementation to find embedded XSD resources.
	 */
	private static class Input implements LSInput {

		private String publicId;
		private String systemId;
		final private BufferedInputStream inputStream;

		public Input(String publicId, String sysId, InputStream input) {
			this.publicId = publicId;
			this.systemId = sysId;
			this.inputStream = new BufferedInputStream(input);
		}

		public String getPublicId() {
			return publicId;
		}

		public void setPublicId(String publicId) {
			this.publicId = publicId;
		}

		public String getBaseURI() {
			return null;
		}

		public void setBaseURI(String baseURI) {
		}

		public InputStream getByteStream() {
			return null;
		}

		public void setByteStream(InputStream byteStream) {
		}

		public boolean getCertifiedText() {
			return false;
		}

		public void setCertifiedText(boolean certifiedText) {
		}

		public Reader getCharacterStream() {
			return null;
		}

		public void setCharacterStream(Reader characterStream) {
		}

		public String getEncoding() {
			return null;
		}

		public void setEncoding(String encoding) {
		}

		public String getStringData() {
			synchronized (inputStream) {
				try {
					byte[] input = new byte[inputStream.available()];
					inputStream.read(input);
					return new String(input);
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Exception " + e);
					return null;
				}
			}
		}

		public void setStringData(String stringData) {
		}

		public String getSystemId() {
			return systemId;
		}

		public void setSystemId(String systemId) {
			this.systemId = systemId;
		}
	}
}

