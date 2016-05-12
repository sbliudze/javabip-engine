package org.bip.engine;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;
import org.bip.api.Behaviour;
import org.bip.api.DataWire;
import org.bip.api.Port;
import org.bip.api.PortBase;
import org.bip.engine.api.BehaviourEncoder;
import org.bip.engine.api.DataCoordinator;
import org.bip.engine.api.DataEncoder;
import org.bip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deals with the DataGlue. Encodes the informSpecific information.
 * 
 * @author Anastasia Mavridou
 */
public class DataEncoderImpl implements DataEncoder {

	/** The BD dmanager. */
	private BDDFactory BDDmanager;

	/** The data coordinator. */
	private DataCoordinator dataCoordinator;

	/** The behaviour encoder. */
	private BehaviourEncoder behaviourEncoder;

	/** The ports to d var bdd mapping. */
	volatile Map<Entry<Port, Port>, BDD> portsToDVarBDDMapping = new Hashtable<Entry<Port, Port>, BDD>();

	/** The logger. */
	private Logger logger = LoggerFactory.getLogger(CurrentStateEncoderImpl.class);

	/** The component out bd ds. */
	Map<PortBase, BDD> componentOutBDDs = new Hashtable<PortBase, BDD>();

	/** The component in bd ds. */
	Map<PortBase, BDD> componentInBDDs = new Hashtable<PortBase, BDD>();

	/** The implications of ds. */
	Set<BDD> implicationsOfDs = new HashSet<BDD>();

	Set<BDD> implicationsOfPortsToDs = new HashSet<BDD>();

	/** The port to triggers mapping. */
	Map<Entry<PortBase, PortBase>, Boolean> portToTriggersMapping = new Hashtable<Entry<PortBase, PortBase>, Boolean>();

	/*
	 * Possible implementation: Send each combination's BDD to the engine that
	 * takes the conjunction of all of them on-the-fly. When all the registered
	 * components have informed at an execution cycle then take the conjunction
	 * of the above total BDD with the global BDD.
	 * 
	 * Actually we do not care about the number of components that have
	 * informed. We care whether the semaphore has been totally released.
	 * 
	 * Otherwise, the Data Encoder needs to compute and keep the total BDD. It
	 * needs to know when all the components will have informed the engine about
	 * their current state and only then send the total BDD to the core engine.
	 * 
	 * Three are the main factors that should contribute in the implementation
	 * decision. 1. BDD complexity (especially in the conjunction with the
	 * global BDD) 2. Number of function calls 3. Transfering information
	 * regarding the number of components that have informed. 4. Here also is
	 * the questions whether the DataEncoder should save the BDDs or not at each
	 * execution cycle.
	 * 
	 * @see org.bip.engine.DataEncoder#inform(java.util.Map)
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.bip.engine.api.DataEncoder#encodeDisabledCombinations(org.bip.api.
	 * BIPComponent, org.bip.api.Port, java.util.Map)
	 */
	public synchronized BDD encodeDisabledCombinations(BIPComponent decidingComponent, Port decidingPort,
			Map<BIPComponent, Set<Port>> disabledCombinations) throws BIPEngineException {
		/*
		 * The disabledCombinations and disabledComponents are checked in the
		 * DataCoordinator, wherein exceptions are thrown. Here, we just use
		 * assertion.
		 */
		BDD result;
		assert (disabledCombinations != null);
		result = BDDmanager.one();
		/*
		 * Find corresponding d-variable
		 */
		Set<BIPComponent> disabledComponents = disabledCombinations.keySet();
		for (BIPComponent component : disabledComponents) {
			logger.trace("Inform Specific: decidingPort is " + decidingPort + " of component: " + decidingComponent);
			// System.out.println("Inform Specific: decidingPort is " +
			// decidingPort +
			// " of component: "
			// + decidingComponent);

			Set<Port> componentPorts = disabledCombinations.get(component);
			if (componentPorts == null) {
				throw new BIPEngineException("component " + component + " disabled combinations are null ");
			}
			logger.trace("Inform Specific: disabled Component ports size: " + componentPorts.size());
			for (Port port : componentPorts) {
				logger.trace("Inform Specific: disabledPort is " + port.getId() + "of component" + port.component());
				// System.out.println("Inform Specific: disabledPort is " +
				// port.getId() +
				// "of component"
				// + port.component());

				Set<Entry<Port, Port>> allpairsBiDirectionalPairs = portsToDVarBDDMapping.keySet();
				Entry<Port, Port> pairToNegate = new AbstractMap.SimpleEntry<Port, Port>(decidingPort, port);
				if (allpairsBiDirectionalPairs.contains(pairToNegate)) {
					BDD tmp = result.and(portsToDVarBDDMapping.get(pairToNegate).not());
					result.free();
					result = tmp;
				}
			}
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.DataEncoder#specifyDataGlue(java.lang.Iterable)
	 */
	public Set<BDD> specifyDataGlue(Iterable<DataWire> dataGlue) throws BIPEngineException {
		logger.debug("Create new data BDD nodes for {}", dataGlue);
		if (dataGlue == null || !dataGlue.iterator().hasNext()) {
			logger.error("The glue parser has failed to compute the data glue.\n"
					+ "\tPossible reasons: No data transfer or corrupt/non-existant glue XML file.");
			throw new BIPEngineException("The glue parser has failed to compute the data glue.\n"
					+ "\tPossible reasons: No data transfer or corrupt/non-existant glue XML file.");
		}

		return createDataBDDNodes(dataGlue);
		// Compute the BDD for the dataflow constraints and return it to the
		// Data Coordinator, which then passes it to the BDD Engine kernel
		// return computeDvariablesBDDs();
	}

	// /**
	// * Conjunction of all implication BDDs.
	// *
	// * @return the bdd
	// */
	// private Set<BDD> computeDvariablesBDDs() {
	// Set<BDD> result = BDDmanager.one();
	// long time = System.currentTimeMillis();
	// logger.trace("Adding implications to the data constraints: " +
	// implicationsOfDs.size());
	// System.out.println("Adding implications to the data constraints: " +
	// implicationsOfDs.size());
	// System.out.println("Number of BDD nodes: " + BDDmanager.getNodeNum());
	// /*
	// * The conjunction of all implications takes too much time..
	// */
	// for (BDD eachD : this.implicationsOfDs) {
	// result.andWith(eachD);
	// // System.out.println("result is updated");
	//
	//
	//
	// }
	// BDDmanager.reorder(BDDFactory.REORDER_SIFTITE);
	// System.out.println("EData: Reorder stats: " +
	// BDDmanager.getReorderStats());
	// System.out.println(System.currentTimeMillis() - time);
	// return result;
	// }

	/**
	 * Find the d-variables that correspond to each port. Find the BDDs of these
	 * d-variables and return them.
	 * 
	 * @param port
	 *            the port
	 * @return ArrayList<BDD> of d-variables that correspond to the port given
	 *         as an argument.
	 */
	// TODO: after merging the createDataBDDNodes function delete this one
	private synchronized ArrayList<BDD> createImplications(Port port) {
		ArrayList<BDD> auxiliary = new ArrayList<BDD>();

		logger.trace("Implication for port " + port + " of component " + port.component());
		for (Map.Entry<Entry<Port, Port>, BDD> pair : portsToDVarBDDMapping.entrySet()) {
			Port firstPair = pair.getKey().getKey();
			Port secondPair = pair.getKey().getValue();
			if (firstPair.equals(port) || secondPair.equals(port)) {
				logger.trace("\t port " + firstPair + " of component " + firstPair.component());
				logger.trace("\t port " + secondPair + " of component " + secondPair.component());
				auxiliary.add(pair.getValue());
			}
		}
		return auxiliary;
	}

	/**
	 * Creates the data bdd nodes.
	 * 
	 * @param dataWires
	 *            the data wires
	 * @throws BIPEngineException
	 *             the BIP engine exception
	 */
	private Set<BDD> createDataBDDNodes(Iterable<DataWire> dataWires) throws BIPEngineException {
		/*
		 * Get the number of BDD-nodes of the System. We base this on the
		 * assumption that all the components have registered before. Therefore,
		 * we know the size of the BDD nodes created for states and ports, which
		 * is the current System BDD size.
		 */
		int initialSystemBDDSize = dataCoordinator.getNoPorts() + dataCoordinator.getNoStates();
		int currentSystemBddSize = initialSystemBDDSize;
		logger.trace("CurrentSystemBDDSize: " + currentSystemBddSize);

		// Create BDD nodes for all the variables representing data wires
		Iterator<DataWire> dataIterator = dataWires.iterator();
		while (dataIterator.hasNext()) {
			// Find all (component,port)-pair instances for both ends of the
			// wire
			DataWire dataWire = dataIterator.next();
			// In-end of the wire
			List<Port> inPorts = inPorts(dataWire.getTo());
			logger.trace("inPorts Size: " + inPorts.size());
			// Out-end of the wire
			List<Port> outPorts = outPorts(dataWire.getFrom());
			logger.trace("outPorts size: " + outPorts.size());

			currentSystemBddSize = crossProductOfPorts(inPorts, outPorts, currentSystemBddSize);

		}

		createImplicationsOfPortsToDs(dataWires);

		Set<BDD> result = new HashSet<BDD>(implicationsOfDs);
		result.addAll(implicationsOfPortsToDs);
		logger.debug("Size of result {}", result.size());
		logger.debug("Size of system {}", currentSystemBddSize);
		return result;
	}

	public synchronized Set<BDD> extendDataBDDNodes(Iterable<DataWire> wires, BIPComponent newComponent) {
		logger.debug("Extending the bdd nodes for {}", newComponent);
		String newComponentType = newComponent.getType();
		int currentSystemBddSize = dataCoordinator.getNoPorts() + dataCoordinator.getNoStates();
		currentSystemBddSize += portsToDVarBDDMapping.size();
		logger.debug("{} BDD nodes in the system already", currentSystemBddSize);

		for (DataWire wire : wires) {
			String wireInType = wire.getTo().getSpecType(), wireOutType = wire.getFrom().getSpecType();
			boolean isIn = wireInType.equals(newComponentType), isOut = wireOutType.equals(newComponentType);

			logger.debug("Wire {} is in ({}), is out (" + isOut + ")", wire, isIn);

			if (!isIn && !isOut) {
				continue;
			}

			if (isIn) {
				// 1 x n
				List<Port> inPorts = inPorts(wire.getTo(), newComponent);
				List<Port> outPorts = outPorts(wire.getFrom());
				currentSystemBddSize = crossProductOfPorts(inPorts, outPorts, currentSystemBddSize);
			}

			if (isOut) {
				// n x 1
				List<Port> inPorts = inPorts(wire.getTo());
				List<Port> outPorts = outPorts(wire.getFrom(), newComponent);
				currentSystemBddSize = crossProductOfPorts(inPorts, outPorts, currentSystemBddSize);
			}
		}

		for (BDD bdd : implicationsOfPortsToDs) {
			bdd.free();
		}
		implicationsOfPortsToDs.clear();

		createImplicationsOfPortsToDs(wires);

		Set<BDD> result = new HashSet<BDD>(implicationsOfDs);
		result.addAll(implicationsOfPortsToDs);
		logger.debug("Size of result {}", result.size());
		logger.debug("Size of system {}", currentSystemBddSize);
		return result;
	}

	private int crossProductOfPorts(List<Port> inPorts, List<Port> outPorts, int currentSystemBddSize) {
		/*
		 * Here take the cross product of in and out variables to create the
		 * d-variables for one data-wire Store this in a Map with the ports as
		 * the key and the d-variable as a value.
		 * 
		 * Before creating the d-variable check for dublicates in the Map. If
		 * this does not exist then create it.
		 */
		for (Port inPort : inPorts) {
			for (Port outPort : outPorts) {
				Entry<Port, Port> inOutPortsPair = new AbstractMap.SimpleEntry<Port, Port>(inPort, outPort);

				if (!portsToDVarBDDMapping.containsKey(inOutPortsPair)) {
					/*
					 * Create new variable in the BDD manager for the
					 * d-variables.
					 */
					if (BDDmanager.varNum() < currentSystemBddSize + 1) {
						BDDmanager.setVarNum(currentSystemBddSize + 1);
					}
					BDD node = BDDmanager.ithVar(currentSystemBddSize);
					if (node == null) {
						logger.error("Single node BDD for d-variable for port " + inPort.getId() + " of component "
								+ inPort.component() + " and port " + outPort.getId() + " of component "
								+ outPort.component() + " is null");
						throw new BIPEngineException("Single node BDD for d-variable for port " + inPort.getId()
								+ " of component " + inPort.component() + " and port " + outPort.getId()
								+ " of component " + outPort.component() + " is null");
					}
					logger.debug("Create D-variable BDD node for inPort: " + inPort + " and outPort " + outPort);

					implicationsOfDs.add(node.not().or(componentInBDDs.get(inPort).and(componentOutBDDs.get(outPort))));
					portsToDVarBDDMapping.put(inOutPortsPair, node);
					/*
					 * Store the position of the d-variables in the BDD manager,
					 * for further use in the BDDBIPEngine.
					 */
					dataCoordinator.getdVarPositionsToWires().put(currentSystemBddSize, inOutPortsPair);
					dataCoordinator.getPositionsOfDVariables().add(currentSystemBddSize);
					if (portsToDVarBDDMapping.get(inOutPortsPair) == null
							|| portsToDVarBDDMapping.get(inOutPortsPair).isZero()) {
						logger.error("Single node BDD for d variable for ports " + inPort.getId() + " and "
								+ outPort.toString() + " is equal to null");
						throw new BIPEngineException("Single node BDD for d variable for ports " + inPort.getId()
								+ " and " + outPort.toString() + " is equal to null");
					}
					currentSystemBddSize++;
					logger.debug("CurrentSystemBDDSize: {}", currentSystemBddSize);
				}
			}
		}

		return currentSystemBddSize;
	}

	private synchronized void createImplicationsOfPortsToDs(Iterable<DataWire> wires) {
		Map<BDD, ArrayList<BDD>> moreImplications = new Hashtable<BDD, ArrayList<BDD>>();
		// TODO: Consider moving this into the previous cycle
		// TODO: Do it only after discussing having a component instance
		// associated to a port instance.
		// Build BDDs for the data-transfer constraints
		Iterator<DataWire> wiresIt = wires.iterator();
		while (wiresIt.hasNext()) {
			DataWire wire = wiresIt.next();
			List<Port> inPorts = inPorts(wire.getTo());
			logger.debug("Wire {} has {} associated ports in the system", wire, inPorts.size());
			for (Port inPort : inPorts) {
				ArrayList<BDD> auxiliary = createImplications(inPort);
				logger.trace("Auxiliary size " + auxiliary.size() + " for port " + inPort.getId() + " of component "
						+ inPort.component().getId());
				if (!auxiliary.isEmpty()) {
					moreImplications.put(componentInBDDs.get(inPort), auxiliary);
				}
			}
		}

		Set<BDD> entries = moreImplications.keySet();
		logger.trace("moreImplications size: " + entries.size());
		for (BDD bdd : entries) {
			BDD result = BDDmanager.zero();
			logger.trace("entry of moreImplications size: " + moreImplications.get(bdd).size());
			for (BDD oneImplication : moreImplications.get(bdd)) {
				BDD temp = result.or(oneImplication);
				result.free();
				result = temp;
			}
			BDD temp2 = bdd.not().or(result);
			implicationsOfPortsToDs.add(temp2);
		}

	}

	/*
	 * NB: Inputs are not ports actually. In the specType the type of the
	 * component is stored. In the id the name of the data variable is stored.
	 */
	/**
	 * In ports.
	 * 
	 * @param inData
	 *            the in data
	 * @return the list
	 * @throws BIPEngineException
	 *             the BIP engine exception
	 */
	private synchronized List<Port> inPorts(PortBase inData) throws BIPEngineException {
		/*
		 * Store in the Arraylist below all the possible in ports. Later to take
		 * their cross product.
		 */
		/*
		 * Input data are always assigned to transitions. Therefore, I need the
		 * list of ports of the component that will re receiving the data.
		 */
		List<Port> dataInPorts = new ArrayList<Port>();
		Iterable<BIPComponent> inComponentInstances = dataCoordinator.getBIPComponentInstances(inData.getSpecType());
		for (BIPComponent component : inComponentInstances) {
			if (dataCoordinator.getBehaviourByComponent(component).portsNeedingData(inData.getId()).isEmpty()
					|| dataCoordinator.getBehaviourByComponent(component).portsNeedingData(inData.getId()) == null) {
				logger.error("Output Component of data wire for component " + inData.getSpecType() + " and data "
						+ inData.getId()
						+ " is incorrect. Possible reasons: 1) Input and output components of data wire are reversed; 2) The required data is not specified in the data wires.");
				throw new BIPEngineException("Output Component of data wire for component " + inData.getSpecType()
						+ " and data " + inData.getId()
						+ " is incorrect. Possible reasons: 1) Input and output components of data wire are reversed; 2) The required data is not specified in the data wires.");
			} else {
				logger.trace("inData: " + inData.getId());
				logger.trace("inData component: " + component.getId());
				dataInPorts.addAll(dataCoordinator.getBehaviourByComponent(component).portsNeedingData(inData.getId()));
				logger.trace("dataInPorts size: " + dataInPorts.size());
				for (Port port : dataInPorts) {
					if (behaviourEncoder.getBDDOfAPort(port.component(), port.getId()) == null) {
						logger.error(
								"BDD for inPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
						throw new BIPEngineException(
								"BDD for inPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
					} else {
						this.componentInBDDs.put(port, behaviourEncoder.getBDDOfAPort(port.component(), port.getId()));
						logger.trace("ComponentInBDDs size: " + componentInBDDs.size());
					}
				}
			}
		}
		return dataInPorts;
	}

	private List<Port> inPorts(PortBase inData, BIPComponent component) {
		/*
		 * Store in the Arraylist below all the possible in ports. Later to take
		 * their cross product.
		 */
		/*
		 * Input data are always assigned to transitions. Therefore, I need the
		 * list of ports of the component that will re receiving the data.
		 */
		List<Port> dataInPorts = new ArrayList<Port>();

		Behaviour componentBehaviour = dataCoordinator.getBehaviourByComponent(component);
		List<Port> portsNeedingData = componentBehaviour.portsNeedingData(inData.getId());
		logger.debug("For {}, ports needing data {}", component, portsNeedingData);
		if (portsNeedingData == null || portsNeedingData.isEmpty()) {
			logger.error("Output Component of data wire for component " + inData.getSpecType() + " and data "
					+ inData.getId()
					+ " is incorrect. Possible reasons: 1) Input and output components of data wire are reversed; 2) The required data is not specified in the data wires.");
			throw new BIPEngineException("Output Component of data wire for component " + inData.getSpecType()
					+ " and data " + inData.getId()
					+ " is incorrect. Possible reasons: 1) Input and output components of data wire are reversed; 2) The required data is not specified in the data wires.");
		} else {
			logger.trace("inData: " + inData.getId());
			logger.trace("inData component: " + component.getId());
			dataInPorts.addAll(portsNeedingData);
			logger.trace("dataInPorts size: " + dataInPorts.size());
			for (Port port : dataInPorts) {
				BDD bddOfPort = behaviourEncoder.getBDDOfAPort(port.component(), port.getId());
				if (bddOfPort == null) {
					logger.error(
							"BDD for inPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
					throw new BIPEngineException(
							"BDD for inPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
				} else {
					componentInBDDs.put(port, bddOfPort);
					logger.trace("ComponentInBDDs size: " + componentInBDDs.size());
				}
			}
		}
		return dataInPorts;
	}

	/**
	 * Out ports.
	 * 
	 * @param outData
	 *            the out data
	 * @return the list
	 * @throws BIPEngineException
	 *             the BIP engine exception
	 */
	private synchronized List<Port> outPorts(PortBase outData) throws BIPEngineException {
		/*
		 * Store in the Arraylist below all the possible out ports. Later to
		 * take their cross product.
		 */
		/*
		 * NB: These are not ports actually. In the specType the type of the
		 * component is stored. In the id the name of the data variable is
		 * stored.
		 * 
		 * Input data are always assigned to transitions. Therefore, I need the
		 * list of ports of the component that will be receiving the data.
		 */
		Iterable<BIPComponent> outComponentInstances = dataCoordinator.getBIPComponentInstances(outData.getSpecType());
		List<Port> dataOutPorts = new ArrayList<Port>();
		for (BIPComponent component : outComponentInstances) {
			if (dataCoordinator.getBehaviourByComponent(component).getDataProvidingPorts(outData.getId()).isEmpty()
					|| dataCoordinator.getBehaviourByComponent(component)
							.getDataProvidingPorts(outData.getId()) == null) {
				logger.error(
						"Input Component of data wire is incorrect. Possible reason: Input and output of data wire are reversed.");
				throw new BIPEngineException(
						"Input Component of data wire is incorrect. Possible reason: Input and output of data wire are reversed.");
			} else {
				dataOutPorts.addAll(
						dataCoordinator.getBehaviourByComponent(component).getDataProvidingPorts(outData.getId()));
				logger.trace("Get Data Out Ports size: " + (dataOutPorts.size()));
				for (Port port : dataOutPorts) {
					if (behaviourEncoder.getBDDOfAPort(port.component(), port.getId()) == null) {
						logger.error(
								"BDD for outPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
						throw new BIPEngineException(
								"BDD for outPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
					} else {
						this.componentOutBDDs.put(port, behaviourEncoder.getBDDOfAPort(port.component(), port.getId()));
						logger.trace("ComponentInBDDs size: " + componentOutBDDs.size());
					}
				}
			}
		}
		return dataOutPorts;
	}

	private List<Port> outPorts(PortBase outData, BIPComponent component) {
		/*
		 * Store in the Arraylist below all the possible out ports. Later to
		 * take their cross product.
		 */
		/*
		 * NB: These are not ports actually. In the specType the type of the
		 * component is stored. In the id the name of the data variable is
		 * stored.
		 * 
		 * Input data are always assigned to transitions. Therefore, I need the
		 * list of ports of the component that will be receiving the data.
		 */
		List<Port> dataOutPorts = new ArrayList<Port>();

		Behaviour componentBehaviour = dataCoordinator.getBehaviourByComponent(component);
		Set<Port> dataProvidingPorts = componentBehaviour.getDataProvidingPorts(outData.getId());
		logger.debug("For {}, ports providing data {}", component, dataProvidingPorts);
		if (dataProvidingPorts == null || dataProvidingPorts.isEmpty()) {
			logger.error(
					"Input Component of data wire is incorrect. Possible reason: Input and output of data wire are reversed.");
			throw new BIPEngineException(
					"Input Component of data wire is incorrect. Possible reason: Input and output of data wire are reversed.");
		} else {
			dataOutPorts.addAll(dataProvidingPorts);
			logger.trace("Get Data Out Ports size: " + (dataOutPorts.size()));
			for (Port port : dataOutPorts) {
				BDD bddOfPort = behaviourEncoder.getBDDOfAPort(port.component(), port.getId());
				if (bddOfPort == null) {
					logger.error(
							"BDD for outPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
					throw new BIPEngineException(
							"BDD for outPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
				} else {
					componentOutBDDs.put(port, bddOfPort);
					logger.trace("ComponentInBDDs size: " + componentOutBDDs.size());
				}
			}
		}
		return dataOutPorts;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.bip.engine.api.DataEncoder#setBDDManager(net.sf.javabdd.BDDFactory)
	 */
	public void setBDDManager(BDDFactory manager) {
		this.BDDmanager = manager;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.bip.engine.api.DataEncoder#setBehaviourEncoder(org.bip.engine.api.
	 * BehaviourEncoder)
	 */
	public void setBehaviourEncoder(BehaviourEncoder behaviourEncoder) {
		this.behaviourEncoder = behaviourEncoder;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.bip.engine.api.DataEncoder#setDataCoordinator(org.bip.engine.api.
	 * DataCoordinator)
	 */
	public void setDataCoordinator(DataCoordinator dataCoordinator) {
		this.dataCoordinator = dataCoordinator;
	}

}
