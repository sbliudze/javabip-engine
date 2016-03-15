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
 * Class representing a pool of components used for dynamic JavaBIP
 * 
 * @author rutz
 */
public class ComponentPool {
	private Logger logger = LoggerFactory.getLogger(ComponentPool.class);
	
	// The glue describing the interactions in the system.
	private BIPGlue glue;
	
	// All nodes in the graph
	private Map<String, Node> nodes;
	
	// Incoming edges per node
	private Map<String, Set<Edge>> incomingEdges;
	
	// The pool of components
	private Set<BIPComponent> pool;
	
	// Sorts all edges in the graph per solution
	private Map<Color, Set<Edge>> edgesPerSolution;
	
	// Whether the system is currently valid.
	private boolean valid;
	
	// Remembers what components are in the pool/system
	private Set<String> added;
	
	// Remembers the number of components in the pool/system per type
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
	 * Must be called before using the pool.
	 * Initializes the graph in the system checker as follows: - One node per
	 * type in the glue (so no node for types who have only spontaneous or
	 * internal transitions) - Edge from T to T' labeled x means that one
	 * instance of type T' needs x instances of type T.
	 * 
	 * See more at {@link org.bip.engine.dynamicity.Node} or
	 * {@link org.bip.engine.dynamicity.Edge}
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

		// Create all edges
		Color requirementColor, solutionColor = null;
		String effectType, causeType;
		Edge edge;
		Node causeNode;

		// For each requirement
		for (Require require : requires) {
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
						// TODO: Use Guava's MultiMap?
						Set<Edge> tmp = edgesPerSolution.get(solutionColor);
						if (tmp == null) {
							edgesPerSolution.put(solutionColor, new HashSet<Edge>(Arrays.asList(edge)));
						} else {
							tmp.add(edge);
						}

						// Store incoming edges too.
						// TODO: Use Guava's MultiMap?
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
			// Add them after the Require because there might be statements like
			// A Requires BB and we don't want to forget about multiple
			// instances of the type.
			seen.addAll(tmpSeen);
		}
	}

	/**
	 * This method cleans up the pool, resets the node and reinitializes
	 * necessasry data structures.
	 */
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
	 * Adds an instance to the pool.
	 * 
	 * @param instance
	 *            the instance to be added to the pool.
	 * @return a set of components: - empty if there is no valid system -
	 *         containing only one component if (but not restricted to) there
	 *         was a valid system before and we just added a new component
	 *         (system is still valid) - containing multiple components if
	 *         adding {@code instance} made the system valid.
	 * 
	 * @throws BIPEngineException
	 *             if trying to add a null component or a component with
	 *             unregistered type or a component already in the pool.
	 */
	public Set<BIPComponent> addInstance(BIPComponent instance) throws BIPEngineException {
		if (instance == null) {
			logger.error("Trying to add a null component to the pool.");
			throw new BIPEngineException("Trying to add a null component to the pool.");
		}

		List<Port> enforceablePorts = ((ExecutorKernel) instance).getBehavior().getEnforceablePorts();

		// If the component has no enforceable ports, it is not in the graph but
		// is a "valid system" so we return it.
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

		// Always update the counters
		this.valid |= decrementCounters(instance.getType());
		// Remember we added this component
		this.added.add(instance.getId());
		// Increment the number of components of this type we have added
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

	/**
	 * Removes a component from the system.
	 * 
	 * @param instance
	 *            the component to be removed.
	 * @return {@code true} if the system is still valid and {@code false}
	 *         otherwise.
	 */
	public boolean removeInstance(BIPComponent instance) {
		if (instance == null) {
			logger.error("Trying to remove a null component from the pool.");
			throw new BIPEngineException("Trying to remove a null component from the pool.");
		}

		// Check the type is in the graph
		// TODO check that the component has enforceable ports. Otherwise we
		// just whether the system was valid.
		if (!nodes.containsKey(instance.getType())) {
			logger.error("Trying to remove a componnent of type that is not in the graph {}", instance.getType());
			throw new BIPEngineException(
					"Trying to remove a componnent of type that is not in the graph " + instance.getType());
		}

		// Exception if this component has never been added or been remooved.
		if (!added.contains(instance.getId())) {
			logger.error(
					"Component {} has never been added to the pool but is asked to be removed or it has been removed already. ID = ",
					instance, instance.getId());
			throw new BIPEngineException("Component " + instance
					+ " has never been added to the pool but is asked to be removed or it has been removed already. ID = "
					+ instance.getId());
		}

		// Remove it from the added component
		this.added.remove(instance.getId());
		// Decrement the number of those we have
		Integer count = this.subsystem.get(instance.getType());
		if (count == null || count.intValue() == 0) {
			logger.error("We are removing a component and we should not be able to remove this one.");
			throw new BIPEngineException("We are removing a component and we should not be able to remove this one.");
		} else {
			this.subsystem.put(instance.getType(), count - 1);
		}

		// Increment the counters and check whether the system is still valid.
		this.valid = incrementCounters(instance.getType());

		return this.valid;
	}

	private boolean incrementCounters(String type) {
		Node node = nodes.get(type);

		if (node == null) {
			logger.error("Trying to remove an instance whose type hasn't been registered yet: {}", type);
			throw new BIPEngineException("Trying to remove an instance whose type hasn't been registered yet: " + type);
		}

		// If it doesn't have any outgoing edges then removing it does not
		// change anything to the validity of the system.
		if (node.getEdges().isEmpty()) {
			return this.valid;
		}

		// Increment counters in outgoing edges
		for (Edge e : node.getEdges()) {
			e.incrementCounter();
		}

		// For every node involved in the valid system, check if the system is
		// still valid for at least one of them
		Set<String> subsystemNodes = new HashSet<String>();
		for (Map.Entry<String, Integer> entry : this.subsystem.entrySet()) {
			if (entry.getValue().intValue() > 0)
				subsystemNodes.add(entry.getKey());
		}

		return checkValidSystem(subsystemNodes);
	}

	/*
	 * Utility
	 */
	private boolean checkValidSystem(Set<String> system) {
		if (system.isEmpty()) {
			return false;
		}

		for (Node n : nodes.values()) {
			n.notSatisfied();
		}

		return checkValidSystemInternal(system, new HashSet<String>());
	}

	/*
	 * Should not be called even in the class
	 */
	private boolean checkValidSystemInternal(Set<String> system, Set<String> seen) {
		// For every type involved in the currently running system
		for (String type : system) {
			// pass if we have checked it before (avoid dependency cycles)
			if (!seen.contains(type)) {
				seen.add(type);
				Node node = nodes.get(type);
				if (node == null) {
					logger.error("Node of type {} does not exist.", type);
					throw new BIPEngineException("Node of type " + type + " does not exist");
				}

				// If this node is satisfied, means all its dependencies are
				// satisfied and we should notify whoever called us.
				if (node.isSatisfied()) {
					return true;
				}

				Set<Edge> incomingEdges = this.incomingEdges.get(type);
				// If no incoming edges then no dependencies, so mark the node
				// as satisfied and return true.
				if (incomingEdges == null || incomingEdges.isEmpty()) {
					node.satisfied();
					return true;
				}

				// TODO Use Guava's MultiMap?
				Map<Color, Set<Edge>> incomingEdgesPerSolution = new HashMap<Color, Set<Edge>>();
				// Sort every edge by solution
				for (Edge incomingEdge : incomingEdges) {
					Set<Edge> tmp = incomingEdgesPerSolution.get(incomingEdge.getSolutionColor());
					if (tmp == null) {
						incomingEdgesPerSolution.put(incomingEdge.getSolutionColor(),
								new HashSet<Edge>(Arrays.asList(incomingEdge)));
					} else {
						tmp.add(incomingEdge);
					}
				}

				// Check that at least on of those solution is satisfied.
				for (Map.Entry<Color, Set<Edge>> entry : incomingEdgesPerSolution.entrySet()) {
					boolean solutionIsSatisfied = true;
					Set<String> dependenciesOfSolution = new HashSet<String>();
					for (Edge edge : entry.getValue()) {
						solutionIsSatisfied &= edge.isSatisfied();
						dependenciesOfSolution.add(edge.getSource());
					}

					// if it is satisfied, check that all nodes of this solution
					// are satisfied.
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

		// If no incoming edges, decrement the counters and the system is valid
		if (incomingEdges.get(type) == null || incomingEdges.get(type).isEmpty()) {
			for (Edge edge : node.getEdges()) {
				edge.decrementCounter();
			}

			return true;
			// If no edges, only check its own dependencies
		} else if (node.getEdges().isEmpty()) {
			return checkDependenciesAND(type);
		}

		for (Edge edge : node.getEdges()) {
			// Decrement counters of outgoing edges of this node.
			edge.decrementCounter();

			if (edge.isSatisfied()) {
				Color edgeSolutionColor = edge.getSolutionColor();
				Set<Edge> sameSolutionEdges = edgesPerSolution.get(edgeSolutionColor);

				// If all edges of this solution are satisfied, then check their node dependencies 
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

	/*
	 * Utility
	 */
	private boolean checkDependenciesAND(String dependency) {
		Set<String> s = new HashSet<String>();
		s.add(dependency);
		return checkDependenciesAND(s);
	}

	/*
	 * Utility
	 */
	private boolean checkDependenciesAND(Set<String> dependencies) {
		for (Node n : nodes.values()) {
			n.notSatisfied();
		}

		return checkDependenciesANDInternal(dependencies, new HashSet<String>());
	}

	/*
	 * Should not be used even in the enclosing class.
	 * TODO: Ask for nested methods in Java.
	 */
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
					
					// If no dependencies then it changed nothing and the node is satisfied.
					if (incomingEdges == null || incomingEdges.isEmpty()) {
						nodeDependency.satisfied();
					} else {
						// TODO Use Guava's MultiMap?
						// Sort edges per solution
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

						// For each solution, check if it is valid.
						for (Map.Entry<Color, Set<Edge>> entry : incomingEdgesPerSolution.entrySet()) {
							boolean solutionIsSatisfied = true;
							Set<String> dependenciesOfSolution = new HashSet<String>();
							for (Edge edge : entry.getValue()) {
								solutionIsSatisfied &= edge.isSatisfied();
								dependenciesOfSolution.add(edge.getSource());
							}

							if (solutionIsSatisfied) {
								// TODO get rid of this recursive call.
								// check that dependencies of this solution are satisfied too.
								if (checkDependenciesANDInternal(dependenciesOfSolution, seen)) {
									nodeDependency.satisfied();
									break;
								} else {
									nodeDependency.notSatisfied();
								}
							}
						}

						// If none of the solution was satisfied, then the node is not satisfied then system is not valid.
						if (!nodeDependency.isSatisfied()) {
							return false;
						}
					}
				}
			}
		}

		// All nodes are satisfied then system is valid.
		return true;
	}

	/*
	 * Checks whether all edges in parameter are satisfied.
	 */
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
