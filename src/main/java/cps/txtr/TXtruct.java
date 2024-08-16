// -*- coding: utf-8-unix; -*-

package cps.txtr;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.logging.*;

import org.w3c.dom.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import org.xml.sax.SAXException;

import org.apache.commons.text.StringEscapeUtils;

import cps.txtr.XML.SchemaError;
import cps.txtr.XML.Walker;

public class TXtruct extends XML.ToNode {
	static Logger logger = Logger.getLogger("txtr.TXtruct.logger");
	static ConsoleHandler handler = new ConsoleHandler();
	static {
		handler.setFormatter(new SimpleFormatter());
		logger.addHandler(handler);
	}
	static Processor nlpInstance;
	enum State {
		SUCCESS, MAYBE, RECOVER, 
	}
	private enum Escape {
		HTML, XML, NONE,
	}
	static abstract class Processor extends XML.ToNode {
		public abstract State apply(boolean forced, XML.Walker... ports);
		public Processor on(int... indices) {
			return new Switcher(this,indices);
		}
		public Processor on(List<Integer> indices) {
			return new Switcher(this,indices);
		}
		public Processor mutable() {
			return new Mutable(this);
		}
	}
	static class Switcher extends Processor {
		Processor inner;
		int[] indices;
		Switcher( Processor inner, int... indices ) {
			this.inner = inner;
			this.indices = indices;
		}
		Switcher( Processor inner, List<Integer> indices ) {
			this.inner = inner;
			int n = indices.size();
			this.indices = new int[n];
			for( int i = 0; i < n; i++ ) {
				this.indices[i] = indices.get(i);
			}
		}
		@Override public Node toNode() {
			Element ret = XML.document.createElement("switcher");
			for( int i : indices ) {
				Element port = XML.document.createElement("port");
				port.setAttribute("index",String.valueOf(i));
				ret.appendChild(port);
			}
			ret.appendChild(inner.toNode());
			return ret;
		}
		@Override public State apply(boolean forced, Walker... ports) {
			List<Walker> newports = new ArrayList<>();
			for( int i : indices ) {
				newports.add(
					i > 0 ? ports[i-1] :
					i < 0 ? new Walker(ports[-i-1].root) :// clone node
					Walker.create()
					);
			}
			return inner.apply( forced, newports.toArray(new Walker[indices.length]) );
		}
	}

	static class Mutable extends Processor {
		Processor inner;
		public Mutable(Processor inner) {
			this.inner = inner;
		}
		@Override public Node toNode() {
			Element ret = XML.document.createElement("mutable");
			ret.appendChild(inner.toNode());
			return ret;
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			inner.apply(true,ports);
			return State.MAYBE;
		}
	}
	static class Nop extends Processor {
		@Override public State apply( boolean forced, XML.Walker... ports ) {
			return forced ? State.SUCCESS : State.MAYBE;
		}
		@Override public Node toNode() {
			return XML.createElement("nop");
		}
		@Override public Processor on(int... indices) {
			return this;
		}
	}
	static final Nop NOP = new Nop();
	static class Control extends Processor {
		String handler;
		String message;
		public Control( String h, String m ) {
			handler = h;
			message = m;
		}
		@Override public Node toNode() {
			Element ret = XML.createElement("dump");
			ret.setAttribute("handler",handler);
			ret.setAttribute("message",message);
			return ret;
		}
		@Override public State apply( boolean forced, XML.Walker... ports ) {
			if( handler.equals("txtr") ) {
				switch(message) {
					case "dump": System.err.println("Dumping: " + ports[0].toString()); break;
					default: System.err.println( "Unknown control message: " + message ); break;
				}
			}
			return forced ? State.SUCCESS : State.MAYBE;
		}
	}
	static class Any extends Processor {
		@Override public Node toNode() {
			return XML.createElement("any");
		}
		@Override public State apply( boolean forced, XML.Walker... ports ) {
			Node node = ports[0].readNode();
			if( node == null ) {
				if( forced ) {
					ports[0].fail();
				}
				return State.RECOVER;
			}
			ports[1].write(node);
			return State.SUCCESS;
		}
		static final Any ON1_2 = new Any();
		static final Processor ON1 = ON1_2.on(1,0);
		static final Processor ON1_3 = ON1_2.on(1,3);
	}
	static class Repeater extends Processor {
		Processor inner;
		int minOccurs;
		Integer maxOccurs;
		Repeater(Processor inner, int minOccurs, Integer maxOccurs) {
			this.inner = inner;
			this.minOccurs = minOccurs;
			this.maxOccurs = maxOccurs;
		} 
		static Processor of(Processor inner, int minOccurs, Integer maxOccurs) {
			return minOccurs == 1 && maxOccurs != null && maxOccurs == 1 ? inner : new Repeater(inner,minOccurs,maxOccurs);
		}
		@Override public Node toNode() {
			Element ret = XML.createElement("repeater");
			ret.setAttribute("minOccurs", String.valueOf(minOccurs));
			ret.setAttribute("maxOccurs", maxOccurs == null ? "unbounded" : maxOccurs.toString());
			ret.appendChild(inner.toNode());
			return ret;
		}
		private void wrong(XML.Walker... ports) {
			throw new XML.SchemaError( "Accepted empty in repeat at: " + ports[0].describeNext() + "\n Processor:\n" + XML.toString(this.toNode()) );
		}
		private boolean processOptional(XML.Walker... ports) {
			switch( inner.apply(false,ports) ) {
				case RECOVER: return false;
				case MAYBE: wrong(ports);
				default: return true;
			}
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			if( minOccurs == 0 ) {
				switch( inner.apply(false,ports) ) {
					case RECOVER: return forced ? State.SUCCESS : State.MAYBE;// empty was parsed
					default: break;
				}
			} else {
				switch( inner.apply(forced,ports) ) {
					case MAYBE:// if inner accepts empty, repeating it won't terminate. 
						wrong(ports);
					case RECOVER: return State.RECOVER;// this case implies not forced
					default: break;
				}
			}
			int i = 1;
			for(;i<minOccurs;i++) {
				inner.apply(true,ports);
			}
			if( maxOccurs == null ) {
				while( processOptional(ports) );
			} else {
				for( ; i < maxOccurs && processOptional(ports); i++ );
			}
			return State.SUCCESS;
		}
	}
	
	static class Director extends Processor {
		@Override public Node toNode() {
			return XML.createElement("direct");
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			for(;;) {
				Node node = ports[0].readNode();
				if( node == null ) {
					return State.SUCCESS;
				}
				ports[1].write(node);
			}
		}
		static final Director instance = new Director();
		static final Processor ON1_2 = instance;
		static final Processor ON3_2 = instance.on(3,2);
		static final Processor ON1_3 = instance.on(1,3);
	}
	static class Sequence extends Processor {
		public List<Processor> steps;
		@Override public Node toNode() {
			Element ret = XML.document.createElement("group");
			for( Processor step : steps ) {
				ret.appendChild(step.toNode());
			}
			return ret;
		}
		public String toString() {
			return "Sequence " + steps.toString();
		}
		private Sequence(List<Processor> steps) {
			this.steps = steps;
		}
		static Processor of(Processor... steps) {
			int n = steps.length;
			if( n == 0 ) {
				return NOP;
			}
			if( n == 1 ) {
				return steps[0];
			}
			List<Processor> arr = new ArrayList<>();
			for( Processor step : steps ) {
				if( step instanceof Sequence ) {
					arr.addAll(((Sequence)step).steps);
				} else {
					arr.add(step);
				}
			}
			return new Sequence(arr);
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			int n = steps.size();
			if( n == 0 ) {
				return forced ? State.SUCCESS : State.MAYBE;
			}
			int i = 0;
			for(;;) {
				State ret = steps.get(i).apply(forced,ports);
				i++;
				if( i == n ) {// no more to apply
					return ret;
				}
				switch( ret ) {
				case SUCCESS:
					for( ; i < n-1; i++ ) {// rests must succeed
						steps.get(i).apply(true,ports);
					}
					// tail recursion
					return steps.get(i).apply(true,ports);
				case MAYBE:
					continue;
				default:
					return ret;
				}
			}
		}
	}
	static class TextBuilder extends Processor {
		String data;
		@Override public Node toNode() {
			Element ret = XML.createElement("text");
			ret.appendChild(XML.createTextNode(data));
			return ret;
		}
		public TextBuilder( String data ) {
			this.data = data;
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			ports[0].write(data);
			return forced ? State.SUCCESS : State.MAYBE;
		}
	}

	static class NodeBuilder extends Processor {
		@Override public final Node toNode() {
			return XML.createElement("create-node");
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			String tagName = ports[0].readText();
			logger.finer("Building element <" + tagName + ">");
			ports[1].createElement(tagName);
			return State.SUCCESS;
		}
		private NodeBuilder() {}
		static final NodeBuilder INSTANCE = new NodeBuilder();
		public static Processor forName(String name) {
			return Sequence.of(new TextBuilder(name), INSTANCE.on(-1,2)).on(0,1);
		}
	}
	static class ElementLeaver extends Processor {
		@Override public final Node toNode() {
			return XML.createElement("element");
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			ports[0].leave();
			return State.SUCCESS;
		}
		static final ElementLeaver ON1 = new ElementLeaver();
		static final Processor ON2 = ON1.on(2);
		static final Processor ON3 = ON1.on(3);
		static final Processor ON4 = ON1.on(4);
		static final Processor ON5 = ON1.on(5);
	}
	static class Attributor extends Processor {
		@Override public Node toNode() {
			return XML.createElement("attribute");
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			Node att = ports[0].detach();// this will be considered as an attribute
			ports[0].setAttribute(att.getNodeName(), att.getTextContent());
			return State.SUCCESS;
		}
		public static final Attributor ON1 = new Attributor();
		public static final Processor ON3 = ON1.on(3);
	}
	static class Commenter extends Processor {
		@Override public Node toNode() {
			return XML.createElement("comment");
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			Node cmt = ports[0].detach();// this will be considered as a comment
			ports[0].comment(cmt.getTextContent());
			return State.SUCCESS;
		}
		public static final Commenter ON1 = new Commenter();
		public static final Processor ON3 = ON1.on(3);
	}

	static private class EvaluatorBase {
		private static XPath xpath = XPathFactory.newInstance().newXPath();
		static XPathExpression compile(String str) {
			try {
				return xpath.compile(str);
			} catch (XPathExpressionException err) {
				throw new Error("ERR@Evaluator: " + str + "\n" + err.getMessage());
			}
		}
		static State eval(String str, XPathExpression expr, Node node, Walker out) {
			logger.finer("Evaluating \"" + str + "\"");
			try {
				try {
					NodeList nodes = (NodeList) expr.evaluate(node,XPathConstants.NODESET);
					int n = nodes.getLength();
					for( int i = 0; i < n; i++ ) {
						out.write(nodes.item(i).getChildNodes());
					}
					return State.SUCCESS;
				} catch(XPathException err) {
					out.write(expr.evaluate(node));
					return State.SUCCESS;
				}
			} catch( Exception err ) {
				logger.severe("Error: Evaluation \"" + str + "\" on [" + node.toString() + "]");
				throw new Error(err.getMessage());
			}
		}
		static State copy( String str, XPathExpression expr, Node node, Walker out ) {
			try {
				out.write((NodeList)expr.evaluate(node,XPathConstants.NODESET) );
				return State.SUCCESS;
			} catch( Exception err ) {
				logger.severe("Error: Evaluation \"" + str + "\" on [" + node.toString() + "]");
				throw new Error(err.getMessage());
			}
		}
		static boolean test( String str, XPathExpression expr, Node node ) {
			try {
				return (boolean)expr.evaluate(node,XPathConstants.BOOLEAN);
			} catch( Exception err ) {
				logger.severe("Error: Evaluation \"" + str + "\" on [" + node.toString() + "]");
				throw new Error(err.getMessage());
			}
		}
	}
	static class Copier extends Processor {
		@Override public Node toNode() {
			return XML.createElement("copier");
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			String str = ports[1].readText();
			return EvaluatorBase.copy(str,EvaluatorBase.compile(str),ports[0].parent,ports[2]);
		}
		public static final Copier INSTANCE = new Copier();
	}
	static class Evaluator extends Processor {
		@Override public Node toNode() {
			return XML.createElement("evaluator");
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			String str = ports[1].readText();
			return EvaluatorBase.eval(str,EvaluatorBase.compile(str),ports[0].parent,ports[2]);
		}
		public static final Evaluator INSTANCE = new Evaluator();
	}
	static class CommentReader extends Processor {
		boolean skipComment;
		Processor inner;
		public CommentReader( Processor inner, boolean skipComment ) {
			this.inner = inner;
			this.skipComment = skipComment;
		}
		@Override public Node toNode() {
			return XML.createElement("in-comment");
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			Walker in = ports[0];
			if( in.getsComment(skipComment) ) {
				inner.apply(true,ports);
				in.next();
				return State.SUCCESS;
			}
			if( forced ) {
				in.fail();
			}
			return State.RECOVER;
		}
	}
	static class TextReader extends Processor {
		Escape decode;
		Escape encode;
		TextReader( Escape dec, Escape enc ) {
			decode = dec;
			encode = enc;
		}
		@Override public Node toNode() {
			Element ret = XML.createElement("read-text");
			ret.setAttribute("decode",decode.toString());
			ret.setAttribute("encode",encode.toString());
			return ret;
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			String ret = ports[0].readText();
			if( ret == null ) {
				return State.RECOVER;
			}
			switch(decode) {
				case HTML: ret = StringEscapeUtils.unescapeHtml4(ret); break;
				case XML: ret = StringEscapeUtils.unescapeXml(ret); break;
				case NONE: break;
			}
			switch(encode) {
				case XML: ret = StringEscapeUtils.escapeXml11(ret); break;
				case HTML: ret = StringEscapeUtils.escapeHtml4(ret); break;
				case NONE: break;
			}
			ports[1].write(ret);
			return State.SUCCESS;
		}
		static final Processor ON1_2 = new TextReader(Escape.NONE,Escape.NONE);
		static final Processor ON1_3 = ON1_2.on(1,3);
		static final Processor ON1 = ON1_2.on(1,0);
	}
	static class PatternMatcher extends Processor {
		@Override public final Node toNode() {
			return XML.createElement("matcher");
		}
		private PatternMatcher() {}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			String regex = ports[0].readText();
			Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
			Matcher matcher = ports[1].matches(pattern);
			if( matcher == null ) {
				if( forced ) {
					ports[1].fail();
				}
				return State.RECOVER;
			}
			String text = matcher.group();
			logger.finer( "Match: \"" + regex + "\" matches \"" + text + "\"" );
			ports[2].writeTextElement( "match", text );
			int n = matcher.groupCount();
			for( int i = 0; i < n; i++ ) {
				ports[2].writeTextElement( "group", matcher.group(i + 1) );
			}
			return State.SUCCESS;
		}
		public static final PatternMatcher INSTANCE = new PatternMatcher();
	}
	static class ElementReader extends Processor {
		private String name;
		@Override public Node toNode() {
			Element ret = XML.createElement("read-element");
			ret.setAttribute("name", name);
			return ret;
		}
		public ElementReader(String name) {
			this.name = name;
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			Walker in = ports[0];
			Walker out = ports[1];
			if( in.enters(name) ) {
				out.write((Element)in.toNode());
				in.leaveAnyway();
				logger.finer( "Reading <" + name + ">" );
				return State.SUCCESS;
			}
			if( forced ) {
				in.fail();
			}
			return State.RECOVER;
		}
	}
	static class ElementOpener extends Processor {
		private String name;
		@Override public Node toNode() {
			Element ret = XML.createElement("open-element");
			ret.setAttribute("name", name);
			return ret;
		}
		public ElementOpener(String name) {
			this.name = name;
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			Walker in = ports[0];
			if( in.enters(name) ) {
				logger.finer( "Opening <" + name + ">" );
				return State.SUCCESS;
			}
			if( forced ) {
				in.fail();
			}
			return State.RECOVER;
		}
	}
	static class AttributeReader extends Processor {
		private String name;
		private String def;
		@Override public Node toNode() {
			Element ret = XML.createElement("attribute-reader");
			ret.setAttribute("name", name);
			if( def != null ) {
				ret.setAttribute("default", def);
			}
			return ret;
		}
		public AttributeReader(String name, String def) {
			this.name = name;
			this.def = def;
		}
		@Override public State apply(boolean forced, Walker... ports) {
			Walker in = ports[0];
			Walker out = ports[1];
			String value = in.getAttribute(name,def);
			if( value == null ) {
				if( forced ) {
					in.fail();
				}
				return State.RECOVER;
			} else {
				logger.finer("@"+name+"=\""+value+"\"");
				out.write(value);
				return State.SUCCESS;
			}
		}
	}
	static class Choice extends Processor {
		public ArrayList<Processor> options = new ArrayList<>();
		@Override public Node toNode() {
			Element ret = XML.createElement("choice");
			for( Processor option : options ) {
				ret.appendChild(option.toNode());
			}
			return ret;
		}
		@Override public State apply( boolean forced, XML.Walker... ports ) {
			int n = options.size() - 1;
			if( n == -1 ) {
				if( forced ) {
					throw new SchemaError("Empty option");
				}
				return State.RECOVER;
			}
			logger.finest("Trying choice\n" + XML.toString(toNode()) + "on:\n" + ports[0].describeNext());
			for( int i = 0; i < n; i++ ) {
				Processor option = options.get(i);
				logger.finest("Trying option " + option);
				State ret = option.apply(false,ports);
				if( ret == State.RECOVER ) {
					continue;
				}
				logger.finer("Applied option " + option);
				return ret;
			}
			logger.finest("Trying default option");
			return options.get(n).apply(forced,ports);
		}
	}
	static class Conditional extends Processor {
		static class Case extends XML.ToNode {
			XPathExpression expr;
			String str;
			Processor inner;
			@Override public Node toNode() {
				Element ret = XML.createElement("when");
				ret.setAttribute("test", str);
				ret.appendChild(inner.toNode());
				return ret;
			}
			Case(String str, Processor inner) {
				this.str = str;
				this.inner = inner;
				expr = EvaluatorBase.compile(str);
			}
		}
		List<Case> cases;
		Processor def;
		public Conditional(List<Case> cases, Processor def) {
			this.cases = cases;
			this.def = def;
		}
		@Override public Node toNode() {
			return XML.document.createElement("conds");
		}
		@Override public State apply( boolean forced, XML.Walker... ports ) {
			for( Case c : cases ) {
				if( EvaluatorBase.test( c.str, c.expr, ports[1].toNode() ) ) {
					logger.finer( "Condition \"" + c.str + "\" is fired" );
					return c.inner.apply(forced,ports);
				}
			}
			return def.apply(forced,ports);
		}
	}
	
	static private class TransformerTable extends XML.ToNode {
		class Entry extends XML.ToNode {
			SortedMap<Integer, List<Processor>> leveled;
			List<Processor> anyLevel;
			@Override public Node toNode() {
				DocumentFragment ret = XML.createDocumentFragment();
				leveled.forEach( (l, trs) -> {
					Element e = XML.createElement("option");
					e.setAttribute("level", l.toString());
					trs.forEach( tr -> { e.appendChild(tr.toNode()); } );
					ret.appendChild(e);
				});
				Element e = XML.createElement("option");
				anyLevel.forEach( tr -> { e.appendChild(tr.toNode()); } );
				ret.appendChild(e);
				return ret;
			}
			Entry() {
				leveled = new TreeMap<>();
				anyLevel = new ArrayList<>();
			}
			Choice get(Integer level) {
				Choice ret = new Choice();
				if( level != null ) {
					for( List<Processor> ts : leveled.tailMap(level).values() ) {
						ret.options.addAll(ts);
					}
				}
				ret.options.addAll(anyLevel);
				return ret;
			}
			void add(Integer level, Processor option) {
				if( level == null ) {
					anyLevel.add(option);
					return;
				}
				List<Processor> t = leveled.get(level);
				if (t == null) {
					t = new ArrayList<>();
					leveled.put(level,t);
				}
				t.add(option);
			}
		}
		Map<String, Entry> table;
		@Override public Node toNode() {
			Element ret = XML.createElement("classes");
			table.forEach( (name, entry) -> {
				Element e = XML.createElement("class");
				e.setAttribute("name", name);
				e.appendChild(entry.toNode());
				ret.appendChild(e);
			});
			return ret;
		}
		public TransformerTable() {
			table = new HashMap<>();
		}
		Processor get(String name, Integer level) {
			Entry e = table.get(name);
			if (e == null) {
				throw new RuntimeException("TXtruct: unknown class " + name);
			}
			return e.get(level);
		}
		void add(String name, Integer level, Processor option) {
			Entry e = table.get(name);
			if (e == null) {
				e = new Entry();
				table.put(name, e);
			}
			e.add(level, option);
		}
	}
	private TransformerTable table = new TransformerTable();
	private class TransformerReference extends Processor {
		String ref;
		Integer level;
		@Override public Node toNode() {
			Element ret = XML.createElement("call");
			ret.setAttribute("ref", ref);
			if( level != null ) {
				ret.setAttribute("level", level.toString());
			}
			return ret;
		}
		public TransformerReference(String ref, Integer level) {
			this.ref = ref;
			this.level = level;
		}
		@Override public State apply(boolean forced, XML.Walker... ports) {
			logger.finer( "Applying class " + ref + " level: " + level );
			logger.finest( () -> "on ports: " + Arrays.stream(ports).map( x -> XML.toString(x.toNode()) ).collect(java.util.stream.Collectors.joining(",")) );
			return table.get(ref,level).apply(forced,ports);
		}
	}
	private Processor outputNamer(String name, Processor reader) {
		return name == null ? reader :
			Sequence.of( reader,
				NodeBuilder.forName(name).on(2),
				Director.instance.on(-3,2),
				ElementLeaver.ON2
			).on(1,2,0);
	}
	private int readMinOccurs(XML.Walker in) {
		return in.getIntegerAttribute("minOccurs",1);
	}
	private Integer readMaxOccurs(XML.Walker in) {
		MatchResult m = in.getAttributeMatch("maxOccurs","([1-9][0-9]*)|(unbounded)");
		if( m == null ) {
			return 1;
		}
		String v = m.group(1);
		if( v != null ) {
			return Integer.valueOf(v);
		}
		return null;
	}
	static Escape readEscapeAttribute( XML.Walker in, String name ) {
		MatchResult dm = in.getAttributeMatch( name, "(xml)|(html)|(none)" );
		return dm == null || dm.group(3) != null ? Escape.NONE :
			dm.group(1) != null ? Escape.XML : Escape.HTML;
	}
	private Processor readTransformer(XML.Walker in) {
		if( in.enters("control") ) {
			String handler = in.getAttribute("handler");
			String message = in.getAttribute("message");
			in.leave();
			return new Control(handler,message);
		}
		if( in.enters("match") ) {
			String as = in.getAttributeForName("as",null);
			int minOccurs = readMinOccurs(in);
			Integer maxOccurs = readMaxOccurs(in);
			String regex = in.getAttribute("pattern");
			Processor regexer;
			if( regex == null ) {
				regexer = readSequence(in,NOP);
			} else {
				regexer = new TextBuilder(regex).on(3);
			}
			in.leave();
			Processor step = as == null ?
				Sequence.of( regexer.mutable(), PatternMatcher.INSTANCE.on(-3,1,0) ).on(1,2,0) :
				Sequence.of(
					regexer.mutable(),
					PatternMatcher.INSTANCE.on(-3,1,4),
					NodeBuilder.forName(as).on(2),
					Director.instance.on(-4,2),
					ElementLeaver.ON2
				).on(1,2,0,0);
			return Repeater.of( step, minOccurs, maxOccurs );
		}
		if( in.enters("call") ) {
			String ref = in.requireAttribute("ref");
			Integer level = in.getIntegerAttribute("level",null);
			String as = in.getAttributeForName("as",null);
			int minOccurs = readMinOccurs(in);
			Integer maxOccurs = readMaxOccurs(in);
			Processor args = readSequence(in,null);
			in.leave();
			Processor reader = new TransformerReference(ref,level);
			return args == null ?
				Repeater.of(
					as == null ? reader.on(1,0,3) :
					Sequence.of(
						reader.on(1,0,3),
						NodeBuilder.forName(as).on(2),
						Director.instance.on(-3,2),
						ElementLeaver.ON2
					).on(1,2,0),
					minOccurs,
					maxOccurs
				) :
				Sequence.of(
					args.on(1,2,4).mutable(),
					Repeater.of(
						as == null ?
							Sequence.of(
								Director.instance.on(-4,5).mutable(),
								reader.on(1,-5,3)
							).on(1,2,3,4,0) :
							Sequence.of(
								Director.instance.on(-4,5).mutable(),
								reader.on(1,-5,6),
								NodeBuilder.forName(as).on(2),
								Director.instance.on(-6,2),
								ElementLeaver.ON2
							).on(1,2,3,4,0,0),
						minOccurs,
						maxOccurs
					)
				).on(1,2,3,0);
		}
		if( in.enters("cascade") ) {
			int minOccurs = readMinOccurs(in);
			Integer maxOccurs = readMaxOccurs(in);
			String as = in.getAttributeForName("as",null);
			List<Processor> steps = new ArrayList<>();
			steps.add( readTransformer(in).on(2,1,3) );
			steps.add( readTransformer(in).on(-3,1,4) );
			List<Integer> indices = new ArrayList<>();
			Collections.addAll(indices,2,1,0);
			for( int i=4;; ) {
				Processor step = readTransformer(in);
				if( step == null ) {
					in.leave();
					indices.add(3);
					return Repeater.of( outputNamer( as, new Sequence(steps).on(indices) ), minOccurs, maxOccurs );
				}
				steps.add(step.on(-i,1,++i));
				indices.add(0);
			}
		}
		if( in.enters("in-element") ) {
			int minOccurs = readMinOccurs(in);
			Integer maxOccurs = readMaxOccurs(in);
			String name = in.requireAttribute("name");
			String as = in.getAttributeForName("as",null);
			Processor inner = readSequence(in,NOP);
			in.leave();
			Processor main = Sequence.of( new ElementOpener(name).on(1), inner, ElementLeaver.ON1 );
			return Repeater.of(outputNamer(as,main),minOccurs,maxOccurs);
		}
		if( in.enters("read-text") ) {
			String as = in.getAttributeForName("as",null);
			Escape dec = readEscapeAttribute(in,"decode");
			Escape enc = readEscapeAttribute(in,"encode");
			in.leave();
			return outputNamer( as, new TextReader(dec,enc).on(1,3) );
		}
		if( in.enters("read-attribute") ) {
			String name = in.requireAttributeForName("name");
			String as = in.getAttributeForName("as",name);
			String def = in.getAttribute("default");
			String pattern = in.getAttribute("pattern");
			String cls = pattern == null ? in.getAttribute("class") : null;
			MatchResult m = in.getAttributeMatch("minOccurs","(0)|(1)");
			int minOccurs = m == null || m.group(2) != null ? 1 : 0;
			in.leave();
			Processor reader = new AttributeReader(name,def);
			Processor inner =
				pattern != null ? Sequence.of(
					reader,
					new TextBuilder(pattern).on(4),
					PatternMatcher.INSTANCE.on(-4,-2,3)
				).on(1,0,3,0) :
				cls != null ? Sequence.of( reader, new TransformerReference(cls,null).on(-2,0,3) ) :
				reader.on(1,3);
			Processor step = Sequence.of( inner, NodeBuilder.forName(as).on(4), Director.instance.on(-3,4),ElementLeaver.ON4 ).on(1,0,0,2);
			return Repeater.of(step,minOccurs,1);
		}
		if( in.enters("read-any") ) {
			int minOccurs = in.getIntegerAttribute("minOccurs",1);
			Integer maxOccurs = readMaxOccurs(in);
			String as = in.getAttributeForName("as",null);
			in.leave();
			return Repeater.of(
				as == null ? Any.ON1 :
				Sequence.of(Any.ON1_2, NodeBuilder.forName(as).on(3), Director.instance.on(-2,3), ElementLeaver.ON3).on(1,0,2),
				minOccurs,
				maxOccurs
			);
		}
		if( in.enters("read-element") ) {
			int minOccurs = in.getIntegerAttribute("minOccurs",1);
			Integer maxOccurs = readMaxOccurs(in);
			String as = in.getAttributeForName("as",null);
			String name = in.requireAttributeForName("name");
			in.leave();
			Processor reader = new ElementReader(name);
			Processor main = as == null ? reader.on(1,0) : Sequence.of(reader,NodeBuilder.forName(as).on(3), Director.instance.on(-2,3), ElementLeaver.ON3).on(1,0,2);
			return Repeater.of(main,minOccurs,maxOccurs);
		}
		if( in.enters("in-comment") ) {
			int minOccurs = in.getIntegerAttribute("minOccurs",1);
			Integer maxOccurs = readMaxOccurs(in);
			String as = in.getAttributeForName("as",null);
			boolean skipSpaces = in.getYesNoAttribute("skipSpaces",false);
			Processor inner = readSequence(in,NOP);
			in.leave();
			Processor main = new CommentReader(inner,skipSpaces);
			return Repeater.of(outputNamer(as,main),minOccurs,maxOccurs);
		}
		if( in.enters("read-comment") ) {
			int minOccurs = in.getIntegerAttribute("minOccurs",1);
			Integer maxOccurs = readMaxOccurs(in);
			String as = in.getAttributeForName("as",null);
			boolean skipSpaces = in.getYesNoAttribute("skipSpaces",false);
			in.leave();
			Processor main = as == null ? new CommentReader(TextReader.ON1,skipSpaces) :
				Sequence.of( new CommentReader(TextReader.ON1_3,skipSpaces), NodeBuilder.forName(as).on(2), Director.instance.on(-3,2), ElementLeaver.ON2).on(1,2,0);
			return Repeater.of(main,minOccurs,maxOccurs);
		}
		if( in.enters("group") ) {
			int minOccurs = in.getIntegerAttribute("minOccurs",1);
			Integer maxOccurs = readMaxOccurs(in);
			String as = in.getAttributeForName("as",null);
			Processor main = readSequence(in,NOP);
			in.leave();
			return Repeater.of(outputNamer(as,main),minOccurs,maxOccurs);
		}
		if( in.enters("choice") ) {
			int minOccurs = in.getIntegerAttribute("minOccurs",1);
			Integer maxOccurs = readMaxOccurs(in);
			String as = in.getAttributeForName("as",null);
			Choice main = new Choice();
			for(;;){
				Processor option = readTransformer(in);
				if( option == null ) {
					break;
				}
				main.options.add(option);
			}
			in.leave();
			return Repeater.of(outputNamer(as,main),minOccurs,maxOccurs);
		}
		if( in.enters("element") ) {
			Processor namer;
			String name = in.getAttributeForName("name",null);
			if( name == null ) {
				in.enter("name");
				namer = readSequence(in,null).on(1,2,4);
				in.leave();
			} else {
				namer = new TextBuilder(name).on(4);
			}
			Processor inner = readSequence(in,NOP);
			in.leave();
			return Sequence.of( namer, NodeBuilder.INSTANCE.on(-4,3), inner, ElementLeaver.ON3 ).on(1,2,3,0);
		}
		if( in.enters("attribute") ) {
			Processor namer;
			String name = in.getAttributeForName("name",null);
			if( name == null ) {
				in.enter("name");
				namer = readSequence(in,null).on(1,2,4);
				in.leave();
			} else {
				namer = new TextBuilder(name).on(4);
			}
			Processor inner = readSequence(in,NOP);
			in.leave();
			return Sequence.of( namer, NodeBuilder.INSTANCE.on(-4,3), inner, Attributor.ON3 ).on(1,2,3,0);
		}
		if( in.enters("comment") ) {
			Processor inner = readSequence(in,NOP);
			in.leave();
			return Sequence.of( NodeBuilder.forName("comment").on(3), inner, Commenter.ON3 );
		}
		boolean vorc = false;
		if( in.enters("value-of") && (vorc = true) || in.enters("copy-of") ) {
			String expression = in.getAttribute("select");
			MatchResult pm = in.getAttributeMatch("in","(input)|(output)|(mid)");
			int p1 = pm == null || pm.group(3) != null ? 2 : pm.group(1) != null ? 1 : 3;
			Escape dec = readEscapeAttribute(in,"decode");
			Processor mkexp = expression == null ? readSequence(in,NOP) : new TextBuilder(expression).on(3);
			in.leave();
			Processor b = vorc ? Evaluator.INSTANCE : Copier.INSTANCE;
			return dec == Escape.NONE ? Sequence.of(mkexp,b.on(1,-3,4)).on(p1,2,0,3) :
				Sequence.of(mkexp,b.on(1,-3,4),new TextReader(dec,Escape.NONE).on(-4,5) ).on(p1,2,0,0,3);
		}
		if( in.enters("text") ) {
			String as = in.getAttributeForName("as",null);
			String text = in.readText();
			if( text == null ) {
				in.fail();
			}
			in.leave();
			return outputNamer( as, new TextBuilder(text).on(3) );
		}
		if( in.enters("choose") ) {
			List<Conditional.Case> cases = new ArrayList<>();
			while( in.enters("when") ) {
				String test = in.requireAttribute("test");
				Processor inner = readSequence(in,NOP);
				in.leave();
				cases.add( new Conditional.Case(test,inner) );
			}
			in.enter("otherwise");
			Processor def = readSequence(in,NOP);
			in.leave();
			in.leave();
			return new Conditional(cases,def);
		}
		if( in.enters("if") ) {
			String test = in.requireAttribute("test");
			Processor inner = readSequence(in,NOP);
			in.leave();
			return new Conditional( Collections.singletonList( new Conditional.Case(test,inner)), NOP);
		}
		if( in.enters("StanfordNLP") ) {
			in.leave();
			if( nlpInstance == null ) {
				try {
					nlpInstance = ((Processor) Class.forName("cps.txtr.NLP").getDeclaredConstructor().newInstance()).on(1,3);
				} catch( Exception e ) {
					throw new SchemaError("NLP is not allowed.");
				}
			}
			return nlpInstance;
		}
		String text = in.readText();
		if( text == null ) {
			return null;
		}
		return new TextBuilder(text).on(3);
	}
	
	private Processor readSequence(XML.Walker in, Processor def) {
		Processor first = readTransformer(in);
		if( first == null ) {
			return def;
		}
		ArrayList<Processor> nexts = new ArrayList<>();
		nexts.add(first);
		for(;;) {
			Processor next = readTransformer(in);
			if( next == null ) {
				return Sequence.of(nexts.toArray(new Processor[nexts.size()]));
			}
			nexts.add(next);
		}
	}
	private boolean processTableEntry(XML.Walker in) {
		if( in.enters("class") ) {
			String name = in.requireAttribute("name");
			if( in.enters("option") ) {
				do {
					Integer level = in.getIntegerAttribute("level",null);
					Processor tr = readSequence(in,NOP);
					in.leave();
					table.add( name, level, tr );
				} while( in.enters("option") );
			} else {
				table.add( name, null, readSequence(in,NOP) );
			}
			in.leave();
			return true;
		}
		return false;
	}
	
	private void processTable(XML.Walker in) {
		while( processTableEntry(in) );
	}
	
	private Processor parser;
	
	@Override public Node toNode() {
		Element ret = XML.createElement("txtruct");
		ret.appendChild(parser.toNode());
		return ret;
	}
	static TransformerFactory transformerFactory = TransformerFactory.newInstance();
	private Transformer transformer;
	private Charset inCharset;
	private Charset outCharset;
	private static enum Method { TEXT, XML, HTML, XML_FRAGMENT, HTML_FRAGMENT };
	private Method inMethod;
	private Method outMethod;
	private static Method processMethodAttribute(XML.Walker in) {
			MatchResult m = in.getAttributeMatch("method","(xml-fragment)|(text)|(xml)|(html)|(html-fragment)");
			return
				m == null || m.group(1) != null ? Method.XML_FRAGMENT :
				m.group(2) != null ? Method.TEXT :
				m.group(3) != null ? Method.XML :
				m.group(4) != null ? Method.HTML :
				Method.HTML_FRAGMENT;
	}
	private boolean decode = false;// output de-escaping
	private void processIndentAttributes(XML.Walker in) {
		transformer.setOutputProperty(OutputKeys.INDENT,in.getAttribute("indent","yes"));
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", in.getAttribute("indent-amount","2"));
	}
	private void readTXtr(Node txtr, String dir) throws IOException, SAXException {
		XML.Walker in = new XML.Walker(txtr);
		in.enter("txtruct");
		if( in.enters("description") ) {
			in.leaveAnyway();
		}
		if( in.enters("input") ) {
			inMethod = processMethodAttribute(in);
			String inEnc = in.getAttribute("encoding");
			inCharset = inEnc == null ? StandardCharsets.UTF_8 : Charset.forName(inEnc);
			in.leave();
		} else {
			inCharset = StandardCharsets.UTF_8;
			inMethod = Method.XML_FRAGMENT;
		}
		if( in.enters("include") ) {
			String inherit = in.requireAttribute("href");
//			System.err.println("inheriting " + inherit);
			readTXtr( XML.parse( new FileInputStream(dir+"/"+inherit) ), dir );
//			System.err.println ("inherit done: " + inherit);
			in.leave();
		}
		try {
			transformer = transformerFactory.newTransformer();
		} catch (TransformerConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String encoding = in.getAttribute("encoding","utf-8");
		outCharset = Charset.forName(encoding);
		transformer.setOutputProperty(OutputKeys.ENCODING,encoding);
		outMethod = processMethodAttribute(in);
		switch(outMethod) {
			case XML_FRAGMENT:
			case XML:
				transformer.setOutputProperty(OutputKeys.METHOD,"xml");
				processIndentAttributes(in);
				break;
			case TEXT:
				decode = true;
				break;
			case HTML:
				transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,"html");
			case HTML_FRAGMENT:
				transformer.setOutputProperty(OutputKeys.METHOD,"html");
				processIndentAttributes(in);
				break;
		}

		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,in.getAttribute("omit-xml-declaration","yes"));
		processTable(in);
		parser = readSequence(in,NOP).on(1,0,2);
		in.leave();
	}
	public TXtruct(String txtr) throws IOException, SAXException {
		readTXtr( XML.parse(txtr), "" );
	}
	public TXtruct(FileInputStream txtr, String dir) throws IOException, SAXException {
		readTXtr( XML.parse(txtr), dir );
	}
	public TXtruct(Node node, String dir) throws IOException, SAXException {
		readTXtr(node,dir);
	}
	private static void error(String msg) {
		// System.err.println(msg);
		System.exit(-1);
	}
	private DocumentFragment transform(Node input) {
		DocumentFragment output = XML.create();
		XML.Walker in = new XML.Walker(input);
		XML.Walker out = new XML.Walker(output);
		if( decode ) {
			out.insertProcessingInstruction(StreamResult.PI_DISABLE_OUTPUT_ESCAPING,"&");
		}
		parser.apply(true,in,out);
		in.leave();
		return output;
	}
	public String transformToString(Node input) throws TransformerException {
		DocumentFragment output = transform(input);
		StringWriter writer = new StringWriter();
		transformer.transform(new DOMSource(output), new StreamResult(writer));
		return writer.toString();
	}
	public Document transformToDocument(Node input) {
		DocumentFragment output = transform(input);
		Document doc = output.getOwnerDocument();
		doc.appendChild(output);
		return doc;
	}
	private Node nodeOfString(String text) throws IOException, SAXException {
		switch(inMethod) {
			case XML: return XML.parse(text);
			case XML_FRAGMENT: return XML.ofFragment(text);
			case HTML: return XML.ofHTML(text);
			case HTML_FRAGMENT: return XML.ofHTMLfragment(text);
			default: return XML.ofString(text);
		}
	}
	public DocumentFragment transform(String text) throws IOException, SAXException {
		return transform(nodeOfString(text));
	}
	public String transformToString(String text) throws TransformerException, IOException, SAXException {
		return transformToString(nodeOfString(text));
	}
	public Document transformToDocument(String text) throws IOException, SAXException {
		return transformToDocument(nodeOfString(text));
	}
	private Node nodeOfStream(InputStream inFile) throws IOException, SAXException {
		switch(inMethod) {
			case XML: return XML.parse(inFile);
			case XML_FRAGMENT: return XML.ofFragment(inFile,inCharset);
			case HTML: return XML.ofHTML(inFile);
			case HTML_FRAGMENT: return XML.ofHTMLfragment(inFile,inCharset);
			default: return XML.ofString(inFile,inCharset);
		}
	}
	public DocumentFragment transform(InputStream inFile) throws IOException, SAXException {
		return transform(nodeOfStream(inFile));
	}
	public String transformToString(InputStream inFile) throws IOException, SAXException, TransformerException {
		return transformToString(nodeOfStream(inFile));
	}
	public Document transformToDocument(InputStream inFile) throws IOException, SAXException {
		return transformToDocument(nodeOfStream(inFile));
	}
	public static void main(String... args) throws Exception {
		FileInputStream parserFile = null;
		InputStream inFile = System.in;
		String outFileName = null;
		boolean sexp = false;

		for( int i = 0; i < args.length; i++ ) {
			switch( args[i] ) {
			case "-v":
				i++;
				Level level;
				switch( args[i] ) {
				case "finer": level = Level.FINER; break;
				case "finest": level = Level.FINEST; break;
				case "info": default: level = Level.INFO; break;
				}
				logger.setLevel(level);
				handler.setLevel(level);
				break;
			default:
				if( parserFile == null ) {
					parserFile = new FileInputStream(args[i]);
					sexp = args[i].matches(".*\\.txtrs");
				} else if( inFile == System.in ) {
					inFile = new FileInputStream(args[i]);
				} else if( outFileName == null ) {
					outFileName = args[i];
				} else {
					error("too many argument: " + args[i]);
				}
				break;
			}
		}
		if( parserFile == null ) {
			error("parser file not specified");
		}
		TXtruct txtr;
		if( sexp ) {
			TXtruct s2x = new TXtruct(new FileInputStream("sexp2xml.txtr"),"");
			txtr = new TXtruct(s2x.transform(parserFile),"");
		} else {
			txtr = new TXtruct(parserFile,"");
		}
		logger.finest(txtr.toNode().toString());
		PrintStream outFile =
		outFileName == null ? System.out : new PrintStream( new FileOutputStream(outFileName), true, txtr.outCharset );
		outFile.print(txtr.transformToString(inFile));
	}
}
