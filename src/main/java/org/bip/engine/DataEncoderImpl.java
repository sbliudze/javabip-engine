package org.bip.engine;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;
import org.bip.api.Port;
import org.bip.exceptions.BIPEngineException;
import org.bip.glue.DataWire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deals with the DataGlue. Encodes the informSpecific information.
 * 
 * @author Anastasia Mavridou
 */
public class DataEncoderImpl implements DataEncoder {

	private BDDFactory BDDmanager;
	private DataCoordinator dataCoordinator;
	private BehaviourEncoder behaviourEncoder;

	volatile Map<Entry<Port, Port>, BDD> portsToDVarBDDMapping = new Hashtable<Entry<Port, Port>, BDD>();
	private Logger logger = LoggerFactory.getLogger(CurrentStateEncoderImpl.class);
	Map<Port, BDD> componentOutBDDs = new Hashtable<Port, BDD>();
	Map<Port, BDD> componentInBDDs = new Hashtable<Port, BDD>();
	ArrayList<BDD> implicationsOfDs = new ArrayList<BDD>();
	Map<BDD, ArrayList<BDD>> moreImplications = new Hashtable<BDD, ArrayList<BDD>>();
	Map<Entry<Port, Port>, Boolean> portToTriggersMapping = new Hashtable<Entry<Port, Port>, Boolean>();

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
	public synchronized BDD encodeDisabledCombinations(BIPComponent decidingComponent, Port decidingPort, Map<BIPComponent, Set<Port>> disabledCombinations) throws BIPEngineException {
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

			Set<Port> componentPorts = disabledCombinations.get(component);
			logger.trace("Inform Specific: disabled Component ports size: " + componentPorts.size());
			for (Port port : componentPorts) {
				logger.trace("Inform Specific: disabledPort is " + port.getId() + "of component" + port.component());

				Set<Entry<Port, Port>> allpairsBiDirectionalPairs = portsToDVarBDDMapping.keySet();
				Entry<Port, Port> pairToNegate = new AbstractMap.SimpleEntry<Port, Port>(decidingPort, port);
				if (allpairsBiDirectionalPairs.contains(pairToNegate)) {
					result.andWith(portsToDVarBDDMapping.get(pairToNegate).not());
				}
			}
		}
		return result;
	}

	public BDD specifyDataGlue(Iterable<DataWire> dataGlue) throws BIPEngineException {
		if (dataGlue == null || !dataGlue.iterator().hasNext()) {
			logger.error("The glue parser has failed to compute the data glue.\n" + "\tPossible reasons: No data transfer or corrupt/non-existant glue XML file.");
			throw new BIPEngineException("The glue parser has failed to compute the data glue.\n" + "\tPossible reasons: No data transfer or corrupt/non-existant glue XML file.");
		}

		createDataBDDNodes(dataGlue);
		// Compute the BDD for the dataflow constraints and return it to the
		// Data Coordinator, which then passes it to the BDD Engine kernel
		return computeDvariablesBDDs();
	}

	/**
	 * Conjunction of all implication BDDs.
	 * 
	 * @param
	 * @return
	 */
	private BDD computeDvariablesBDDs() {
		BDD result = BDDmanager.one();
		logger.trace("Adding implications to the data constraints: " + implicationsOfDs.size());
		for (BDD eachD : this.implicationsOfDs) {
			result.andWith(eachD);
		}
		return result;
	}

	/**
	 * Find the d-variables that correspond to each port. Find the BDDs of these
	 * d-variables and return them.
	 * 
	 * @param The
	 *            port and the component this port belongs to.
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

	private void createDataBDDNodes(Iterable<DataWire> dataWires) throws BIPEngineException {
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

			/*
			 * Here take the cross product of in and out variables to create the
			 * d-variables for one data-wire Store this in a Map with the ports
			 * as the key and the d-variable as a value.
			 * 
			 * Before creating the d-variable check for dublicates in the Map.
			 * If this does not exist then create it.
			 */
			for (Port inPort : inPorts) {
				for (Port outPort : outPorts) {
					if (!moreImplications.containsKey(outPort)) {
					}
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
							logger.error("Single node BDD for d-variable for port " + inPort.getId() + " of component " + inPort.component() + " and port " + outPort.getId() + " of component "
									+ outPort.component() + " is null");
							throw new BIPEngineException("Single node BDD for d-variable for port " + inPort.getId() + " of component " + inPort.component() + " and port " + outPort.getId() + " of component "
									+ outPort.component() + " is null");
						}
						logger.trace("Create D-variable BDD node for inPort: " + inPort + " and outPort " + outPort);

						implicationsOfDs.add(node.not().or(componentInBDDs.get(inPort).and(componentOutBDDs.get(outPort))));
						portsToDVarBDDMapping.put(inOutPortsPair, node);
						/*
						 * Store the position of the d-variables in the BDD
						 * manager, for further use in the BDDBIPEngine.
						 */
						dataCoordinator.getdVarPositionsToWires().put(currentSystemBddSize, inOutPortsPair);
						dataCoordinator.getPositionsOfDVariables().add(currentSystemBddSize);
						if (portsToDVarBDDMapping.get(inOutPortsPair) == null || portsToDVarBDDMapping.get(inOutPortsPair).isZero()) {
							logger.error("Single node BDD for d variable for ports " + inPort.getId() + " and " + outPort.toString() + " is equal to null");
							throw new BIPEngineException("Single node BDD for d variable for ports " + inPort.getId() + " and " + outPort.toString() + " is equal to null");
						}
						currentSystemBddSize++;
						logger.trace("CurrentSystemBDDSize: " + currentSystemBddSize);
					}
				}
			}

		}

		// TODO: Consider moving this into the previous cycle
		// TODO: Do it only after discussing having a component instance
		// associated to a port instance.

		// Build BDDs for the data-transfer constraints
		Iterator<DataWire> wires = dataWires.iterator();
		while (wires.hasNext()) {
			DataWire wire = wires.next();
			List<Port> inPorts = inPorts(wire.getTo());
			for (Port inPort : inPorts) {
				ArrayList<BDD> auxiliary = createImplications(inPort);
				logger.trace("Auxiliary size " + auxiliary.size() + " for port " + inPort.getId() + " of component " + inPort.component().getName());
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
			implicationsOfDs.add(temp2);
		}
	}

	/*
	 * NB: Inputs are not ports actually. In the specType the type of the
	 * component is stored. In the id the name of the data variable is stored.
	 */
	private synchronized List<Port> inPorts(Port inData) throws BIPEngineException {
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
			logger.trace("inData: " + inData.getId());
			logger.trace("inData component: " + component.getName());
			dataInPorts.addAll(dataCoordinator.getBehaviourByComponent(component).portsNeedingData(inData.getId()));
			logger.trace("dataInPorts size: " + dataInPorts.size());
			for (Port port : dataInPorts) {
				if (behaviourEncoder.getBDDOfAPort(port.component(), port.getId()) == null) {
					logger.error("BDD for inPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
					throw new BIPEngineException("BDD for inPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
				} else {
					this.componentInBDDs.put(port, behaviourEncoder.getBDDOfAPort(port.component(), port.getId()));
					logger.trace("ComponentInBDDs size: " + componentInBDDs.size());
				}
			}
		}
		return dataInPorts;
	}

	private synchronized List<Port> outPorts(Port outData) throws BIPEngineException {
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
					|| dataCoordinator.getBehaviourByComponent(component).getDataProvidingPorts(outData.getId()) == null) {
				logger.error("getDataProvidingPorts is returning null or empty for a component that is specified in the data wire as input component.");
				throw new BIPEngineException("getDataProvidingPorts is returning null or empty for a component that is specified in the data wire as input component.");
			} else {
				dataOutPorts.addAll(dataCoordinator.getBehaviourByComponent(component).getDataProvidingPorts(outData.getId()));
				logger.trace("Get Data Out Ports size: " + (dataOutPorts.size()));
				for (Port port : dataOutPorts) {
					if (behaviourEncoder.getBDDOfAPort(port.component(), port.getId()) == null) {
						logger.error("BDD for outPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
						throw new BIPEngineException("BDD for outPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
					} else {
						this.componentOutBDDs.put(port, behaviourEncoder.getBDDOfAPort(port.component(), port.getId()));
						logger.trace("ComponentInBDDs size: " + componentOutBDDs.size());
					}
				}
			}
		}
		return dataOutPorts;
	}

	public void setBDDManager(BDDFactory manager) {
		this.BDDmanager = manager;
	}

	public void setBehaviourEncoder(BehaviourEncoder behaviourEncoder) {
		this.behaviourEncoder = behaviourEncoder;
	}

	public void setDataCoordinator(DataCoordinator dataCoordinator) {
		this.dataCoordinator = dataCoordinator;
	}

}
