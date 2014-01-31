package org.bip.engine;

import java.util.ArrayList;
import java.util.Collection;

// TODO: Auto-generated Javadoc
/**
 * The Class HelperFunctions.
 */
public class HelperFunctions {
	
	/**
	 * Adds the all.
	 *
	 * @param list the list
	 * @param iterable the iterable
	 */
	public static void addAll(ArrayList<Object> list, Iterable<Object> iterable) {
		for (Object port: iterable){
	        list.add(port);
	    }
	}

	/**
	 * Adds the all.
	 *
	 * @param <E> the element type
	 * @param list the list
	 * @param iter the iter
	 * @return the collection
	 */
	public static <E> Collection<E> addAll(Collection<E> list, Iterable<E> iter) {
	    for (E item : iter) {
	        list.add(item);
	    }
	    return list;
	}
}
