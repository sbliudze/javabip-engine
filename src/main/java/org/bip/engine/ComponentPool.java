package org.bip.engine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.bip.api.BIPComponent;
import org.bip.api.BIPGlue;
import org.bip.api.PortBase;
import org.bip.api.Require;
import org.bip.engine.api.Pool;
import org.bip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class representing a pool of components used for dynamic JavaBIP
 * 
 * @author rutz
 */
public class ComponentPool implements Pool {
	private Logger logger = LoggerFactory.getLogger(ComponentPool.class);

	// Add lock for concurrency purposes
	private Lock lock;

	// The glue describing the interactions in the system.
	private BIPGlue glue;

	// All nodes in the graph
	private Map<String, Node> nodes;

	// Incoming edges per node
	private MultiMap<String, Edge> incomingEdges;

	// Sorts all edges in the graph per solution
	private MultiMap<Color, Edge> edgesPerSolution;

	// Whether the system is currently valid.
	private boolean valid;

	// Remembers what components are in the pool/system
	// by ID
	private Set<String> added;

	// Remembers the number of components in the pool/system per type
	private Map<String, Integer> subsystem;

	private Map<Set<String>, Boolean> valids;

	public ComponentPool(BIPGlue glue) {
		this.lock = new ReentrantLock();
		this.glue = glue;
		this.nodes = new HashMap<String, Node>();
		this.incomingEdges = new MultiHashMap<String, Edge>();
		this.edgesPerSolution = new MultiHashMap<Color, Edge>();
		this.valid = false;
		this.added = new HashSet<String>();
		this.subsystem = new HashMap<String, Integer>();
		this.valids = new HashMap<Set<String>, Boolean>();
	}

	/**
	 * Must be called before using the pool. Initializes the graph in the system
	 * checker as follows: - One node per type in the glue (so no node for types
	 * who have only spontaneous or internal transitions) - Edge from T to T'
	 * labeled x means that one instance of type T' needs x instances of type T.
	 * 
	 * See more at {@link org.bip.engine.Node} or
	 * {@link org.bip.engine.Edge}
	 */
	public void initialize() {
		lock.lock();
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
		// For each requirement
		for (Require require : requires) {
			// Get the type of the effect
			String effectType = require.getEffect().getSpecType();

			// If only one conjunction of causes the get the unconditional color
			// for solutions
			Color solutionColor = null;
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
					String causeType = cause.getSpecType();
					Node causeNode = nodes.get(causeType);

					if (!seen.contains(effectType)) {
						// Create a new edge or get the one that has been
						// created before
						Edge edge = causeNode.getOrCreate(effectType, solutionColor, effectType);
						// Increment label and counter
						edge.incrementLabel();

						// Store the edges for further use mapped from their
						// solution color
						edgesPerSolution.put(solutionColor, edge);

						// Store incoming edges too.
						incomingEdges.put(effectType, edge);

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

		// Get all connected components
		setConnectedComponents();

		lock.unlock();
	}

	private void setConnectedComponents() {
		Set<String> seen = new HashSet<String>();

		for (Map.Entry<String, Node> entry : nodes.entrySet()) {
			if (!seen.contains(entry.getKey())) {
				seen.addAll(setConnectedComponent(entry.getValue()));
			}
		}
	}

	// BFS to create connected components
	private Set<String> setConnectedComponent(Node start) {
		Queue<Node> q = new LinkedList<Node>();
		Set<String> seen = new HashSet<String>();
		Set<String> toAdd = new HashSet<String>();
		q.add(start);
		while (!q.isEmpty()) {
			Node current = q.poll();
			if (!seen.contains(current.getType())) {
				if (incomingEdges.get(current.getType()) != null) {
					for (Edge e : incomingEdges.get(current.getType())) {
						toAdd.add(e.getSource());
					}
				}

				toAdd.addAll(current.getNeighboursTypes());
				for (String neighbourType : toAdd) {
					q.add(nodes.get(neighbourType));
				}
			}

			seen.add(current.getType());
		}

		// For later use, in case we recompute connected components
		Boolean previous = valids.get(new HashSet<String>(seen));
		if (previous == null) {
			valids.put(new HashSet<String>(seen), false);
		}

		return seen;
	}

	/**
	 * This method cleans up the pool, resets the node and reinitializes
	 * necessary data structures.
	 */
	public void cleanup() {
		lock.lock();
		this.valid = false;
		this.added = new HashSet<String>();
		this.subsystem = new HashMap<String, Integer>();
		for (Map.Entry<Set<String>, Boolean> entry : this.valids.entrySet()) {
			entry.setValue(false);
		}
		for (Node node : nodes.values()) {
			node.reset();
		}
		lock.unlock();
	}

	@Override
	public boolean isValid() {
		return valid;
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
	public boolean addInstance(BIPComponent instance) throws BIPEngineException {
		lock.lock();
		try {
			if (instance == null) {
				logger.error("Trying to add a null component to the pool.");
				throw new BIPEngineException("Trying to add a null component to the pool.");
			}

//			// TODO Find another way to find the ports without casting
//			List<Port> enforceablePorts = ((ExecutorKernel) instance).getBehavior().getEnforceablePorts();

//			// If the component has no enforceable ports, it is not in the graph
//			// but
//			// is a "valid system" so we return it.
//			if (enforceablePorts == null || enforceablePorts.isEmpty()) {
//				return true;
//			} else if (!nodes.containsKey(instance.getType())
//					&& !(enforceablePorts == null || enforceablePorts.isEmpty())) {
//				logger.error("Trying to add a component of type that is not in the graph: {}", instance.getType());
//				throw new BIPEngineException(
//						"Trying to add a component of type that is not in the graph: " + instance.getType());
//			}
//
//			if (this.added.contains(instance.getId())) {
//				logger.error("Component {} of type {} has already been added to the pool or ID is wrong (duplicate)",
//						instance.getId(), instance.getType());
//				throw new BIPEngineException("Component " + instance.getId() + " of type " + instance.getType()
//						+ " has already been added to the pool or ID is wrong (duplicate)");
//			}

			// Always update the counters
			boolean tmpValid = decrementCounters(instance.getType());
			// Modify in the optimizers for valid checks whether the connected
			// subgraph of instance makes the graph valid
			this.valid |= tmpValid;
			if (tmpValid) {
				for (Map.Entry<Set<String>, Boolean> entry : valids.entrySet()) {
					if (entry.getKey().contains(instance.getType())) {
						entry.setValue(true);
						break;
					}
				}
			}

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
			// return a set of all the instances that were in it and empty the
			// pool
			return valid;
		} finally {
			lock.unlock();
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
		lock.lock();
		try {
			if (instance == null) {
				logger.error("Trying to remove a null component from the pool.");
				throw new BIPEngineException("Trying to remove a null component from the pool.");
			}

			// TODO Find another way to find the ports without casting
//			List<Port> enforceablePorts = ((ExecutorKernel) instance).getBehavior().getEnforceablePorts();
//
//			// If the component has no enforceable ports, it is not in the graph
//			// so
//			// the system is as valid as it was before removing it
//			if (enforceablePorts == null || enforceablePorts.isEmpty()) {
//				return this.valid;
//
//				// Check whether this type is in the graph
//			} else if (!nodes.containsKey(instance.getType())
//					&& !(enforceablePorts == null || enforceablePorts.isEmpty())) {
//				logger.error("Trying to remove a componnent of type that is not in the graph {}", instance.getType());
//				throw new BIPEngineException(
//						"Trying to remove a componnent of type that is not in the graph " + instance.getType());
//			}

			// Exception if this component has never been added or been
			// removed.
			if (!added.contains(instance.getId())) {
				logger.error(
						"Component {} of type {} has never been added to the pool but is asked to be removed or it has been removed already.",
						instance.getId(), instance.getType());
				throw new BIPEngineException("Component " + instance.getId() + " of type " + instance.getType()
						+ " has never been added to the pool and asked to be removed or has been removed already.");
			}

			// Decrement the number of those we have
			Integer count = this.subsystem.get(instance.getType());
			if (count == null || count.intValue() == 0) {
				logger.error("We are removing a component and we should not be able to remove this one.");
				throw new BIPEngineException(
						"We are removing a component and we should not be able to remove this one.");
			} else {
				this.subsystem.put(instance.getType(), count - 1);
			}
			// Remove it from the added component
			this.added.remove(instance.getId());

			// Increment the counters and check whether the system is still
			// valid.
			this.valid = incrementCounters(instance.getType());

			return this.valid;
		} finally {
			lock.unlock();
		}
	}

	private boolean incrementCounters(String type) {
		Node node = nodes.get(type);
		String toCheck = null;

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
			if (!e.isSatisfied() && toCheck == null) {
				toCheck = type;
			}
		}

		// If no edge is unsatisfied then just return the previous state
		if (toCheck == null) {
			return this.valid;
		}

		boolean otherSolution = false;
		for (Map.Entry<Set<String>, Boolean> entry : valids.entrySet()) {
			if (entry.getKey().contains(toCheck) && entry.getValue().booleanValue()) {
				Set<String> subsystemToCheck = new HashSet<String>();
				for (String t : entry.getKey()) {
					if (subsystem.get(t) != null && subsystem.get(t).intValue() > 0) {
						subsystemToCheck.add(t);
					}
				}

				boolean v = checkValidSystem(subsystemToCheck);
				if (!v) {
					entry.setValue(false);
				} else {
					return v;
				}
			} else {
				otherSolution |= entry.getValue().booleanValue();
			}
		}

		return otherSolution;
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

				MultiMap<Color, Edge> incomingEdgesPerSolution = new MultiHashMap<Color, Edge>();
				// Sort every edge by solution
				for (Edge incomingEdge : incomingEdges) {
					incomingEdgesPerSolution.put(incomingEdge.getSolutionColor(), incomingEdge);
				}

				// Check that at least on of those solution is satisfied.
				for (Map.Entry<Color, Set<Edge>> entry : incomingEdgesPerSolution.entrySet()) {
					boolean solutionIsSatisfied = true;
					Set<String> dependenciesOfSolution = new HashSet<String>();
					for (Edge edge : entry.getValue()) {
						solutionIsSatisfied &= edge.isSatisfied();
						dependenciesOfSolution.add(edge.getSource());
					}

					// if it is satisfied, check that all dependency nodes of
					// this solution are satisfied.
					if (solutionIsSatisfied && checkDependenciesANDInternal(dependenciesOfSolution, seen)) {
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

				// If all edges of this solution are satisfied, then check their
				// node dependencies
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

					// If no dependencies then it changed nothing and the node
					// is satisfied.
					if (incomingEdges == null || incomingEdges.isEmpty()) {
						nodeDependency.satisfied();
					} else {
						// Sort edges per solution
						MultiMap<Color, Edge> incomingEdgesPerSolution = new MultiHashMap<Color, Edge>();
						for (Edge incomingEdge : incomingEdges) {
							incomingEdgesPerSolution.put(incomingEdge.getSolutionColor(), incomingEdge);
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
								// Check that dependencies of this solution are
								// satisfied too.
								if (checkDependenciesANDInternal(dependenciesOfSolution, seen)) {
									nodeDependency.satisfied();
									break;
								} else {
									nodeDependency.notSatisfied();
								}
							}
						}

						// If none of the solution was satisfied, then the node
						// is not satisfied then system is not valid.
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
