import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.w3c.dom.DocumentFragment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.*;

import cps.txtr.*;

public class TXtrTest {
	static String lines(String... str) {
		return String.join(eol,str);
	}
	static TXtruct TXtructOfLines(String... str) throws IOException, SAXException {
		return new TXtruct(lines(str));
	}
	static final String eol = System.lineSeparator();
	static final FileSystem fs = FileSystems.getDefault();
	void assertEqualFiles( String expect, String actual ) throws Exception {
		assertEquals( Files.readAllLines(fs.getPath(expect)), Files.readAllLines(fs.getPath(actual)));
	}
	void translate( String name, String ext, String opt ) throws Exception {
		String txtr = ext + "2" + opt + ".txtr";
		String in = name + "." + ext;
		String outName = in + "." + opt;
		String out = "outputs/" + outName;
		TXtruct.main( txtr, in, out );
		assertEqualFiles( "expectations/" + outName, out );
	}
	@Test public void Read1() throws Exception {
		XML.Walker in = new XML.Walker(XML.parse("<read>me</read>"));
		in.enter("read");
		assertEquals( "me", in.readText() );
		in.leave();
	}
	@Test public void Write1() throws Exception {
		DocumentFragment output = XML.create();
		XML.Walker out = new XML.Walker(output);
		out.writeTextElement("write", "you");
		System.out.println(output);
		assertEquals("<write>you</write>"+eol, XML.toString(output));
	}
	@Test public void TestWrite1() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>hello</txtruct>"
		);
		String output = txtr.transformToString("");
		System.out.println(output);
		assertEquals("hello", output);
	}
	@Test public void TestWrite2() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct><text>hello</text></txtruct>"
		);
		String output = txtr.transformToString("");
		System.out.println(output);
		assertEquals("hello", output);
	}
	@Test public void Encode() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct method=\"text\">",
			" <input method=\"text\"/>",
			" <read-text encode=\"html\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("hello&world");
		System.out.println(output);
		assertEquals(lines("hello&amp;world"), output);
	}
	@Test public void Decode() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct method=\"text\">",
			" <input method=\"text\"/>",
			" <read-text decode=\"html\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("hello&world&amp;");
		System.out.println(output);
		assertEquals(lines("hello&world&"), output);
	}
	@Test public void Decode2() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct method=\"text\">",
			" <input method=\"text\"/>",
			" <match pattern=\".*\" as=\"it\"/>",
			" <value-of select=\"it/match\" decode=\"html\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("hello&world&amp;");
		System.out.println(output);
		assertEquals(lines("hello&world&"), output);
	}
	@Test public void TestWriteMore() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct><text>hello</text> world</txtruct>"
		);
		String output = txtr.transformToString("");
		System.out.println(output);
		assertEquals("hello world", output);
	}
	@Test public void WriteElement() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct><element name=\"hello\">world</element></txtruct>"
		);
		String output = txtr.transformToString("");
		System.out.println(output);
		assertEquals("<hello>world</hello>"+eol,output);
	}
	@Test public void WriteElement2() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct><element><name>hello</name>world</element></txtruct>"
		);
		String output = txtr.transformToString("");
		System.out.println(output);
		assertEquals("<hello>world</hello>"+eol,output);
	}
	@Test public void Indent() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct indent=\"no\"><element name=\"hello\"/><element name=\"world\"/></txtruct>"
		);
		String output = txtr.transformToString("");
		System.out.println(output);
		assertEquals("<hello/><world/>",output);
	}
	@Test public void match1() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <match as=\"m\">__(.+)__</match>",
			" <value-of select=\"m/group[1]\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("__hello__");
		System.out.println(output);
		assertEquals("hello", output);
	}
	@Test public void match2() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <match as=\"m\">__(.+)__</match>**<value-of select=\"m/group[1]\"/>**</txtruct>");
		String output = txtr.transformToString("__hello__");
		System.out.println(output);
		assertEquals("**hello**", output);
	}
	@Test public void matchEmpty() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <match as=\"m\" pattern=\".*\"/>(<value-of select=\"m/group[1]\"/>)</txtruct>");
		String output = txtr.transformToString("");
		System.out.println(output);
		assertEquals("()", output);
	}
	@Test public void eval() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml\"/>",
			" <text>hello </text>",
			" <value-of select=\"foo\" in=\"input\"/>",
			" <read-element name=\"foo\" as=\"dummy\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("<foo>world</foo>");
		System.out.println(output);
		assertEquals("hello world", output);
	}
	@Test public void evalAttribute1() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml\"/>",
			" <text>hello </text>",
			" <value-of select=\"foo/@val\" in=\"input\"/>",
			" <read-element name=\"foo\" as=\"dummy\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("<foo val=\"world\"/>");
		System.out.println(output);
		assertEquals("hello world", output);
	}
	@Test public void evalAttribute2() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml\"/>",
			" <text>hello </text>",
			" <in-element name=\"foo\">",
			"  <value-of select=\"@val\" in=\"input\"/>",
			" </in-element>",
			"</txtruct>"
		);
		String output = txtr.transformToString("<foo val=\"world\"/>");
		System.out.println(output);
		assertEquals("hello world", output);
	}
	@Test public void evalAttribute3() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml\"/>",
			" <text>hello </text>",
			" <in-element name=\"foo\">",
			"  <in-element name=\"bar\">",
			"   <value-of select=\"../@val\" in=\"input\"/>",
			"  </in-element>",
			" </in-element>",
			"</txtruct>"
		);
		String output = txtr.transformToString("<foo val=\"world\"><bar/></foo>");
		System.out.println(output);
		assertEquals("hello world", output);
	}
	@Test public void evalOutput() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <element name=\"foo\">",
			"  <attribute name=\"val\">hello</attribute>",
			"  <element name=\"bar\">",
			"   <value-of select=\"../@val\" in=\"output\"/>",
			"   <text> world</text>",
			"  </element>",
			" </element>",
			"</txtruct>"
		);
		String output = txtr.transformToString("");
		System.out.println(output);
		assertEquals(lines(
			"<foo val=\"hello\">",
			"  <bar>hello world</bar>",
			"</foo>",
			""
		), output);
	}
	@Test public void matchGroups() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <match as=\"m\">__(.+)__( .+)</match>**<value-of select=\"m/group[1]\"/>**<value-of select=\"m/group[2]\"/>",
			"</txtruct>");
		String output = txtr.transformToString("__hello__ world");
		System.out.println(output);
		assertEquals("**hello** world", output);
	}
	@Test public void matchInElement() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <match as=\"m\">__(.+)__</match>",
			" <element name=\"head\">",
			"  <value-of select=\"m/group[1]\"/>",
			" </element>",
			"</txtruct>");
		String output = txtr.transformToString("__hello__");
		System.out.println(output);
		assertEquals("<head>hello</head>"+eol, output);
	}
	@Test public void matchMore() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <match as=\"m\">__(.+)__ </match>",
			" <element name=\"title\">",
			"  <value-of select=\"m/group[1]\"/>",
			" </element>",
			" <match as=\"m2\">.+</match>",
			" <value-of select=\"m2/match\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("__hello__ world");
		System.out.println(output);
		assertEquals("<title>hello</title>world", output);
	}
	@Test public void matchNamed() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <match as=\"pre\">(.+)&gt; </match>",
			" <match as=\"bdy\">.+</match>",
			" <value-of select=\"bdy/match\"/> {<value-of select=\"pre/group[1]\"/>}</txtruct>"
		);
		String output = txtr.transformToString("world> hello");
		System.out.println(output);
		assertEquals("hello {world}", output);
	}
	@Test public void choice() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <choice>",
			"  <group>",
			"   <match as=\"idx\">__(.+)__</match>",
			"   <text>*</text>",
			"   <value-of select=\"idx/group[1]\"/>",
			"   <text>*</text>",
			"  </group>",
			"  <group>",
			"   <match as=\"idx\">_(.+)_</match>",
			"   <text>/</text>",
			"   <value-of select=\"idx/group[1]\"/>",
			"   <text>/</text>",
			"  </group>",
			" </choice>",
			"</txtruct>"
		);
		String output = txtr.transformToString("__hello__");
		System.out.println(output);
		assertEquals("*hello*", output);
		output = txtr.transformToString("_hello_");
		System.out.println(output);
		assertEquals("/hello/", output);
	}
	@Test public void class1() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <class name=\"myClass\">",
			"  <option>",
			"   <match as=\"m\">__(.+)__( .*)</match>",
			"   <element name=\"title\">",
			"    <value-of select=\"m/group[1]\"/>",
			"   </element>",
			"   <element name=\"body\">",
			"    <value-of select=\"m/group[2]\"/>",
			"   </element>",
			"  </option>",
			" </class>",
			" <call ref=\"myClass\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("__hello__ world");
		System.out.println(output);
		assertEquals(lines(
			"<title>hello</title><body> world</body>",
			""), output);
	}
	@Test public void classMany() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <class name=\"myClass\">",
			"  <option>",
			"   <match as=\"m\">([^ ]+) ?</match>",
			"   <value-of select=\"m/group[1]\"/>",
			"   <text>!</text>",
			"  </option>",
			" </class>",
			" <call ref=\"myClass\" maxOccurs=\"unbounded\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("hello world");
		System.out.println(output);
		assertEquals("hello!world!", output);
	}
	@Test public void classArg() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <class name=\"myClass\">",
			"  <option>",
			"   <match as=\"m\">([^ ]+) ?</match>",
			"   <value-of select=\"m/group[1]\"/>",
			"   <value-of select=\"punct\"/>",
			"  </option>",
			" </class>",
			" <call ref=\"myClass\" maxOccurs=\"unbounded\">",
			"  <element name=\"punct\">!</element>",
			" </call>",
			"</txtruct>"
		);
		String output = txtr.transformToString("hello world");
		System.out.println(output);
		assertEquals("hello!world!", output);
	}
	@Test public void TestClass0() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <class name=\"myClass\">",
			"  <option>",
			"   <match>bar</match>",
			"   <text>BUG!</text>",
			"  </option>",
			" </class>",
			" <call ref=\"myClass\" minOccurs=\"0\"/>",
			" <match as=\"m\">.*</match>",
			" <value-of select=\"m/match\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("hello world");
		System.out.println(output);
		assertEquals("hello world", output);
	}
	@Test public void classRead1() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <class name=\"myClass\">",
			"  <option>",
			"   <match as=\"m\">__(.+)__( .*)</match>",
			"   <element name=\"title\">",
			"    <value-of select=\"m/group[1]\"/>",
			"   </element>",
			"   <element name=\"body\">",
			"    <value-of select=\"m/group[2]\"/>",
			"   </element>",
			"  </option>",
			" </class>",
			" <call ref=\"myClass\" as=\"it\"/>",
			" <value-of select=\"it\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("__hello__ world");
		System.out.println(output);
		assertEquals(lines(
			"<title>hello</title><body> world</body>",
			""), output);
	}
	@Test public void classReadMany() throws Exception{
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <class name=\"my-class\">",
			"  <match as=\"w\">([^ ]+) *</match>",
			"  <element name=\"w\">",
			"   <value-of select=\"w/group[1]\"/>",
			"  </element>",
			" </class>",
			" <call ref=\"my-class\" maxOccurs=\"unbounded\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("hello world");
		System.out.println(output);
		assertEquals(lines(
			"<w>hello</w><w>world</w>",
			""), output);
	}
	@Test public void classReadManyAs() throws Exception{
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <class name=\"my-class\">",
			"  <match as=\"w\">([^ ]+) *</match>",
			"  <element name=\"w\">",
			"   <value-of select=\"w/group[1]\"/>",
			"  </element>",
			" </class>",
			" <call ref=\"my-class\" maxOccurs=\"unbounded\" as=\"it\"/>",
			" <value-of select=\"it\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("hello world");
		System.out.println(output);
		assertEquals(lines(
			"<w>hello</w><w>world</w>",
			""), output);
	}
	@Test public void classReadManyAsArg() throws Exception{
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <class name=\"my-class\">",
			"  <match as=\"w\">([^ ]+) *</match>",
			"  <element name=\"w\">",
			"   <value-of select=\"w/group[1]\"/>",
			"  </element>",
			" </class>",
			" <call ref=\"my-class\" maxOccurs=\"unbounded\" as=\"it\">",
			"  <element name=\"arg\">foo</element>",
			" </call>",
			" <value-of select=\"it\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("hello world");
		System.out.println(output);
		assertEquals(lines(
			"<w>hello</w><w>world</w>",
			""), output);
	}
	@Test public void classRec1() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <class name=\"lines\">",
			"  <option>",
			"   <match as=\"m\" pattern=\"(.*)\\R\"/>",
			"   <text>| </text><value-of select=\"m/group[1]\"/>",
			"   <call ref=\"lines0\"/>",
			"  </option>",
			"  <option>",
			"   <match as=\"m\">.+</match>",
			"   <text>| </text><value-of select=\"m/match\"/>",
			"  </option>",
			" </class>",
			" <class name=\"lines0\">",
			"  <option>",
			"   <call ref=\"lines\"/>",
			"  </option>",
			"  <option/>",
			" </class>",
			" <call ref=\"lines\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"hello",
			"world"));
		System.out.println(output);
		assertEquals("| hello| world", output);
	}
	@Test public void classRec2() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <class name=\"block\">",
			"  <option>",
			"   <match as=\"m\">__(.+)__( .*)$</match>",
			"   <element name=\"title\">",
			"    <value-of select=\"m/group[1]\"/>",
			"   </element>",
			"   <element name=\"body\">",
			"    <value-of select=\"m/group[2]\"/>",
			"   </element>",
			"  </option>",
			"  <option>",
			"   <match as=\"m\">__(.+)__$</match>",
			"   <element name=\"title\">",
			"    <value-of select=\"m/group[1]\"/>",
			"   </element>",
			"   <element name=\"body\">",
			"    <call ref=\"lines\"/>",
			"   </element>",
			"  </option>",
			" </class>",
			" <class name=\"lines\">",
			"  <option level=\"0\"/>",
			"  <option>",
			"   <match as=\"m\">[^_].*$</match>",
			"   <value-of select=\"m\"/>",
			"   <call ref=\"lines\" level=\"0\"/>",
			"  </option>",
			" </class>",
			" <call ref=\"block\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString("__hello__ world");
		System.out.println(output);
		assertEquals(lines(
			"<title>hello</title><body> world</body>",
			""), output);
	}
	@Test public void attribute1() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <element name=\"elm\">",
			"  <attribute name=\"att\">test value</attribute>",
			" </element>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
		));
		System.out.println(output);
		assertEquals(lines(
			"<elm att=\"test value\"/>",
			""
		), output);
	}
	@Test public void attributeVar() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <element name=\"elm\">",
			"  <attribute><name>att</name>test value</attribute>",
			" </element>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
		));
		System.out.println(output);
		assertEquals(lines(
			"<elm att=\"test value\"/>",
			""
		), output);
	}
	@Test @Tag("NLP")
	public void natural1() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <StanfordNLP/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"Please, do work!"
		));
		System.out.println(output);
		assertEquals(lines(
			"<p pos=\"ROOT\">",
			"  <p pos=\"S\">",
			"    <p pos=\"INTJ\">",
			"      <w lemma=\"please\" pos=\"UH\">Please</w>",
			"    </p>",
			"    <w lemma=\",\" pos=\",\">,</w> <p pos=\"VP\">",
			"      <w lemma=\"do\" pos=\"VBP\">do</w> <p pos=\"VP\">",
			"        <w lemma=\"work\" pos=\"VB\">work</w>",
			"      </p>",
			"    </p>",
			"    <w lemma=\"!\" pos=\".\">!</w>",
			"  </p>",
			"</p>",
			""
		), output);
	}
	@Test @Tag("NLP")
	public void natural_newline() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <StanfordNLP/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"Hello","","world"
		));
		System.out.println(output);
		String expect = lines(
			"<p pos=\"ROOT\">",
			"  <p pos=\"FRAG\">",
			"    <p pos=\"INTJ\">",
			"      <w lemma=\"hello\" pos=\"UH\">Hello</w>",
			"    </p>",
			"",// TODO: why?
			"<p pos=\"NP\">",
			"      <w lemma=\"world\" pos=\"NN\">world</w>",
			"    </p>",
			"  </p>",
			"</p>",
			"");
		System.out.println(expect);
		assertEquals(expect,output);
	}
	@Test @Tag("NLP")
	public void natural2() throws Exception {
		TXtruct txtr = new TXtruct( lines(
			"<txtruct>",
			" <match as=\"m\">__(.+)__</match>",
			" <cascade>",
			"  <value-of select=\"m/group[1]\"/>",
			"  <StanfordNLP/>",
			" </cascade>",
			"</txtruct>"
		));
		String output = txtr.transformToString("__A Test Title__");
		System.out.println(output);
		assertEquals(lines(
			"<p pos=\"ROOT\">",
			"  <p pos=\"NP\">",
			"    <w lemma=\"a\" pos=\"DT\">A</w> <w lemma=\"Test\" pos=\"NNP\">Test</w> <w lemma=\"Title\" pos=\"NNP\">Title</w>",
			"  </p>",
			"</p>",
			""
		), output);
	}
	@Test public void inElement1() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml\"/>",
			" <in-element name=\"elm\">",
			"  <text>hello world</text>",
			" </in-element>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"<elm/>"
		));
		System.out.println(output);
		assertEquals(lines(
			"hello world"
		), output);
	}
	@Test public void inElement2() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml\"/>",
			" <in-element name=\"elm\">",
			"  <text>hello </text>",
			"  <match as=\"m\">.+</match>",
			"  <value-of select=\"m/match\"/>",
			" </in-element>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"<elm>world</elm>"
		));
		System.out.println(output);
		assertEquals(lines(
			"hello world"
		), output);
	}
	@Test public void readAttribute1() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml\"/>",
			" <in-element name=\"elm\">",
			"  <read-attribute name=\"value\"/>",
			"  <match as=\"m\">.+</match>",
			"  <value-of select=\"value\"/>",
			"  <value-of select=\"m/match\"/>",
			" </in-element>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"<elm value=\"hello\"> world</elm>"
		));
		System.out.println(output);
		assertEquals(lines(
			"hello world"
		), output);
	}
	@Test public void readAttribute2() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml\"/>",
			" <in-element name=\"elm\">",
			"  <read-attribute name=\"novalue\" minOccurs=\"0\"/>",
			"  <if test=\"novalue\">BUG!</if>",
			"  <match as=\"m\">.+</match>",
			"  <value-of select=\"m/match\"/>",
			" </in-element>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"<elm value=\"bye\">hello world</elm>"
		));
		System.out.println(output);
		assertEquals(lines(
			"hello world"
		), output);
	}
	@Test public void readAttributeAs() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml\"/>",
			" <in-element name=\"elm\">",
			"  <read-attribute name=\"value\" as=\"my-elm\"/>",
			"  <match as=\"my-value\">.+</match>",
			"  <value-of select=\"my-elm\"/>",
			"  <value-of select=\"my-value/match\"/>",
			" </in-element>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"<elm value=\"hello\"> world</elm>"
		));
		System.out.println(output);
		assertEquals(lines(
			"hello world"
		), output);
	}
	@Test public void readAttributePattern() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml\"/>",
			" <in-element name=\"elm\">",
			"  <read-attribute name=\"value\" pattern=\"h.*\" as=\"h\"/>",
			"  <match as=\"w\">.+</match>",
			"  <value-of select=\"h/match\"/>",
			"  <value-of select=\"w/match\"/>",
			" </in-element>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"<elm value=\"hello\"> world</elm>"
		));
		System.out.println(output);
		assertEquals(lines(
			"hello world"
		), output);
	}
	@Test public void readComment() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml-fragment\"/>",
			" <read-comment as=\"value\"/>",
			" <text>hello </text>",
			" <value-of select=\"value\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"<!--world-->"
		));
		System.out.println(output);
		assertEquals("hello world", output);
	}
	@Test public void readComment2() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml-fragment\"/>",
			" <in-comment>",
			"  <match as=\"value\"> *(world) *</match>",
			" </in-comment>",
			" <text>hello </text>",
			" <value-of select=\"value/group[1]\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"<!-- world -->"
		));
		System.out.println(output);
		assertEquals("hello world", output);
	}
	@Test public void readComment3() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml-fragment\"/>",
			" <class name=\"cls\">",
			"  <option>",
			"   <read-comment/>",
			"  </option>",
			"  <option>",
			"   <match pattern=\"\\s+\" as=\"sp\"/>",
			"   <value-of select=\"sp/match\"/>",
			"  </option>",
			"  <option>",
			"   <read-element name=\"a\"/>",
			"  </option>",
			" </class>",
			" <call ref=\"cls\" maxOccurs=\"unbounded\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"<a/>  <a/>"
		));
		System.out.println(output);
		assertEquals("  ", output);
	}
	@Test public void readComment4() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml-fragment\"/>",
			" <read-comment as=\"value\" skipSpaces=\"yes\"/>",
			" <text>hello </text>",
			" <value-of select=\"value\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"   ",
			"  <!--world-->"
		));
		System.out.println(output);
		assertEquals("hello world", output);
	}
	@Test public void repeat() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <class name=\"word\">",
			"  <match pattern=\"[^ ]+\" as=\"x\"/>",
			"  <value-of select=\"x/match\"/>",
			"  <match pattern=\" \"/>",
			" </class>",
			" <call ref=\"word\" maxOccurs=\"unbounded\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"hel lo "
		));
		System.out.println(output);
		assertEquals("hello", output);
	}
	@Test public void repeat_mixed() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <class name=\"word\">",
			"  <option><read-element name=\"hello\"/>hello </option>",
			"  <option><read-text/></option>",
			" </class>",
			" <call ref=\"word\" maxOccurs=\"unbounded\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"<hello/>world"
		));
		System.out.println(output);
		assertEquals("hello world", output);
	}
	@Test public void minOccurs0() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <group minOccurs=\"0\">",
			"  <match as=\"dummy\">_</match>",
			" </group>",
			" <match as=\"x\">h.*</match>",
			" <value-of select=\"x/match\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"hello"
		));
		System.out.println(output);
		assertEquals("hello", output);
	}
	@Test public void minOccurs1() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <group minOccurs=\"0\">",
			"  <match as=\"dummy\">_</match>",
			" </group>",
			" <match as=\"x\">h.*</match>",
			" <value-of select=\"x/match\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"_hello"
		));
		System.out.println(output);
		assertEquals("hello", output);
	}
	@Test public void comment() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <match as=\"it\">.+</match>",
			" <comment><value-of select=\"it/match\"/></comment>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"hello"
		));
		System.out.println(output);
		assertEquals(lines(
			"<!--hello-->",
			""
		), output);
	}
	@Test public void comment2() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <text>hello</text>",
			" <comment>world</comment>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
		));
		System.out.println(output);
		assertEquals(lines(
			"hello<!--world-->"
		), output);
	}
	@Test public void comment3() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <element name=\"hello\"/>",
			" <comment>world</comment>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
		));
		System.out.println(output);
		assertEquals(lines(
			"<hello/><!--world-->",
			""
		), output);
	}
	@Test public void comment4() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <element name=\"a\">",
			"  <text>hello</text>",
			" </element>",
			" <comment>world</comment>",
			" <text>!</text>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
		));
		System.out.println(output);
		assertEquals(lines(
			"<a>hello</a><!--world-->!"
		), output);
	}
	@Test public void arg1() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <class name=\"sharps\">",
			"  <if test=\"depth > 0\">#<call ref=\"sharps\">",
			"    <element name=\"depth\"><value-of select=\"depth - 1\"/></element>",
			"   </call>",
			"  </if>",
			" </class>",
			" <call ref=\"sharps\">",
			"  <element name=\"depth\">5</element>",
			" </call>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
		));
		System.out.println(output);
		assertEquals("#####", output);
	}
	@Test public void evaluate() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			" <input method=\"xml-fragment\"/>",
			" <copy-of in=\"input\">*[@id='me']</copy-of>",
			" <in-element name=\"hello\">",
			" </in-element>",
			" <in-element name=\"world\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"<hello/>",
			"<world id=\"me\"/>"
		));
		System.out.println(output);
		assertEquals("<world id=\"me\"/>"+eol, output);
	}
	@Test public void valueOfOut() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			"  <element name=\"me\">world</element>",
			"  <text>hello </text>",
			"  <value-of select=\"me\" in=\"output\"/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
		));
		System.out.println(output);
		assertEquals("<me>world</me>hello world", output);
	}
	@Test public void toDocument() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			"  <element name=\"hello\">world</element>",
			"</txtruct>"
		);
		String output = XML.toString(txtr.transformToDocument(lines(
		)));
		System.out.println(output);
		assertEquals("<hello>world</hello>"+eol, output);
	}
	@Test public void html() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct method=\"html\" indent=\"no\">",
			"  <element name=\"html\">hello<element name=\"br\"/>world</element>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
		));
		System.out.println(output);
		assertEquals(lines(
				"<!DOCTYPE html SYSTEM \"html\">",
				"<html>hello<br>world</html>"
			), output
		);
	}
	@Test public void readHtmlFragment() throws Exception {
		TXtruct txtr = TXtructOfLines(
			"<txtruct>",
			"  <input method=\"html-fragment\"/>",
			"  <in-element name=\"em\">",
			"   <read-text as=\"it\"/>",
			"   <value-of select=\"it\"/>",
			"   <in-element name=\"br\"/>",
			"  </in-element>",
			"  <read-text/>",
			"</txtruct>"
		);
		String output = txtr.transformToString(lines(
			"<em>hello<br></em> world"
		));
		System.out.println(output);
		assertEquals("hello world", output);
	}
	@Test public void ex1_md2xml() throws Exception {
		translate("ex1", "md", "xml");
	}
	@Test @Tag("NLP") public void easyMD_nlpXML() throws Exception {
		translate("easy", "md", "nlp.xml");
	}
	@Test @Tag("NLP") public void easyMD_nlpMD() throws Exception {
		translate("easy", "md", "nlp.md");
	}
	@Test public void bootstrap() throws Exception {
		String out = "outputs/txtr.html";
		TXtruct.main( "txtr2html.txtr", "txtr2html.txtr", out );
		assertEqualFiles("txtr.html",out);
	}
	@Test public void txtrs() throws Exception {
		String out = "outputs/txtrs.html";
		TXtruct.main( "txtr2html.txtrs", "txtr2html.txtr", out );
		assertEqualFiles("txtrs.html",out);
	}
}