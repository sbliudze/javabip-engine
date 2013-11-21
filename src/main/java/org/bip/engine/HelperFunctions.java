package org.bip.engine;

import java.util.ArrayList;
import java.util.Collection;

public class HelperFunctions {
	
	public static void addAll(ArrayList<Object> list, Iterable<Object> iterable) {
		for (Object port: iterable){
	        list.add(port);
	    }
	}

	public static <E> Collection<E> addAll(Collection<E> list, Iterable<E> iter) {
	    for (E item : iter) {
	        list.add(item);
	    }
	    return list;
	}
}
