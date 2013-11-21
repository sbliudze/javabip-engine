package org.bip.engine;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;

import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;
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

	private BDDBIPEngine engine;
	private DataCoordinator dataCoordinator;
	private BehaviourEncoder behaviourEncoder;


	volatile Map<BiDirectionalPair, BDD> portsToDVarBDDMapping = new Hashtable<BiDirectionalPair, BDD>();
	private Logger logger = LoggerFactory.getLogger(CurrentStateEncoderImpl.class);
	Map<BiDirectionalPair, BDD> componentOutBDDs = new Hashtable<BiDirectionalPair, BDD>();
	Map<BiDirectionalPair, BDD> componentInBDDs = new Hashtable<BiDirectionalPair, BDD>();
	ArrayList<BDD> implicationsOfDs = new ArrayList<BDD>();
	Map<BDD, ArrayList<BDD>> moreImplications = new Hashtable<BDD, ArrayList<BDD>>();
	Map<BiDirectionalPair, Boolean> portToTriggersMapping = new Hashtable<BiDirectionalPair, Boolean>();

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
			result = engine.getBDDManager().one();
			/*
			 * Find corresponding d-variable
			 */
			Set<BIPComponent> disabledComponents = disabledCombinations.keySet();
			for (BIPComponent component : disabledComponents) {
				logger.debug("Inform Specific: disabledComponent is " + component.getName());

				Set<Port> componentPorts = disabledCombinations.get(component);
				logger.debug("Inform Specific: disabled Component ports size: " + componentPorts.size());
				for (Port port : componentPorts) {
					logger.debug("Inform Specific: disabledPort is " + port.id);
					Set<BiDirectionalPair> allpairsBiDirectionalPairs = portsToDVarBDDMapping.keySet();
					
					BiDirectionalPair firstPairToNegate = new BiDirectionalPair(component, port);
					BiDirectionalPair secondPairToNegate = new BiDirectionalPair(decidingComponent, decidingPort);
					BiDirectionalPair pairToNegate = new BiDirectionalPair(firstPairToNegate, secondPairToNegate);
					if (allpairsBiDirectionalPairs.contains(pairToNegate)){
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
		BDD result = engine.getBDDManager().one();
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
	//TODO: after merging the createDataBDDNodes function delete this one
	private synchronized ArrayList<BDD> createImplications(BIPComponent component, Port port) {
		ArrayList<BDD> auxiliary = new ArrayList<BDD>();
		for (Map.Entry<BiDirectionalPair, BDD> pair : portsToDVarBDDMapping.entrySet()) {
			BiDirectionalPair firstPair = (BiDirectionalPair) pair.getKey().getFirst();
			BiDirectionalPair secondPair = (BiDirectionalPair) pair.getKey().getSecond();
			Port firstPort = (Port) firstPair.getSecond();
			BIPComponent firstComponent = (BIPComponent) firstPair.getFirst();
			Port secondPort = (Port) secondPair.getSecond();
			BIPComponent secondComponent = (BIPComponent) secondPair.getFirst();
			if ((firstComponent.equals(component) && firstPort.id.equals(port.id)) || (secondComponent.equals(component) && secondPort.id.equals(port.id))) {
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
		logger.debug("CurrentSystemBDDSize: " + currentSystemBddSize);
		
		// Create BDD nodes for all the variables representing data wires
		Iterator<DataWire> dataIterator = dataWires.iterator();
		while (dataIterator.hasNext()) {
			// Find all (component,port)-pair instances for both ends of the wire 
			DataWire dataWire = dataIterator.next();
			// In-end of the wire
			Map<BIPComponent, Iterable<Port>> componentToInPorts = inPorts(dataWire.to);
			logger.debug("Data WireIn Components size: " + componentToInPorts.size());
			// Out-end of the wire
			Map<BIPComponent, Iterable<Port>> componentToOutPorts = new Hashtable<BIPComponent, Iterable<Port>>();
			componentToOutPorts.putAll(outPorts(dataWire.from));

			/*
			 * Here take the cross product of in and out variables to create the
			 * d-variables for one data-wire Store this in a Map with the ports
			 * as the key and the d-variable as a value.
			 * 
			 * Before creating the d-variable check for dublicates in the Map.
			 * If this does not exist then create it.
			 */
			
			Set<BIPComponent> components = componentToInPorts.keySet();
			for (BIPComponent componentIn : components) {
				for (Port inPort : componentToInPorts.get(componentIn)) {
					BiDirectionalPair inComponentPortPair = new BiDirectionalPair(componentIn, inPort);

					Set<BIPComponent> componentsOut = componentToOutPorts.keySet();
					for (BIPComponent componentOut : componentsOut) {
						for (Port outPort : componentToOutPorts.get(componentOut)) {
							BiDirectionalPair outComponentPortPair = new BiDirectionalPair(componentOut, outPort);
							BiDirectionalPair inOutPortsPair = new BiDirectionalPair(inComponentPortPair, outComponentPortPair);

							if (!portsToDVarBDDMapping.containsKey(inOutPortsPair)) {
								/*
								 * Create new variable in the BDD manager for
								 * the d-variables.
								 */
								if (engine.getBDDManager().varNum() < currentSystemBddSize + 1) {
									engine.getBDDManager().setVarNum(currentSystemBddSize + 1);
								}
								BDD node = engine.getBDDManager().ithVar(currentSystemBddSize);
								if (node == null) {
									logger.error("Single node BDD for d-variable for port " + inPort.id + " of component " + componentIn + " and port " + outPort.id + " of component " + componentOut
											+ " is null");
									throw new BIPEngineException("Single node BDD for d-variable for port " + inPort.id + " of component " + componentIn + " and port " + outPort.id + " of component "
											+ componentOut + " is null");
								}
								logger.info("Create D-variable BDD node of Ports-pair: " + inPort + " " + outPort);
								this.implicationsOfDs.add(node.not().or(componentInBDDs.get(inComponentPortPair).and(componentOutBDDs.get(outComponentPortPair))));
								portsToDVarBDDMapping.put(inOutPortsPair, node);

								/*
								 * Store the position of the d-variables in the
								 * BDD manager, for further use in the
								 * BDDBIPEngine.
								 */
								engine.getdVariablesToPosition().put(currentSystemBddSize, inOutPortsPair);
								engine.getPositionsOfDVariables().add(currentSystemBddSize);
								if (portsToDVarBDDMapping.get(inOutPortsPair) == null || portsToDVarBDDMapping.get(inOutPortsPair).isZero()) {
									logger.error("Single node BDD for d variable for ports " + inPort.id + " and " + outPort.toString() + " is equal to null");
									throw new BIPEngineException("Single node BDD for d variable for ports " + inPort.id + " and " + outPort.toString() + " is equal to null");
								}
								currentSystemBddSize++;
								logger.debug("CurrentSystemBDDSize: " + currentSystemBddSize);
							}
						}
					}
				}
			}
		}

		// TODO: Consider moving this into the previous cycle
		//TODO: Do it only after discussing having a component instance associated to a port instance.
		
		// Build BDDs for the data-transfer constraints 
		Iterator<DataWire> dataIterator2 = dataWires.iterator();
		while (dataIterator2.hasNext()) {
			DataWire dataWire = dataIterator2.next();
			Map<BIPComponent, Iterable<Port>> componentToInPorts = inPorts(dataWire.to);
			logger.debug("Data WireIn Ports size: " + componentToInPorts.size());
			Set<BIPComponent> components = componentToInPorts.keySet();
			for (BIPComponent component : components) {
				for (Port inPort : componentToInPorts.get(component)) {
					BiDirectionalPair inComponentPortPair = new BiDirectionalPair(component, inPort);
					ArrayList<BDD> auxiliary = createImplications(component, inPort);
					logger.debug("Auxiliary size " + auxiliary.size() + " for port " + inPort.id + " of component " + component.getName());
					if (!auxiliary.isEmpty()) {
						moreImplications.put(componentInBDDs.get(inComponentPortPair), auxiliary);
					}
				}
			}
			Set<BDD> entries = moreImplications.keySet();
			logger.debug("moreImplications size: " + entries.size());
			for (BDD bdd : entries) {
				BDD result = engine.getBDDManager().zero();
				logger.debug("entry of moreImplications size: " + moreImplications.get(bdd).size());
				for (BDD oneImplication : moreImplications.get(bdd)) {
					BDD temp = result.or(oneImplication);
					result.free();
					result = temp;
				}
				BDD temp2 = bdd.not().or(result);
				this.implicationsOfDs.add(temp2);
			}
		}
	}

	private synchronized Map<BIPComponent, Iterable<Port>> inPorts(Port inData) throws BIPEngineException {
		/*
		 * Store in the Arraylist below all the possible out ports. Later to
		 * take their cross product.
		 */
		Map<BIPComponent, Iterable<Port>> componentInPortMapping = new Hashtable<BIPComponent, Iterable<Port>>();
		/*
		 * NB: These are not ports actually. In the specType the type of the
		 * component is stored. In the id the name of the data variable is
		 * stored.
		 * 
		 * Input data are always assigned to transitions. Therefore, I need the
		 * list of ports of the component that will re receiving the data.
		 */

		Iterable<BIPComponent> inComponentInstances = dataCoordinator.getBIPComponentInstances(inData.specType);
		for (BIPComponent component : inComponentInstances) {
			logger.debug("inData: " + inData.id);
			logger.debug("inData component: " + component.getName());
			ArrayList<Port> dataInPorts = (ArrayList<Port>) dataCoordinator.getBehaviourByComponent(component).portsNeedingData(inData.id);
			componentInPortMapping.put(component, dataInPorts);
			logger.debug("dataInPorts size: " + dataInPorts.size());
			for (Port port : dataInPorts) {
				if (behaviourEncoder.getBDDOfAPort(component, port.id) == null) {
					logger.error("BDD for inPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
					throw new BIPEngineException("BDD for inPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
				} else {
					BiDirectionalPair inComponentPortPair = new BiDirectionalPair(component, port);
					this.componentInBDDs.put(inComponentPortPair, behaviourEncoder.getBDDOfAPort(component, port.id));
					logger.debug("ComponentInBDDs size: " + componentInBDDs.size());
				}
			}
		}
		return componentInPortMapping;
	}

	private synchronized Map<BIPComponent, Iterable<Port>> outPorts(Port outData) throws BIPEngineException {
		/*
		 * Output data are not associated to transitions. Here, will take the
		 * conjunction of all possible transitions of a component.
		 */
		Iterable<BIPComponent> outComponentInstances = dataCoordinator.getBIPComponentInstances(outData.specType);
		Map<BIPComponent, Iterable<Port>> componentToPort = new Hashtable<BIPComponent, Iterable<Port>>();
		for (BIPComponent component : outComponentInstances) {

			/*
			 * Take the disjunction of all possible ports of this component
			 */
			Set<Port> componentOutPorts = dataCoordinator.getBehaviourByComponent(component).getDataProvidingPorts(outData.id);
			logger.debug("Get Data Out Ports size: " + (componentOutPorts.size()));
			componentToPort.put(component, componentOutPorts);
			for (Port port : componentOutPorts) {
				BiDirectionalPair outComponentPortPair = new BiDirectionalPair(component, port);
				this.componentOutBDDs.put(outComponentPortPair, behaviourEncoder.getBDDOfAPort(component, port.id));
			}
		}
		return componentToPort;
	}

	public void setEngine(BDDBIPEngine engine) {
		this.engine = engine;
	}

	public void setBehaviourEncoder(BehaviourEncoder behaviourEncoder) {
		this.behaviourEncoder = behaviourEncoder;
	}

	public void setDataCoordinator(DataCoordinator dataCoordinator) {
		this.dataCoordinator = dataCoordinator;
	}

}
