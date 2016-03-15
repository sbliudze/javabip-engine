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
	private boolean valid;
	private Set<String> added;
	private Map<String, Integer> subsystem;

	public ComponentPool(BIPGlue glue) {
		this.glue = glue;
		this.nodes = new HashMap<String, Node>();
		this.incomingEdges = new HashMap<String, Set<Edge>>();
		this.pool = new HashSet<BIPComponent>();
		this.edgesPerSolution = new HashMap<Color, Set<Edge>>();
		this.valid = false;
		this.added = new HashSet<String>();
		this.subsystem = new HashMap<String, Integer>();
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

		// for (String type : nodes.keySet()) {
		// logger.debug("{} is a node of the graph", type);
		// }

		// Create all edges
		Color requirementColor, solutionColor = null;
		String effectType, causeType;
		Edge edge;
		Node causeNode;

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

		// for (Node n : nodes.values()) {
		// for (Edge e : n.getEdges()) {
		// logger.debug("Color: {}, edge: {}", e.getSolutionColor(),
		// e.toString());
		// }
		// }
	}

	public void cleanup() {
		this.pool = new HashSet<BIPComponent>();
		this.valid = false;
		this.added = new HashSet<String>();
		this.subsystem = new HashMap<String, Integer>();
		for (Node node : nodes.values()) {
			node.reset();
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
		} else if (!nodes.containsKey(instance.getType())
				&& !(enforceablePorts == null || enforceablePorts.isEmpty())) {
			logger.error("Trying to add a component of type that is not in the graph: {}", instance.getType());
			throw new BIPEngineException(
					"Trying to add a component of type that is not in the graph: " + instance.getType());
		}

		if (this.added.contains(instance.getId())) {
			logger.error("Component {} has already been added to the pool. ID = {}", instance, instance.getId());
			throw new BIPEngineException(
					"Component " + instance + " has already been added to the pool. ID = " + instance.getId());
		}

		// logger.debug("Adding an instance of type {} to the pool",
		// instance.getType());

		// Always update the counters
		this.valid |= decrementCounters(instance.getType());
		this.added.add(instance.getId());
		Integer count = this.subsystem.get(instance.getType());
		if (count == null || count.intValue() == 0) {
			this.subsystem.put(instance.getType(), 1);
		} else {
			this.subsystem.put(instance.getType(), count + 1);
		}

		// if we have a valid system then add this component to the pool
		// return a set of all the instances that were in it and empty the pool
		if (valid) {
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

	public boolean removeInstance(BIPComponent instance) {
		if (instance == null) {
			logger.error("Trying to remove a null component from the pool.");
			throw new BIPEngineException("Trying to remove a null component from the pool.");
		}

		if (!nodes.containsKey(instance.getType())) {
			logger.error("Trying to remove a componnent of type that is not in the graph {}", instance.getType());
			throw new BIPEngineException(
					"Trying to remove a componnent of type that is not in the graph " + instance.getType());
		}

		if (!added.contains(instance.getId())) {
			logger.error(
					"Component {} has never been added to the pool but is asked to be removed or it has been removed already. ID = ",
					instance, instance.getId());
			throw new BIPEngineException("Component " + instance
					+ " has never been added to the pool but is asked to be removed or it has been removed already. ID = "
					+ instance.getId());
		}

		this.added.remove(instance.getId());
		Integer count = this.subsystem.get(instance.getType());
		if (count == null || count.intValue() == 0) {
			logger.error("We are removing a component and we should not be able to remove this one.");
			throw new BIPEngineException("We are removing a component and we should not be able to remove this one.");
		} else {
			this.subsystem.put(instance.getType(), count - 1);
		}
		this.valid = incrementCounters(instance.getType());

		return this.valid;
	}

	/**
	 * 
	 * @param type
	 * @return
	 */
	private boolean incrementCounters(String type) {
		Node node = nodes.get(type);

		if (node == null) {
			logger.error("Trying to remove an instance whose type hasn't been registered yet: {}", type);
			throw new BIPEngineException("Trying to remove an instance whose type hasn't been registered yet: " + type);
		}

		if (node.getEdges().isEmpty()) {
			return this.valid;
		}

		for (Edge e : node.getEdges()) {
			e.incrementCounter();
		}

		Set<String> subsystemNodes = new HashSet<String>();
		for (Map.Entry<String, Integer> entry : this.subsystem.entrySet()) {
			if (entry.getValue().intValue() > 0)
				subsystemNodes.add(entry.getKey());
		}

		return checkValidSystem(subsystemNodes);
	}

	private boolean checkValidSystem(Set<String> system) {
		if (system.isEmpty()) {
			return false;
		}

		for (Node n : nodes.values()) {
			n.notSatisfied();
		}

		return checkValidSystemInternal(system, new HashSet<String>());
	}

	private boolean checkValidSystemInternal(Set<String> system, Set<String> seen) {
		for (String type : system) {
			if (!seen.contains(type)) {
				seen.add(type);
				Node node = nodes.get(type);
				if (node == null) {
					logger.error("Node of type {} does not exist.", type);
					throw new BIPEngineException("Node of type " + type + " does not exist");
				}

				if (node.isSatisfied()) {
					return true;
				}

				Set<Edge> incomingEdges = this.incomingEdges.get(type);

				if (incomingEdges == null || incomingEdges.isEmpty()) {
					node.satisfied();
					return true;
				}

				// TODO Use Guava's MultiMap?
				Map<Color, Set<Edge>> incomingEdgesPerSolution = new HashMap<Color, Set<Edge>>();
				for (Edge incomingEdge : incomingEdges) {
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

					if (solutionIsSatisfied && checkValidSystemInternal(dependenciesOfSolution, seen)) {
						node.satisfied();
						return true;
					}
				}

				node.notSatisfied();
			}
		}

		return false;
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
			return checkDependenciesAND(type);
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

					return checkDependenciesAND(nodeDependencies);
				} else if (edgeSolutionColor.equals(ColorFactory.getUnconditionalSolutionColor())) {
					return checkDependenciesAND(edge.getDestination());
				}
			}
		}

		return false;
	}

	private boolean checkDependenciesAND(String dependency) {
		Set<String> s = new HashSet<String>();
		s.add(dependency);
		return checkDependenciesAND(s);
	}

	private boolean checkDependenciesAND(Set<String> dependencies) {
		for (Node n : nodes.values()) {
			n.notSatisfied();
		}

		return checkDependenciesANDInternal(dependencies, new HashSet<String>());
	}

	private boolean checkDependenciesANDInternal(Set<String> dependencies, Set<String> seen) {
		if (dependencies == null || dependencies.isEmpty())
			return true;

		for (String dependency : dependencies) {
			if (!seen.contains(dependency)) {
				seen.add(dependency);
				Node nodeDependency = nodes.get(dependency);

				if (nodeDependency == null) {
					logger.error("Node of type {} does not exist.", dependency);
					throw new BIPEngineException("Node of type " + dependency + " does not exist");
				}

				if (!nodeDependency.isSatisfied()) {
					Set<Edge> incomingEdges = this.incomingEdges.get(dependency);
					if (incomingEdges == null || incomingEdges.isEmpty()) {
						nodeDependency.satisfied();
					} else {
						// TODO Use Guava's MultiMap?
						Map<Color, Set<Edge>> incomingEdgesPerSolution = new HashMap<Color, Set<Edge>>();
						for (Edge incomingEdge : incomingEdges) {
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
								if (checkDependenciesANDInternal(dependenciesOfSolution, seen)) {
									nodeDependency.satisfied();
									break;
								} else {
									nodeDependency.notSatisfied();
								}
							}
						}

						if (!nodeDependency.isSatisfied()) {
							return false;
						}
					}
				}
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
