package org.bip.engine.dynamicity;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class Node {
	private String type;
	private Map<Color, Edge> edges;
	private boolean isSatisfied;

	Node(String type) {
		this.type = type;
		this.edges = new HashMap<Color, Edge>();
		this.isSatisfied = false;
	}

	Edge getOrCreate(String type, Color requirementColor, Color solutionColor, String dest) {
		// See if the edge already exists from this to type
		Edge edge = edges.get(solutionColor);
		if (edge == null) {
			// Otherwise create a new one and store it
			edge = new Edge(requirementColor, solutionColor, this.type, dest);
			edges.put(solutionColor, edge);
		}
		return edge;
	}
	
	void resetCounters() {
		for (Edge e : edges.values()) {
			e.resetCounter();
		}
	}
	
	Collection<Edge> getEdges() {
		return edges.values();
	}

	boolean isSatisfied() {
		return isSatisfied;
	}
	
	void satisfied() {
		isSatisfied = true;
	}
	
	void notSatisfied() {
		isSatisfied = false;
	}

	int nbTimesRequired() {
		int n = 0;
		for (Edge e : edges.values()) {
			n += e.getLabel();
		}
		return n;
	}
	 
	String getType() {
		return type;
	}

	void reset() {
		this.notSatisfied();
		for (Edge e : edges.values()) {
			e.resetCounter();
		}
	}

	Set<String> getNeighboursTypes() {
		Set<String> res = new HashSet<String>();
		for (Edge e : edges.values()) {
			res.add(e.getDestination());
		}
		return res;
	}
	
	@Override
	public String toString() {
		return type;
	}
}

class Edge {
	private Color requirementColor;
	private Color solutionColor;
	private int label;
	private int counter;
	private String src;
	private String dest;
	

	Edge(Color requirementColor, Color solutionColor, String src, String dest) {
		this.label = 0;
		this.counter = 0;
		this.src = src;
		this.dest = dest;
		this.requirementColor = requirementColor;
		this.solutionColor = solutionColor;
	}
	
	void resetCounter() {
		counter = label;
	}

	Color getRequirementColor() {
		return requirementColor;
	}
	
	Color getSolutionColor() {
		return solutionColor;
	}

	void incrementLabel() {
		label++;
		counter++;
	}

	void incrementCounter() {
		counter++;
	}
	void decrementCounter() {
		counter--;
	}

	boolean isSatisfied() {
		return counter <= 0;
	}
	
	int getLabel() {
		return label;
	}
	
	String getSource() {
		return src;
	}
	
	String getDestination() {
		return dest;
	}
	
	@Override
	public String toString() {
		return String.format("%s --label: %d--counter: %d--> %s", src, label, counter, dest);
	}
}