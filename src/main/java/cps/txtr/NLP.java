package cps.txtr;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.w3c.dom.*;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.*;

public class NLP extends TXtruct.Processor {
	StanfordCoreNLP pipeline;
	public NLP() {
		pipeline = new StanfordCoreNLP(	PropertiesUtils.asProperties(
			"annotators", "tokenize,ssplit,pos,lemma,parse",
//			"parse.model", "edu/stanford/nlp/models/srparser/englishSR.ser.gz",
			"tokenize.options", "invertible,untokenizable=allKeep",
		"tokenize.language", "en"
//		"ssplit.newlineIsSentenceBreak", "always"
		));
	}
	@Override public Node toNode() {
		Element ret = XML.createElement("natural");
		return ret;
	}
	static private class PrePostNode {
		public Node pre, node, post;
		public PrePostNode(Node pre, Node node, Node post) {
			this.pre = pre;
			this.node = node;
			this.post = post;
		}
		public void appendPreNodeTo(Node parent) {
			if( pre != null ) {
				parent.appendChild(pre);
			}
			parent.appendChild(node);
		}
	}
	public Node parse(Document doc, String str) {
		if( str.matches("[ \t\r\n]*") ) {
			return doc.createTextNode(str);
		}
		DocumentFragment ret = doc.createDocumentFragment();
		CoreDocument document = new CoreDocument(str);
		pipeline.annotate(document);
		List<CoreSentence> sens = document.sentences();
		PrePostNode ppn = null;
		for( CoreSentence sen : sens ) {
			CoreDocument sd = new CoreDocument(sen.toString());
			pipeline.annotate(sd);
			ppn = parse(doc,sen);
			ppn.appendPreNodeTo(ret);
		}
		if( ppn != null && ppn.post != null ) {
			ret.appendChild(ppn.post);
		}
		return ret;
	}

	PrePostNode parse(Document doc, CoreSentence sent) {
		return parse(doc, sent.tokens().iterator(), sent.coreMap().get(TreeCoreAnnotations.TreeAnnotation.class));
	}
	PrePostNode parse(Document doc, Iterator<CoreLabel> curr, Tree t) {
		String pos = t.label().toString();
		if( t.children().length == 1 && t.getChild(0).isLeaf() ) {
			Element node = doc.createElement("w");
			CoreLabel l = curr.next();
			String wrd = l.word();
			node.appendChild(doc.createTextNode(wrd));
			node.setAttribute("pos", pos);
			node.setAttribute("lemma",l.lemma());
			String bef = l.before();
			String aft = l.after();
			return new PrePostNode( bef == null ? null : doc.createTextNode(bef), node, aft == null ? null : doc.createTextNode(aft) );
		} else {
			Element node = doc.createElement("p");
			node.setAttribute("pos", pos);
			Tree[] chs = t.children();
			int n = chs.length;
			PrePostNode ppn = parse(doc,curr,chs[0]);
			Node pre = ppn.pre;
			node.appendChild(ppn.node);
			for( int i = 1; i < n; i++ ) {
				ppn = parse(doc,curr,chs[i]);
				ppn.appendPreNodeTo(node);
			}
			return new PrePostNode(pre,node,ppn.post);
		}
	}

	@Override public TXtruct.State apply(boolean forced, XML.Walker... ports) {
		XML.Walker in = ports[0];
		XML.Walker out = ports[1];
		String str = in.readText();
		if( str == null ) {
			System.err.println("Warning: Stanford NLP on null");
			in.fail();
		}
		System.err.println("Stanford NLP on [" + str + "]");
		out.write(parse(out.getOwnerDocument(),str));
		return TXtruct.State.SUCCESS;
	}

	public static void main(String[] args) throws Exception {
		NLP test = new NLP();
		String input = args.length == 1 ? new String(Files.readAllBytes(Paths.get(args[0]))) : "it works!";
		System.out.println(XML.toString(test.parse(XML.document,input)));
	}
}