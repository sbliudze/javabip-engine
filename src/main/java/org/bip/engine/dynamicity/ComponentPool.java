package org.bip.engine.dynamicity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bip.api.BIPComponent;
import org.bip.api.BIPGlue;
import org.bip.api.Port;
import org.bip.api.PortBase;
import org.bip.api.Require;
import org.bip.exceptions.BIPEngineException;
import org.bip.executor.ExecutorKernel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author rutz
 *
 */
public class ComponentPool {
	private Logger logger = LoggerFactory.getLogger(ComponentPool.class);
	private BIPGlue glue;
	private Map<String, Node> nodes;
	private Map<String, Set<Edge>> incomingEdges;
	private Set<BIPComponent> pool;
	private Map<Color, Set<Edge>> edgesPerSolution;

	public ComponentPool(BIPGlue glue) {
		this.glue = glue;
		this.nodes = new HashMap<String, Node>();
		this.incomingEdges = new HashMap<String, Set<Edge>>();
		this.pool = new HashSet<BIPComponent>();
		this.edgesPerSolution = new HashMap<Color, Set<Edge>>();
	}

	/**
	 * 
	 * @return
	 */
	public void initialize() {
		Set<String> seen = new HashSet<String>();
		List<Require> requires = glue.getRequiresConstraints();
		// Create all nodes
		for (Require r : requires) {
			String type = r.getEffect().getSpecType();
			if (!nodes.containsKey(type)) {
				nodes.put(type, new Node(type));
			}
		}

		for (String type : nodes.keySet()) {
			logger.debug("{} is a node of the graph", type);
		}

		// Create all edges
		Color requirementColor, solutionColor = null;
		String effectType, causeType;
		Edge edge;
		Node causeNode;
		// Set<String> seen;

		// For each requirement
		for (Require require : requires) {
			// seen = new HashSet<String>();
			// Get the type of the effect
			effectType = require.getEffect().getSpecType();
			// Create a color for this requirement
			requirementColor = ColorFactory.getRequirementColor();

			// If only one conjunction of causes the get the unconditional color
			// for solutions
			if (require.getCauses().size() == 1) {
				solutionColor = ColorFactory.getUnconditionalSolutionColor();
			}

			Set<String> tmpSeen = new HashSet<String>();

			// For every set of conjunctions
			for (List<PortBase> causes : require.getCauses()) {
				// Create a color for this solution
				if (solutionColor == null) {
					solutionColor = ColorFactory.getSolutionColor();
				}

				// For every cause in the disjunction
				for (PortBase cause : causes) {
					causeType = cause.getSpecType();
					// seen.add(causeType);
					causeNode = nodes.get(causeType);

					if (!seen.contains(effectType)) {
						// Create a new edge or get the one that has been
						// created before
						edge = causeNode.getOrCreate(effectType, requirementColor, solutionColor, effectType);
						// Increment label and counter
						edge.incrementLabel();

						// Store the edges for further use mapped from their
						// solution color
						Set<Edge> tmp = edgesPerSolution.get(solutionColor);
						if (tmp == null) {
							edgesPerSolution.put(solutionColor, new HashSet<Edge>(Arrays.asList(edge)));
						} else {
							tmp.add(edge);
						}

						tmp = incomingEdges.get(effectType);
						if (tmp == null) {
							incomingEdges.put(effectType, new HashSet<Edge>(Arrays.asList(edge)));
						} else {
							tmp.add(edge);
						}

						tmpSeen.add(effectType);
					}
				}

				solutionColor = null;
			}
			seen.addAll(tmpSeen);
		}

		for (Node n : nodes.values()) {
			for (Edge e : n.getEdges()) {
				logger.debug("Color: {}, edge: {}", e.getSolutionColor(), e.toString());
			}
		}

	}

	public void cleanup() {
		pool = new HashSet<BIPComponent>();
		for (Node node : nodes.values()) {
			node.resetCounters();
		}
	}

	/**
	 * 
	 * @param instance
	 * @return
	 * @throws BIPEngineException
	 */
	public Set<BIPComponent> addInstance(BIPComponent instance) throws BIPEngineException {
		if (instance == null) {
			logger.error("Trying to add a null component to the pool.");
			throw new BIPEngineException("Trying to add a null component to the pool.");
		}

		List<Port> enforceablePorts = ((ExecutorKernel) instance).getBehavior().getEnforceablePorts();
		
		if (!nodes.containsKey(instance.getType()) && (enforceablePorts == null || enforceablePorts.isEmpty())) {
			return new HashSet<BIPComponent>(Arrays.asList(instance));
		} else if (!nodes.containsKey(instance.getType()) && !(enforceablePorts == null || enforceablePorts.isEmpty())) {
			logger.error("Trying to add a component of type that is not in the graph: {}", instance.getType());
			throw new BIPEngineException(
					"Trying to add a component of type that is not in the graph: " + instance.getType());
		}

		logger.debug("Adding an instance of type {} to the pool", instance.getType());

		// Always update the counters
		boolean validSystem = decrementCounters(instance.getType());

		// if we have a valid system then add this component to the pool
		// return a set of all the instances that were in it and empty the pool
		if (validSystem) {
			pool.add(instance);
			Set<BIPComponent> poolCopy = pool;
			pool = new HashSet<BIPComponent>();
			return poolCopy;

			// else add instance to the pool and return an empty set
		} else {
			pool.add(instance);
			return Collections.emptySet();
		}
	}

	private boolean decrementCounters(String type) throws BIPEngineException {
		Node node = nodes.get(type);

		if (node == null) {
			logger.error("Trying to add an instance whose type hasn't been registered yet: {}", type);
			throw new BIPEngineException("Trying to add an instance whose type hasn't been registered yet: " + type);
		}

		if (incomingEdges.get(type) == null || incomingEdges.get(type).isEmpty()) {
			for (Edge edge : node.getEdges()) {
				edge.decrementCounter();
			}

			return true;
		} else if (node.getEdges().isEmpty()) {
			return checkDependencies(new HashSet<String>(Arrays.asList(type)), new HashSet<String>());
		}

		for (Edge edge : node.getEdges()) {
			// Decrement counters of outgoing edges of this node.
			edge.decrementCounter();

			if (edge.isSatisfied()) {
				Color edgeSolutionColor = edge.getSolutionColor();
				Set<Edge> sameSolutionEdges = edgesPerSolution.get(edgeSolutionColor);

				if (allSatisfied(sameSolutionEdges)) {
					Set<String> nodeDependencies = new HashSet<String>();
					for (Edge e : sameSolutionEdges) {
						nodeDependencies.add(e.getSource());
					}

					return checkDependencies(nodeDependencies, new HashSet<String>());
				} else if (edgeSolutionColor.equals(ColorFactory.getUnconditionalSolutionColor())) {
					return checkDependencies(new HashSet<String>(Arrays.asList(edge.getDestination())),
							new HashSet<String>());
				}
			}
		}

		return false;
	}

	private boolean checkDependencies(Set<String> nodeDependencies, Set<String> seen) {
		if (nodeDependencies == null || nodeDependencies.isEmpty()) {
			return true;
		}

		for (String currentType : nodeDependencies) {
			if (!seen.contains(currentType)) {
				seen.add(currentType);
				Node currentNode = nodes.get(currentType);
				if (currentNode == null) {
					logger.error("Node of type {} does not exist.", currentType);
					throw new BIPEngineException("Node of type " + currentType + " does not exist");
				}

				if (!currentNode.isSatisfied()) {
					Set<Edge> edges = incomingEdges.get(currentType);
					if (edges == null || edges.isEmpty()) {
						currentNode.satisfied();
					} else {
						Map<Color, Set<Edge>> incomingEdgesPerSolution = new HashMap<Color, Set<Edge>>();
						for (Edge incomingEdge : edges) {
							Set<Edge> tmp = incomingEdgesPerSolution.get(incomingEdge.getSolutionColor());
							if (tmp == null) {
								incomingEdgesPerSolution.put(incomingEdge.getSolutionColor(),
										new HashSet<Edge>(Arrays.asList(incomingEdge)));
							} else {
								tmp.add(incomingEdge);
							}
						}

						for (Map.Entry<Color, Set<Edge>> entry : incomingEdgesPerSolution.entrySet()) {
							boolean solutionIsSatisfied = true;
							Set<String> dependenciesOfSolution = new HashSet<String>();
							for (Edge edge : entry.getValue()) {
								solutionIsSatisfied &= edge.isSatisfied();
								dependenciesOfSolution.add(edge.getSource());
							}

							if (solutionIsSatisfied) {
								// TODO get rid of this recursive call.
								if (checkDependencies(dependenciesOfSolution, seen)) {
									currentNode.satisfied();
									break;
								}
							}
						}

						if (!currentNode.isSatisfied()) {
							return false;
						}
					}
				}

				seen.add(currentType);
			}
		}

		return true;
	}

	private boolean allSatisfied(Set<Edge> sameSolutionEdges) {
		if (sameSolutionEdges == null || sameSolutionEdges.isEmpty()) {
			return true;
		}

		for (Edge edge : sameSolutionEdges) {
			if (!edge.isSatisfied()) {
				return false;
			}
		}

		return true;
	}

}
