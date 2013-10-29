package org.bip.engine;

import java.util.ArrayList;

import org.bip.behaviour.Port;

public class HelperFunctions {
	
	public static void addAll(ArrayList<Port> list, Iterable<Port> iterable) {
		for (Port port: iterable){
	        list.add(port);
	    }
	}

}
