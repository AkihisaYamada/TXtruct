package cps.txtr;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.transform.dom.DOMSource;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.w3c.dom.*;

public class XML {
	static String streamToString(InputStream inputStream, Charset cs) throws IOException {
		StringBuilder textBuilder = new StringBuilder();
		try (Reader reader = new BufferedReader(new InputStreamReader(inputStream, cs))) {
			int c = 0;
			while ((c = reader.read()) != -1) {
				textBuilder.append((char) c);
			}
			return textBuilder.toString();
		}
	}

	private static String[][] props = { { "encoding", "UTF-8" }, { "indent", "yes" },
			{ "{http://xml.apache.org/xslt}indent-amount", "2" }, { OutputKeys.OMIT_XML_DECLARATION, "yes" } };
	private static Transformer transformer;
	static {
		try {
			transformer = TransformerFactory.newInstance().newTransformer();
			for (String[] prop : props) {
				transformer.setOutputProperty(prop[0], prop[1]);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private static DocumentBuilder documentBuilder;
	static {
		try {
			documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static Document document = newDocument();

	public static Document newDocument() {
		return documentBuilder.newDocument();
	}

	public static DocumentFragment createDocumentFragment() {
		return document.createDocumentFragment();
	}

	public static Element createElement(String tagName) {
		return document.createElement(tagName);
	}

	public static Text createTextNode(String data) {
		return document.createTextNode(data);
	}

	public static Comment createComment(String data) {
		return document.createComment(data);
	}

	public static abstract class ToNode {
		public abstract Node toNode();
		@Override
		public String toString() {
			return XML.toString(toNode());
		}
	}

	public static Document parse(InputStream file) throws IOException, SAXException {
		return documentBuilder.parse(file);
	}

	public static Document parse(String string) throws IOException, SAXException {
		return documentBuilder.parse(new InputSource(new StringReader(string)));
	}

	public static Node ofFragment(String str) throws IOException, SAXException {
		return parse("<root>" + str + "</root>").getDocumentElement();
	}

	public static Node ofFragment(InputStream file, Charset charset) throws IOException, SAXException {
		return ofFragment(streamToString(file,charset));
	}

	public static Node ofString(String str) {
		Document doc = newDocument();
		DocumentFragment root = doc.createDocumentFragment();
		root.appendChild(doc.createTextNode(str));
		return root;
	}

	public static Node ofString(InputStream file, Charset charset) throws IOException {
		return ofString(streamToString(file, charset));
	}

	public static Node ofHTML(String str) {
		return org.jsoup.helper.W3CDom.convert(org.jsoup.Jsoup.parse(str));
	}

	public static Node ofHTML(InputStream file) throws IOException {
		return org.jsoup.helper.W3CDom.convert(org.jsoup.Jsoup.parse(file,null,""));
	}

	public static Node ofHTMLfragment(String str) {
		Document doc = org.jsoup.helper.W3CDom.convert(org.jsoup.Jsoup.parseBodyFragment(str));
		return doc.getElementsByTagName("body").item(0);
	}

	public static Node ofHTMLfragment(InputStream file, Charset charset) throws IOException {
		return ofHTMLfragment(streamToString(file, charset));
	}

	public static void write(Node node, OutputStream out) throws TransformerException {
		transformer.transform(new DOMSource(node), new StreamResult(out));
	}

	public static String toString(Node elm) {
		StringWriter writer = new StringWriter();
		try {
			transformer.transform(new DOMSource(elm), new StreamResult(writer));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return writer.toString();
	}

	public static class SchemaError extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public SchemaError(String msg) {
			super("XML.SchemeError: " + msg);
		}
	}

	private static boolean isWhiteSpace(String text) {
		return text.matches("[ \t\n]*");
	}

	public static DocumentFragment create() {
		return XML.newDocument().createDocumentFragment();
	}

	public static class Walker extends ToNode {
		protected Node parent;// parent is necessary when node = null
		protected Node previous;// child that was last processed. null means no child was processed
		protected Node node;// child under precess. null means one need to seek
		protected String data;// the string under process
		protected int cursor;// cursor when data is valid
		protected int length;// length of the data
		protected Node root;// the root node
		protected Document document;// the owner document
		protected List<String> expectations = new ArrayList<>();

		public Walker(Node node) {
			document = node.getOwnerDocument();
			parent = root = node;
			previous = null;
			this.node = null;
			data = null;
			cursor = 0;
		}

		public static Walker create() {
			return new Walker(XML.create());
		}

		public Document getOwnerDocument() {
			return document;
		}

		@Override public Node toNode() {
			return parent;
		}

		private void seek( boolean readSpaces, boolean readComment, boolean readPI ) {
			if( node != null &&
				( data == null/* element */ || readSpaces || !isWhiteSpace(data) ) ) {
				return;
			}
			node = previous == null ? parent.getFirstChild() : previous.getNextSibling();
			for (;;) {
				if (node == null) {
					return;
				}
				switch( node.getNodeType() ) {
				case Node.ELEMENT_NODE:
					return;
				case Node.TEXT_NODE:
					String data = node.getTextContent();
					if( data != "" && (readSpaces || !isWhiteSpace(data)) ) {
						this.data = data;
						cursor = 0;
						length = this.data.length();
						return;
					}
					break;
				case Node.COMMENT_NODE:
					if( readComment ) {
						this.data = node.getTextContent();
						cursor = 0;
						length = this.data.length();
						return;
					}
					break;
				case Node.PROCESSING_INSTRUCTION_NODE:
					if( readPI ) {
						return;
					}
					break;
				}
				previous = node;// this is attribute or comment node etc. mark it processed
				node = node.getNextSibling();
			}
		}

		public void next() {
			if (node == null) {
				throw new SchemaError("BUG: skipping null");
			}
			previous = node;
			node = null;
			data = null;
		}

		public void moveCursorTo(int n) {
			if( n >= length && node.getNodeType() == Node.TEXT_NODE ) {// when a text node ends, automatically go to next sibling. It doesn't apply for comment nodes.
				next();
			} else {
			cursor = n;
			}
		}

		private Node getNode() {
			seek(true,true,true);
			return cursor == 0 ? node : document.createTextNode(data.substring(cursor));
		}

		public boolean getsComment(boolean skipSpaces) {
			seek(!skipSpaces,true,false);
			return node != null && node.getNodeType() == Node.COMMENT_NODE;
		}

		public boolean getsEnd() {
			seek(false,false,false);
			return node == null;
		}

		/**
		 * Tests if there is a text to process. It is true when a comment is under process.
		 */
		public boolean getsText() {
			seek(true,false,false);
			return node != null && ( node.getNodeType() == Node.COMMENT_NODE || node.getNodeType() == Node.TEXT_NODE );
		}
		public boolean getsElement() {
			seek(false,false,false);
			return node != null && node.getNodeType() == Node.ELEMENT_NODE;
		}

		public boolean getsElement(String expected) {
			return getsElement() && node.getNodeName().equals(expected);
		}

		/**
		 * Tests if there is a processing instruction.
		 */
		public boolean getsProcessingInstruction() {
			seek(true,true,true);
			return node != null && node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE;
		}

		public void enter() {
			seek(false,false,false);
			if (node == null) {
				throw new SchemaError("BUG: entering null node");
			}
			parent = node;
			previous = null;
			node = null;
		}

		private static int describe(StringBuilder sb, Node parent, Node node, int cursor, int len) {
			if (len <= 0) {
				return len;
			}
			if (node == null) {
				if (parent == null) {
					sb.append("<<closed document>>");
					return 0;
				}
				if (parent.getParentNode() == null) {
					sb.append("<<EOF>>");
					return 0;
				}
				String tagName = parent.getNodeName();
				sb.append("</");
				sb.append(tagName);
				sb.append(">");
				return describe(sb, parent.getParentNode(), parent.getNextSibling(), 0, len - tagName.length() - 3);
			}
			switch (node.getNodeType()) {
			case Node.COMMENT_NODE: {
				String text = node.getTextContent();
				int thislen = text.length();
				sb.append("<!--");
				if (len < 4) {
					sb.append("...");
					return 0;
				}
				if (len - 4 < thislen) {
					sb.append(text.substring(0,len-4));
					sb.append("...");
					return 0;
				}
				sb.append(text);
				sb.append("-->");
				return describe(sb, parent, node.getNextSibling(), 0, len - thislen - 7);
			}
			case Node.TEXT_NODE:
				String text = node.getTextContent().substring(cursor);
				int thislen = text.length();
				if (len < thislen) {
					sb.append(text.substring(0,len));
					sb.append("...");
					return 0;
				}
				sb.append(text);
				return describe(sb, parent, node.getNextSibling(), 0, len - thislen);
			case Node.ELEMENT_NODE:
				String tagName = node.getNodeName();
				sb.append("<");
				sb.append(tagName);
				len = len - 1 - tagName.length();
				NamedNodeMap attrs = node.getAttributes();
				int nAttrs = attrs.getLength();
				for (int i = 0; i < nAttrs; i++ ) {
					if (len <= 0) {
						sb.append("...");
						break;
					}
					Attr attr = (Attr)attrs.item(i);
					String key = attr.getName();
					String val = attr.getValue();
					sb.append(" ");
					sb.append(key);
					sb.append("=\"");
					sb.append(val);
					sb.append("\"");
					len = len - key.length() - val.length() - 4;
				}
				sb.append(">");
				return describe(sb,node,node.getFirstChild(),0,len-1);
			default:
				sb.append("<<unknown node>>");
				return 0;
			}
		}
		/**
		 * Describes what is to be read.
		 */
		public String describeNext() {
			StringBuilder sb = new StringBuilder();
			describe(sb,parent,node,cursor,80);
			return sb.toString();
		}

		public String getAttribute(String name, String defaultValue) {
			Attr attr = ((Element) parent).getAttributeNode(name);
			if (attr == null) {
				expectations.add("@" + name);
				return defaultValue;
			}
			return attr.getValue();
		}

		public String getAttribute(String name) {
			return getAttribute(name, null);
		}

		public Boolean getYesNoAttribute(String name) {
			MatchResult r = getAttributeMatch(name,"(yes)|(no)");
			if( r == null ) return null;
			return r.group(1) != null;
		}

		public boolean getYesNoAttribute(String name, boolean def) {
			Boolean ret = getYesNoAttribute(name);
			if( ret == null ) {
				return def;
			}
			return ret;
		}

		private String requiredAttribute(String name, String ret) {
			if (ret == null) {
				throw new SchemaError("Missing attribute " + name + " in <" + parent.getNodeName() + ">");
			}
			return ret;
		}

		public String requireAttribute(String name) throws SchemaError {
			return requiredAttribute(name, getAttribute(name, null));
		}

		private MatchResult matchAttribute(String name, Pattern p, String input) {
			Matcher ret = p.matcher(input);
			if (ret.matches()) {
				return ret;
			}
			throw new SchemaError("Attribute " + name + " is expected to match " + p + " but is \"" + input
				+ "\" in <" + parent.getNodeName() + ">");
		}
		public MatchResult getAttributeMatch(String name, Pattern p) {
			String input = getAttribute(name);
			if (input == null) {
				return null;
			}
			return matchAttribute(name,p,input);
		}
		public MatchResult requireAttributeMatch(String name, Pattern p) {
			String input = requireAttribute(name);
			return matchAttribute(name,p,input);
		}
		public MatchResult getAttributeMatch(String name, String pattern) {
			return getAttributeMatch(name,Pattern.compile(pattern));
		}

		static final Pattern namePattern = Pattern.compile("[A-Za-z][A-Za-z0-9._\\-]*");

		public String getAttributeForName(String name, String defaultName) {
			MatchResult m = getAttributeMatch(name,namePattern);
			return m == null ? defaultName : m.group();
		}

		public String requireAttributeForName(String name) {
			return requiredAttribute(name, getAttributeForName(name, null));
		}

		private int parseIntAttribute(String name, String value) {
			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException err) {
				throw new SchemaError("attribute " + name + " is expected to be integer but is \"" + value + "\"");
			}
		}

		public Integer getIntegerAttribute(String name, Integer def) {
			String str = getAttribute(name);
			return str == null ? def : (Integer) parseIntAttribute(name, str);
		}

		public int requireIntegerAttribute(String name) {
			return parseIntAttribute(name, requireAttribute(name));
		}

		public void fail() throws SchemaError {
			throw new SchemaError("Encountered " + describeNext() + " while expecting " + expectations);
		}

		public boolean enters(String expected) {
			if (getsElement(expected)) {
				// System.err.println("DBG: entering " + expected);
				expectations.clear();
				enter();
				return true;
			}
			expectations.add("<" + expected + ">");
			return false;
		}

		public void enter(String expected) {
			if (!enters(expected)) {
				fail();
			}
		}

		public void rename(String name) {
			document.renameNode(node,node.getNamespaceURI(),name);
		}

		public void leaveAnyway() {
			if (parent == null) {
				throw new SchemaError("BUG: leaving root");
			}
			expectations.clear();
			previous = parent;
			parent = parent.getParentNode();
			node = null;
		}

		public boolean leaves() throws SchemaError {
			if (getsEnd()) {
				// System.err.println("DBG: leaving </" + reader.parent.getNodeName() + ">");
				leaveAnyway();
				return true;
			} else {
				expectations.add("</" + parent.getNodeName() + ">");
				return false;
			}
		}

		public void leave() {
			if (!leaves()) {
				fail();
			}
		}

		public Matcher matches(Pattern pattern) {
			if( getsText() ) {
				Matcher matcher = pattern.matcher(data).region(cursor,length);
				if( matcher.lookingAt() ) {
					expectations.clear();
					moveCursorTo(matcher.end());
					return matcher;
				}
			} else {// try to match empty
				Matcher matcher = pattern.matcher("");
				if( matcher.lookingAt() ) {
					expectations.clear();
					return matcher;
				}
			}
			expectations.add("\"" + pattern.toString() + "\"");
			return null;
		}

		public Matcher match(Pattern pattern) {
			Matcher ret = matches(pattern);
			if (ret == null) {
				fail();
			}
			return ret;
		}

		public Node readNode() {
			Node ret = getNode();
			if (ret != null) {
				expectations.clear();
				next();
			}
			return ret;
		}

		public ProcessingInstruction readProcessingInstruction() {
			if (!getsProcessingInstruction()) {
				return null;
			}
			return (ProcessingInstruction)readNode();
		}
		public String readComment(boolean skipComment) {
			if (!getsComment(skipComment)) {
				expectations.add("#comment");
				return null;
			}
			String ret = node.getTextContent();
			expectations.clear();
			next();
			return ret;
		}

		public String readText() {
			if (!getsText()) {
				expectations.add("#text");
				return null;
			}
			String ret = data.substring(cursor);
			expectations.clear();
			if( node.getNodeType() == Node.TEXT_NODE ) {
				next();
			}
			return ret;
		}

		/**
		 * Exits the current element, but do not output it. 
		**/
		public Node detach() {
			if(parent == null) {
				throw new SchemaError("BUG: detaching root");
			}
			expectations.clear();
			Node ret = parent;
			parent = ret.getParentNode();
			previous = ret.getPreviousSibling();
			parent.removeChild(ret);
			node = null;
			return ret;
		}

		public void createElement(String name) {
			Element newChild = document.createElement(name);
			seek(true,true,true);
			node = parent.insertBefore(newChild, node);
			// System.err.println("DBG: creating <" + name + ">");
			enter();
		}

		public void removeElement(String name) {
			NodeList chs = parent.getChildNodes();
			for (int i = 0; i < chs.getLength(); i++) {
				Node it = chs.item(i);
				if (it.getNodeName().equals(name)) {
					if (previous == it) {
						previous = it.getPreviousSibling();
					}
					if (node == it) {
						node = null;
						cursor = 0;
					}
					parent.removeChild(chs.item(i));
					break;
				}
			}
		}

		public void move(Node newChild) {
			previous = previous == null ?
				parent.appendChild(newChild) :
				parent.insertBefore(newChild,node);
		}

		public void write(String text) {
			// System.err.println("DBG: writing \"" + text + "\"");
			if (previous != null && previous.getNodeType() == Node.TEXT_NODE) {
				// When previous is a text node, append to it
				previous.setTextContent(previous.getTextContent() + text);
			} else {
				move(document.createTextNode(text));
			}
		}

		public void write(Node node) {
			if (node.getNodeType() == Node.TEXT_NODE) {
				write(node.getTextContent());
			} else {
				move(document.importNode(node, true));
			}
		}

		public void write(NodeList nodes) {
			int n = nodes.getLength();
			for (int i = 0; i < n; i++) {
				write(nodes.item(i));
			}
		}

		public void comment(String str) {
			move(document.createComment(str));
		}

		public void writeTextElement(String name, String data) {
			createElement(name);
			write(data);
			leave();
		}

		public void setAttribute(String name, String value) {
			((Element) parent).setAttribute(name, value);
		}

		public void enclose(String name) {
			Element newNode = document.createElement(name);
			Node child = parent.getFirstChild();
			while (child != null) {
				Node nextChild = child.getNextSibling();
				newNode.appendChild(child);
				child = nextChild;
			}
			parent.appendChild(newNode);
			child = null;
		}

		public void insertProcessingInstruction(String target, String data) {
			move(document.createProcessingInstruction(target,data));
		}
	}
}
