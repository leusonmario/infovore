package com.ontology2.hydroxide;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.api.client.util.Strings;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ontology2.millipede.shell.CommandLineApplication;
import com.ontology2.millipede.sink.Sink;

public class ExpandFreebaseRdfToNTriples implements Sink<String> {



	final Sink<PrimitiveTriple> acceptSink;
	final Sink<String> rejectSink;
	final Map<String,String> prefixMap = Maps.newHashMap();
	final static Splitter lineSplitter = Splitter.on("\t").limit(3);
	final static Splitter iriSplitter = Splitter.on(":").limit(2);
	
	private static Log logger = LogFactory.getLog(ExpandFreebaseRdfToNTriples.class);
	
	public ExpandFreebaseRdfToNTriples(Sink<PrimitiveTriple> acceptSink,Sink<String> rejectSink) {
		this.acceptSink = acceptSink;
		this.rejectSink = rejectSink;
	}

	@Override
	public void accept(String obj) throws Exception {
		if (obj.isEmpty())
			return;
		
		if(obj.startsWith("@prefix")) {
			try {
				List<String> parts=splitPrefixDeclaration(obj);
				prefixMap.put(parts.get(1),parts.get(2));
			} catch(InvalidPrefixException ex) {
				logger.warn("Invalid prefix declaration: "+obj);
				rejectSink.accept(obj);
				return;
			}
		} else {
			try {
				List<String> parts = expandTripleParts(obj);
				acceptSink.accept(new PrimitiveTriple(parts.get(0),parts.get(1),parts.get(2)));
			} catch(InvalidNodeException ex) {
				logger.warn("Invalid triple: "+obj);
				rejectSink.accept(obj);
				return;				
			}
		}
		
	}

	List<String> expandTripleParts(String obj) throws InvalidNodeException {
		List<String> parts=splitTriple(obj);
		
		parts.set(0,expandIRINode(parts.get(0)));
		parts.set(1,expandIRINode(parts.get(1)));
		parts.set(2,expandAnyNode(parts.get(2)));
		return parts;
	}

	static List<String> splitTriple(String obj) throws InvalidNodeException {
		if (!obj.endsWith(".")) {
			throw new InvalidNodeException();
		}
		
		obj=obj.substring(0,obj.length()-1);
		List<String> parts=Lists.newArrayList(lineSplitter.split(obj));
		if (parts.size()!=3) {
			throw new InvalidNodeException();
		}
		return parts;
	}
	
	public String expandIRINode(String string) throws InvalidNodeException {
		List<String> parts=Lists.newArrayList(iriSplitter.split(string));
		if (prefixMap.containsKey(parts.get(0))) {
			return "<"+prefixMap.get(parts.get(0))+parts.get(1)+">";
		}
		
		throw new InvalidNodeException();
	}
	
	public String expandAnyNode(String string) {
		List<String> parts=Lists.newArrayList(iriSplitter.split(string));
		if (prefixMap.containsKey(parts.get(0))) {
			return "<"+prefixMap.get(parts.get(0))+parts.get(1)+">";
		}
		
		return string;
	}
	

	static List<String> splitPrefixDeclaration(String obj) throws InvalidPrefixException {
		List<String> parts=Lists.newArrayList(Splitter.on(" ").split(obj));
		if (parts.size()!=3) {
			throw new InvalidPrefixException();
		}
		
		String prefix=parts.get(1);
		String mapsTo=parts.get(2);	
		
		if (!prefix.endsWith(":")) {
			throw new InvalidPrefixException();
		}
		
		parts.set(1, prefix.substring(0, prefix.length()-1));

		if (!mapsTo.startsWith("<") || !mapsTo.endsWith(">.")) {
			throw new InvalidPrefixException();
		}
		
		parts.set(2, mapsTo.substring(1, mapsTo.length()-2));
		
		return parts;
	} 
	
	public static class InvalidPrefixException extends Exception {
	}
	
	public static class InvalidNodeException extends Exception {
	}
	
	@Override
	public void close() throws Exception {
		acceptSink.close();
		rejectSink.close();
	}

}