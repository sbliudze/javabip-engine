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

	private Iterable<DataWire> dataGlueSpec;

	volatile Map<BiDirectionalPair, BDD> portsToDVarBDDMapping = new Hashtable<BiDirectionalPair, BDD>();
	private Logger logger = LoggerFactory.getLogger(CurrentStateEncoderImpl.class);
	Map<BiDirectionalPair, BDD> componentOutBDDs = new Hashtable<BiDirectionalPair, BDD>();
	Map<BiDirectionalPair, BDD> componentInBDDs = new Hashtable<BiDirectionalPair, BDD>();
	ArrayList<BDD> implicationsOfDs = new ArrayList<BDD>();
	Map<BDD, ArrayList<BDD>> moreImplications = new Hashtable<BDD, ArrayList<BDD>>();
	Map <BiDirectionalPair, Boolean> portToTriggersMapping = new Hashtable<BiDirectionalPair, Boolean> ();

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
	public synchronized BDD informSpecific(BIPComponent decidingComponent, Port decidingPort, Map<BIPComponent, Set<Port>> disabledCombinations) throws BIPEngineException {
		/*
		 * The disabledCombinations and disabledComponents are checked in the
		 * DataCoordinator, wherein exceptions are thrown. Here, we just use
		 * assertion.
		 */
		BDD result;

		assert (disabledCombinations != null);
		if (disabledCombinations.isEmpty()) {
			result = engine.getBDDManager().zero();
		} else {
			result = engine.getBDDManager().one();
			/*
			 * Find corresponding d-variable
			 */
			Set<BIPComponent> disabledComponents = disabledCombinations.keySet();
			for (BIPComponent component : disabledComponents) {
				if (component.equals(decidingComponent)) {
					try {
						logger.error("in inform Specific the deciding component: " + decidingComponent.getName() + " equals the disabled component: ." + component.getName()
								+ "\t That should never happen .");
						throw new BIPEngineException("in inform Specific the deciding component: " + decidingComponent.getName() + " equals the disabled component: ." + component.getName()
								+ "\t That should never happen.");
					} catch (BIPEngineException e) {
						e.printStackTrace();
						throw e;
					}
				}
				logger.debug("Inform Specific: disabledComponent is " + component.getName());
				
				Set<Port> componentPorts =  disabledCombinations.get(component);
				logger.debug("Inform Specific: disabled Component ports size: "+componentPorts.size());
				for (Port port : componentPorts) {
					logger.debug("Inform Specific: disabledPort is " + port.id);
					Set<BiDirectionalPair> allpairsBiDirectionalPairs = portsToDVarBDDMapping.keySet();
					for (BiDirectionalPair pair : allpairsBiDirectionalPairs) {
						BiDirectionalPair pairOne = (BiDirectionalPair) pair.getFirst();
						BIPComponent pairOneComponent = (BIPComponent) pairOne.getFirst();
						Port pairOnePort = (Port) pairOne.getSecond();
						BiDirectionalPair pairTwo = (BiDirectionalPair) pair.getSecond();
						BIPComponent pairTwoComponent = (BIPComponent) pairTwo.getFirst();
						Port pairTwoPort = (Port) pairTwo.getSecond();
						
						if ((component.equals(pairOneComponent) && decidingComponent.equals(pairTwoComponent) && pairTwoPort.id.equals(decidingPort.id) && pairOnePort.id.equals(port.id))
								|| (component.equals(pairTwoComponent) && decidingComponent.equals(pairOneComponent) && pairOnePort.id.equals(decidingPort.id) && pairTwoPort.id.equals(port.id))) {
								
									 result.andWith(portsToDVarBDDMapping.get(pair).not());
									logger.debug("Inform Specific: Pair One Port: " + pairOnePort);
									logger.debug("Inform Specific: Pair Two Port: " + pairTwoPort);
//									BDD tmp = result.and(
//											portsToDVarBDDMapping.get(pair).not());
									// logger.info("Inform Specific: PortsToDVarBDDMapping SIZE: "+portsToDVarBDDMapping.size());
//									result.free();
//									result = tmp;
//								}
//							}
						}
					}
				}
			}
		}
		
		return result;
		
	}

	public BDD specifyDataGlue(Iterable<DataWire> dataGlue) throws BIPEngineException {
		if (dataGlue == null || !dataGlue.iterator().hasNext()) {
			try {
				logger.error("The glue parser has failed to compute the data glue.\n" + "\tPossible reasons: No data transfer or corrupt/non-existant glue XML file.");
				throw new BIPEngineException("The glue parser has failed to compute the data glue.\n" + "\tPossible reasons: No data transfer or corrupt/non-existant glue XML file.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		//TODO: Not really need to have a global for that. Give it as an argument.
		this.dataGlueSpec = dataGlue;
		createDataBDDNodes();
		return computeDvariablesBDDs();
	}

	private BDD computeDvariablesBDDs() {
		BDD result = engine.getBDDManager().one();
		for (BDD eachD : this.implicationsOfDs) {
			result.andWith(eachD);
		}
		return result;
	}

	private synchronized ArrayList<BDD> createImplications (BIPComponent component, Port port){
		ArrayList<BDD> auxiliary = new ArrayList<BDD>();
		for (Map.Entry <BiDirectionalPair, BDD> pair: portsToDVarBDDMapping.entrySet()){
			BiDirectionalPair firstPair = (BiDirectionalPair) pair.getKey().getFirst();
			BiDirectionalPair secondPair = (BiDirectionalPair) pair.getKey().getSecond();
			Port firstPort = (Port) firstPair.getSecond();
			BIPComponent firstComponent = (BIPComponent) firstPair.getFirst();
			Port secondPort = (Port) secondPair.getSecond();
			BIPComponent  secondComponent = (BIPComponent) secondPair.getFirst();
			if ((firstComponent.equals(component) && firstPort.id.equals(port.id)) || 
					(secondComponent.equals(component) && secondPort.id.equals(port.id)) ){
					auxiliary.add(pair.getValue());	
			}
		}	
		return auxiliary;
	}


	private void createDataBDDNodes() throws BIPEngineException {
		/*
		 * Get the number of BDD-nodes of the System. We base this on the
		 * assumption that all the components have registered before. Therefore,
		 * we know the size of the BDD nodes created for states and ports, which
		 * is the current System BDD size.
		 */
		int initialSystemBDDSize = dataCoordinator.getNoPorts() + dataCoordinator.getNoStates();
		int currentSystemBddSize = initialSystemBDDSize;
		logger.debug("CurrentSystemBDDSize: " + currentSystemBddSize);
		Iterator<DataWire> dataIterator =dataGlueSpec.iterator();
		while (dataIterator.hasNext()) {
			DataWire dataWire = dataIterator.next();
			Map<BIPComponent, Iterable<Port>> componentToInPorts = inPorts(dataWire.to);
			logger.debug("Data WireIn Components size: " + componentToInPorts.size());
			Set<BIPComponent> components = componentToInPorts.keySet();
			Map<Port, Map<BIPComponent, Iterable<Port>>> componentOutPorts = new Hashtable<Port, Map<BIPComponent, Iterable<Port>>>();
			for (BIPComponent component : components) {
				logger.debug("Data WireIn component's ports: "+ componentToInPorts.get(component));
				for (Port port : componentToInPorts.get(component)) {
					componentOutPorts.putAll(outPorts(dataWire.from, port));
				}
			}
			
			/*
			 * Here take the cross product of in and out variables to create the
			 * d-variables for one data-wire Store this in a Map with the ports
			 * as the key and the d-variable as a value.
			 * 
			 * Before creating the d-variable check for dublicates in the Map.
			 * If this does not exist then create it.
			 */
			for (BIPComponent component : components) {
				for (Port inPort : componentToInPorts.get(component)) {
					Map<BIPComponent, Iterable<Port>> suitableOutPorts = componentOutPorts.get(inPort);
					Set<BIPComponent> componentsOut = suitableOutPorts.keySet();
					BiDirectionalPair inComponentPortPair = new BiDirectionalPair(component, inPort);

					for (BIPComponent componentOut : componentsOut) {
						for (Port outPort : suitableOutPorts.get(componentOut)) {
							logger.info("Data WireOut component's ports: "+ componentOutPorts.get(inPort).size());
							BiDirectionalPair outComponentPortPair = new BiDirectionalPair(componentOut, outPort);
							BiDirectionalPair inOutPortsPair = new BiDirectionalPair(inComponentPortPair, outComponentPortPair);
							if (!portsToDVarBDDMapping.containsKey(inOutPortsPair) && !(component.equals(componentOut) && inPort.id.equals(outPort.id))) {
								/*
								 * Create new variable in the BDD manager for
								 * the d-variables. Does it start from 0 or 1 ?
								 * if from 0 increase later
								 */
								if (engine.getBDDManager().varNum() < currentSystemBddSize + 1) {
									engine.getBDDManager().setVarNum(currentSystemBddSize + 1);
								}
								BDD node = engine.getBDDManager().ithVar(currentSystemBddSize);
								if (node == null) {
									try {
										logger.error("Single node BDD for d-variable for port " + inPort.id + " of component " + component + " and port " + outPort.id + " of component "
												+ componentOut + " is null");
										throw new BIPEngineException("Single node BDD for d-variable for port " + inPort.id + " of component " + component + " and port " + outPort.id
												+ " of component " + componentOut + " is null");
									} catch (BIPEngineException e) {
										e.printStackTrace();
										throw e;
									}
								}
								logger.info("Create D-variable BDD node of Ports-pair: " + inPort + " " + outPort);
								this.implicationsOfDs.add(node.not().or(componentInBDDs.get(inComponentPortPair).and(componentOutBDDs.get(outComponentPortPair))));
								portsToDVarBDDMapping.put(inOutPortsPair, node);

								/*
								 * Store the position of the d-variables in the
								 * BDD manager
								 */
								engine.getdVariablesToPosition().put(currentSystemBddSize, inOutPortsPair);
								engine.getPositionsOfDVariables().add(currentSystemBddSize);
								if (portsToDVarBDDMapping.get(inOutPortsPair) == null || portsToDVarBDDMapping.get(inOutPortsPair).isZero()) {
									try {
										logger.error("Single node BDD for d variable for ports " + inPort.id + " and " + outPort.toString() + " is equal to null");
										throw new BIPEngineException("Single node BDD for d variable for ports " + inPort.id + " and " + outPort.toString() + " is equal to null");
									} catch (BIPEngineException e) {
										e.printStackTrace();
										throw e;
									}
								}
								currentSystemBddSize++;
								logger.info("CurrentSystemBDDSize: " + currentSystemBddSize);
							}

						}
					}
				}
			}
		}
		Iterator<DataWire> dataIterator2 =dataGlueSpec.iterator();
		while (dataIterator2.hasNext()) {
			DataWire dataWire = dataIterator2.next();
			Map<BIPComponent, Iterable<Port>> componentToInPorts = inPorts(dataWire.to);
			logger.info("Data WireIn Ports size: " + componentToInPorts.size());
			Set<BIPComponent> components = componentToInPorts.keySet();
			for (BIPComponent component : components) {
				for (Port inPort : componentToInPorts.get(component)) {
					BiDirectionalPair inComponentPortPair = new BiDirectionalPair(component, inPort);
					ArrayList<BDD> auxiliary=createImplications(component, inPort);
					logger.info("Auxiliary size " + auxiliary.size()+ " for port "+inPort.id+ " of component "+component.getName());
					if (!auxiliary.isEmpty()) {
						moreImplications.put(componentInBDDs.get(inComponentPortPair), auxiliary);
					}
				}
			}
			Set<BDD> entries = moreImplications.keySet();
			 logger.info("moreImplications size: "+entries.size());
			for (BDD bdd : entries) {
				BDD result = engine.getBDDManager().zero();
				 logger.debug("entry of moreImplications size: "+moreImplications.get(bdd).size());
				for (BDD lala : moreImplications.get(bdd)) {
					BDD temp = result.or(lala);
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
		// ArrayList<Port> componentInPorts = new ArrayList<Port>();
		/*
		 * DB: These are not ports actually. In the specType the type of
		 * the component is stored. In the id the name of the data variable is
		 * stored.
		 * 
		 * Input data are always assigned to transitions. Therefore, I need the
		 * list of ports of the component that will re receiving the data.
		 */
		String inComponentType = inData.specType;

		Iterable<BIPComponent> inComponentInstances = dataCoordinator.getBIPComponentInstances(inComponentType);
		for (BIPComponent component : inComponentInstances) {
			logger.info("inData: " + inData.id);
			logger.info("inData component: " + component.getName());
			ArrayList<Port> dataInPorts = (ArrayList<Port>) dataCoordinator.getBehaviourByComponent(component).portsNeedingData(inData.id);
			componentInPortMapping.put(component, dataInPorts);
			logger.info("dataInPorts size: " + dataInPorts.size());
			for (Port port : dataInPorts) {
				if (behaviourEncoder.getBDDOfAPort(component, port.id) == null) {
					logger.error("BDD for inPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
					throw new BIPEngineException("BDD for inPort in DataEncoder was not found. Possible reason: specifyDataGlue is called before registration of components has finished.");
				} else {
					BiDirectionalPair inComponentPortPair = new BiDirectionalPair(component, port);
					componentInBDDs.put(inComponentPortPair, behaviourEncoder.getBDDOfAPort(component, port.id));
					logger.info("ComponentInBDDs size: " + componentInBDDs.size());

				}
			}
		}
		return componentInPortMapping;
	}

	private synchronized Map<Port, Map<BIPComponent, Iterable<Port>>> outPorts(Port outData, Port decidingPort) throws BIPEngineException {
		/*
		 * Store in the Arraylist below all the possible in ports. Later to take
		 * their cross product.
		 */
		Hashtable<Port, Map<BIPComponent, Iterable<Port>>> componentInToOutPorts = new Hashtable<Port, Map<BIPComponent, Iterable<Port>>>();
		
		/*
		 * Output data are not associated to transitions. Here, will take the
		 * conjunction of all possible transitions of a component.
		 */
		String outComponentType = outData.specType;
		Iterable<BIPComponent> outComponentInstances = dataCoordinator.getBIPComponentInstances(outComponentType);
		Map<BIPComponent, Iterable<Port>> componentToPort = new Hashtable<BIPComponent, Iterable<Port>>();
		for (BIPComponent component : outComponentInstances) {
			/*
			 * Take the disjunction of all possible ports of this component
			 */

			ArrayList<Port> componentOutPorts = (ArrayList<Port>) dataCoordinator.getBehaviourByComponent(component).getEnforceablePorts();
			logger.info("Get Data Out Ports size: " + (componentOutPorts.size()));
			componentToPort.put(component, componentOutPorts);
			for (Port port : componentOutPorts) {
				BiDirectionalPair outComponentPortPair = new BiDirectionalPair(component, port);
				this.componentOutBDDs.put(outComponentPortPair, behaviourEncoder.getBDDOfAPort(component, port.id));
			}
		}
		componentInToOutPorts.put(decidingPort, componentToPort);
		return componentInToOutPorts;
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
