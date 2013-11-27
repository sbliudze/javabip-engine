package org.bip.engine;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Semaphore;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.api.Behaviour;
import org.bip.api.Data;
import org.bip.api.Port;
import org.bip.exceptions.BIPEngineException;
import org.bip.glue.BIPGlue;
import org.bip.glue.DataWire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * There is no need for DataCoordinator interface, just DataCoordinatorImpl will implement BIP engine interface. DataCoordinatorImpl needs BIP
 * coordinator ( BIP Engine again ) that actual does all the computation of BIP engine. DataCoordinatorImpl takes care of only of data querying and
 * passing to BIP executors.
 * 
 * DataCoordinator intercepts call register and inform from BIPExecutor.
 * 
 * @authors: mavridou, zolotukhina
 */

public class DataCoordinatorImpl implements BIPEngine, InteractionExecutor, DataCoordinator {

	private Logger logger = LoggerFactory.getLogger(BIPCoordinatorImpl.class);

	private ArrayList<BIPComponent> registeredComponents = new ArrayList<BIPComponent>();

	/**
	 * Helper hashtable with integers representing the local identities of registered components as the keys and the Behaviours of these components as
	 * the values.
	 */
	private Hashtable<BIPComponent, Behaviour> componentBehaviourMapping = new Hashtable<BIPComponent, Behaviour>();
	/**
	 * Helper hashset of the components that have informed in an execution cycle.
	 */
	private Hashtable<BIPComponent, ArrayList<Port>> componentUndecidedPorts = new Hashtable<BIPComponent, ArrayList<Port>>();

	/**
	 * Helper hashtable with strings as keys representing the component type of the registered components and ArrayList of BIPComponent instances that
	 * correspond to the component type specified in the key.
	 */
	private Hashtable<String, ArrayList<BIPComponent>> typeInstancesMapping = new Hashtable<String, ArrayList<BIPComponent>>();

	private ArrayList<DataWire> dataWires;

	/** Number of ports of components registered */
	private int nbPorts;

	/** Number of states of components registered */
	private int nbStates;

	private int count;

	/**
	 * Create instances of all the the Data Encoder and of the BIPCoordinator
	 */
	private DataEncoder dataEncoder = new DataEncoderImpl();
	private BIPCoordinator bipCoordinator = null;

	private boolean registrationFinished = false;

	private Map<String, Map<String, Set<DataWire>>> componentDataWires;

	private Semaphore registrationSemaphore;

	private InteractionExecutor interactionExecutor;

	private Map<Integer, BiDirectionalPair> dVariablesToPosition = new Hashtable<Integer, BiDirectionalPair>();

	private List<Integer> positionsOfDVariables = new ArrayList<Integer>();

	public DataCoordinatorImpl(BIPCoordinator bipCoordinator) {
		if (bipCoordinator == null)
			this.bipCoordinator = new BIPCoordinatorImpl();
		else
			this.bipCoordinator = bipCoordinator;
		
		this.bipCoordinator.setInteractionExecutor(this);
		dataEncoder.setDataCoordinator(this);
		dataEncoder.setBehaviourEncoder(this.bipCoordinator.getBehaviourEncoderInstance());
		dataEncoder.setBDDManager(this.bipCoordinator.getBDDManager());
//		dataEncoder.setEngine(this.bipCoordinator.getBDDManager());
		componentDataWires = new HashMap<String, Map<String, Set<DataWire>>>();
		registrationSemaphore = new Semaphore(1);
	}

	/**
	 * Sends interactions-glue to the BIP Coordinator Sends data-glue to the Data Encoder.
	 */
	public void specifyGlue(BIPGlue glue) {
		bipCoordinator.specifyGlue(glue);
		this.dataWires = glue.dataWires;
		try {
			/*
			 * Send the information about the dataWires to the DataEncoder to create the d-variables BDD nodes.
			 * 
			 * specifyDataGlue checks the validity of wires and throws an exception if necessary.
			 */
			bipCoordinator.specifyPermanentConstraints(dataEncoder.specifyDataGlue(dataWires));
		} catch (BIPEngineException e) {
			e.printStackTrace();
		}

		for (String componentType : typeInstancesMapping.keySet()) {
			// here we just get the behaviour of some component of this type
			// for all other components the Data and the Wires will be exactly the same
			Behaviour behaviour = componentBehaviourMapping.get(typeInstancesMapping.get(componentType).get(0));
			Map<String, Set<DataWire>> dataWire = new Hashtable<String, Set<DataWire>>();
			for (Port port : behaviour.getEnforceablePorts()) {
				for (Data data : behaviour.portToDataInForGuard(port)) {
					// if this data has already been treated for another port
					if (dataWire.containsKey(data)) {
						continue;
					}
					Set<DataWire> wireSet = new HashSet<DataWire>();
					for (DataWire wire : this.dataWires) {
						if (wire.isIncoming(data.name(), behaviour.getComponentType())) {
							wireSet.add(wire);
							logger.debug("Added wire " + wire.from + " to data " + data.name() + " of comp " + componentType);
						}
					}
					dataWire.put(data.name(), wireSet);
				}
			}

			componentDataWires.put(componentType, dataWire);
		}
		registrationFinished = true;
		registrationSemaphore.release();
	}

	public void register(BIPComponent component, Behaviour behaviour) {
		/*
		 * The condition below checks whether the component has already been registered.
		 */
		try {
			if (component == null) {
				throw new BIPEngineException("Registering component must not be null.");
			}
			if (registeredComponents.contains(component)) {
				logger.error("Component " + component.getName() + " has already registered before.");
				throw new BIPEngineException("Component " + component.getName() + " has already registered before.");
			} else {
				registeredComponents.add(component);
				componentBehaviourMapping.put(component, behaviour);
				nbPorts += ((ArrayList<Port>) behaviour.getEnforceablePorts()).size();
				nbStates += ((ArrayList<String>) behaviour.getStates()).size();
				bipCoordinator.register(component, behaviour);
			}
			ArrayList<BIPComponent> componentInstances = new ArrayList<BIPComponent>();

			/*
			 * If this component type already exists in the hashtable, update the ArrayList of BIPComponents that corresponds to this component type.
			 */
			if (typeInstancesMapping.containsKey(component.getName())) {
				componentInstances.addAll(typeInstancesMapping.get(component.getName()));
			}

			componentInstances.add(component);
			// SB: Not sure this is necessary, but should not harm
			typeInstancesMapping.remove(component.getName());
			typeInstancesMapping.put(component.getName(), componentInstances);
		} catch (BIPEngineException e) {
			e.printStackTrace();
		}
	}

	public synchronized void inform(BIPComponent component, String currentState, Set<Port> disabledPorts) {
		// for each component store its undecided ports
		// TODO create undecided port with the help of set.removeAll
		componentUndecidedPorts.put(component, getUndecidedPorts(component, currentState, disabledPorts));

		/*
		 * If all components have not finished registering the informSpecific cannot be done: In the informSpecific information is required from all
		 * the registered components.
		 */
		if (!registrationFinished) {
			try {
				registrationSemaphore.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		try {
			doInformSpecific(component);
		} catch (BIPEngineException e) {
			e.printStackTrace();
		}
		/*
		 * Inform the BIPCoordinator only after all the informSpecifics for the particular component have finished
		 */
		bipCoordinator.inform(component, currentState, disabledPorts);
	}

	/**
	 * Send each disabled combination of each deciding Component directly to the Data Encoder.
	 * 
	 * Exceptions are thrown if: 1. DecidingComponent are not in the list of registered components. 2. Deciding Port of the deciding component (holder
	 * component) is not specified in the Behaviour of the holder. 3. DisabledComponents in the disabledCombinations are not in the list of registered
	 * components. 4. Disabled Ports in the disabledCombinations are not specified in the Behaviour of the holder component.
	 */
	public synchronized void informSpecific(BIPComponent decidingComponent, Port decidingPort, Map<BIPComponent, Set<Port>> disabledCombinations) throws BIPEngineException {
		if (disabledCombinations == null || disabledCombinations.isEmpty()) {
			logger.warn("No disabled combinations specified in informSpecific for deciding component." + decidingComponent.getName() + " for deciding port " + decidingPort.id
					+ " Map of disabledCombinations is empty.");
			/*
			 * This is not a bad situation, since that only means that all combinations are acceptable. Hence nothing to do.
			 */
			return;
		}
		assert (disabledCombinations != null && !disabledCombinations.isEmpty());

		if (!registeredComponents.contains(decidingComponent)) {
			logger.error("Deciding component specified in informSpecific is not in the list of registered components");
			throw new BIPEngineException("Deciding component specified in informSpecific is not in the list of registered components");
		}
		assert (registeredComponents.contains(decidingComponent));

		ArrayList<Port> componentPorts = (ArrayList<Port>) componentBehaviourMapping.get(decidingComponent).getEnforceablePorts();
		if (!componentPorts.contains(decidingPort)) {
			logger.error("Deciding port {} in informSpecific is not specified in the behaviour of the deciding component {}.", decidingPort, decidingComponent.getName());
			throw new BIPEngineException("Deciding port in informSpecific is not specified in the behaviour of the deciding component.");
		}
		assert (componentPorts.contains(decidingPort));

		Set<BIPComponent> disabledComponents = disabledCombinations.keySet();
		for (BIPComponent component : disabledComponents) {
			if (!registeredComponents.contains(component)) {
				logger.error("Component " + component.getName() + " specified in the disabledCombinations of informSpecific was not registered.");
				throw new BIPEngineException("Component " + component.getName() + " specified in the disabledCombinations of informSpecific was not registered.");
			}
			assert (registeredComponents.contains(component));

			Set<Port> disabledPortsOfOneComponent = disabledCombinations.get(component);
			for (Port port : disabledPortsOfOneComponent) {
				List<Port> componentEnforceablePorts = componentBehaviourMapping.get(component).getEnforceablePorts();
				if (!componentEnforceablePorts.contains(port)) {
					logger.error("Disabled port " + port + " in informSpecific is not specified in the behaviour of the disabled component: " + component.getName());
					throw new BIPEngineException("Disabled port " + port + " in informSpecific is not specified in the behaviour of the disabled component: " + component.getName());
					
				}
				assert (componentEnforceablePorts.contains(port));
			}
		}

		/*
		 * At this point we know that all the involved components are registered and the specified ports belong to corresponding behaviours.
		 * 
		 * Send each disabled combination of each deciding Component directly to the Data Encoder.
		 */
		bipCoordinator.specifyTemporaryConstraints(dataEncoder.encodeDisabledCombinations(decidingComponent, decidingPort, disabledCombinations));

	}
	
	/*
	 * Merging the "subinteractions". Some port can in more than one of the enabled d-variables. 
	 * This port should not be sent to the Executors twice
	 */
	private List<Map<BIPComponent, Iterable<Port>>> mergingSubInteractions(byte[] chosenInteraction, ArrayList<Port> portsExecuted){
		List<Map<BIPComponent, Iterable<Port>>> bigInteraction = new ArrayList<Map<BIPComponent, Iterable<Port>>>();
		for (Integer i : positionsOfDVariables) {
			Map<BIPComponent, Iterable<Port>> oneInteraction = new Hashtable<BIPComponent, Iterable<Port>>();


			if (chosenInteraction[i] == 1) {
				logger.debug("chosenInteraction length " + chosenInteraction.length);
				BiDirectionalPair pair = dVariablesToPosition.get(i);
				Port firstPair =  pair.getFirst();
				Port secondPair =  pair.getSecond();
				ArrayList<Port> componentPorts = new ArrayList<Port>();
				componentPorts.add(firstPair);

				logger.info("Chosen Component: {}", firstPair.component().getName());
				logger.info("Chosen Port: {}", componentPorts.get(0).id);
				ArrayList<Port> secondComponentPorts = new ArrayList<Port>();
				secondComponentPorts.add(secondPair);

				logger.info("Chosen Component: {}", secondPair.component().getName());
				logger.info("Chosen Port: {}", secondComponentPorts.get(0).id);

				boolean found = false;
				Map<BIPComponent, Iterable<Port>> mergedInteractions = new Hashtable<BIPComponent, Iterable<Port>>();
				ArrayList<Integer> indexOfInteractionsToBeDeleted = new ArrayList<Integer>();
				portsExecuted.add(firstPair);
				portsExecuted.add(secondPair);

				for (Map<BIPComponent, Iterable<Port>> subInteraction : bigInteraction) {
					if (found == false) {
						/*
						 * If both of the ports of the d-variable are already in a subinteraction, dont add the again.
						 */
						if (subInteraction.containsKey(firstPair.component()) && subInteraction.containsKey(secondPair.component())) {
							if (subInteraction.get(firstPair.component()).iterator().next().id.equals(firstPair.id)
									&& subInteraction.get(secondPair.component()).iterator().next().id.equals(secondPair.id)) {
								found = true;
								logger.debug("Double match");
								logger.debug("Merged interactions size: " + mergedInteractions.size());
							}
							/*
							 * If one of the ports (the first port) of the d-variable are already in a subinteraction. Add only the second port.
							 */
						} else if (subInteraction.containsKey( firstPair.component()) && !subInteraction.containsKey(secondPair.component())) {
							if (subInteraction.get( firstPair.component()).iterator().next().id.equals(firstPair.id)) {
								found = true;
								indexOfInteractionsToBeDeleted.add(bigInteraction.indexOf(subInteraction));
								mergedInteractions.putAll(subInteraction);
								mergedInteractions.put(secondPair.component(), secondComponentPorts);
							}
							/*
							 * If one of the ports (the second port) of the d-variable are already in a subinteraction. Add only the first port.
							 */
						} else if (subInteraction.containsKey(secondPair.component()) && !subInteraction.containsKey( firstPair.component())) {
							if (subInteraction.get(secondPair.component()).iterator().next().id.equals(secondPair.id)) {
								found = true;
								indexOfInteractionsToBeDeleted.add(bigInteraction.indexOf(subInteraction));
								mergedInteractions.putAll(subInteraction);
								mergedInteractions.put( firstPair.component(), componentPorts);
							}
						}
					}
				}
				/*
				 * If no merging necessary add both ports
				 */
				if (found == false) {
					oneInteraction.put(firstPair.component(),  componentPorts);
					oneInteraction.put(secondPair.component(), secondComponentPorts);
					(bigInteraction).add(oneInteraction);
				} else {
					logger.debug("indexOfInteractionsToBeDeleted size: " + indexOfInteractionsToBeDeleted.size());
					
					for (Integer index : indexOfInteractionsToBeDeleted) {
						logger.debug("allInteractions size before removing: " + bigInteraction.size());
						bigInteraction.remove(bigInteraction.get(index));
						logger.debug("allInteractions size after removing: " + bigInteraction.size());
					}
					/*
					 * Add the newly created subsets of chosen ports
					 */
					if (mergedInteractions.size() != 0) {
						logger.debug("mergedInteractions size: " + mergedInteractions.size());
						bigInteraction.add(mergedInteractions);
					}
				}
			}
		}
		return bigInteraction;
	}

	/**
	 * BDDBIPEngine informs the BIPCoordinator for the components (and their associated ports) that are part of the same chosen interaction.
	 * 
	 * Through this function all the components need to be notified. If they are participating in an interaction then their port to be fired is sent
	 * to them through the execute function of the BIPExecutor. If they are not participating in an interaction then null is sent to them.
	 * 
	 * @throws BIPEngineException
	 */
	public void executeInteractions(Iterable<Map<BIPComponent, Iterable<Port>>> portGroupsToExecute) throws BIPEngineException {	
		this.count++;
		Iterator<Map<BIPComponent, Iterable<Port>>> oneGroupToExecuteIterator = portGroupsToExecute.iterator();
		Hashtable<Entry<BIPComponent, Port>, Hashtable<String, Object>> requiredDataMapping = new Hashtable<Entry<BIPComponent, Port>, Hashtable<String, Object>>();
		/**
		 * This is a list of components participating in the chosen-by-the-engine interactions. This keeps track of the chosen components in order to
		 * differentiate them from the non chosen ones. Through this function all the components need to be notified. Either by sending null to them
		 * or the port to be fired.
		 */
		ArrayList<BIPComponent> enabledComponents = new ArrayList<BIPComponent>();
		while (oneGroupToExecuteIterator.hasNext()) {
			Map<BIPComponent, Iterable<Port>> oneInteraction = oneGroupToExecuteIterator.next();
			Iterator<BIPComponent> interactionComponents = oneInteraction.keySet().iterator();
			while (interactionComponents.hasNext()) {

				BIPComponent component = interactionComponents.next();
				enabledComponents.add(component);
				Iterator<Port> compPortsToFire = oneInteraction.get(component).iterator();

				logger.debug("For component {} the ports are {}. ", component.getName(), oneInteraction.get(component));
				/*
				 * If the Iterator<Port> is null or empty for a chosen component, throw an exception. This should not happen.
				 */
				if (compPortsToFire == null || !compPortsToFire.hasNext()) {
					logger.error("In a chosen by the engine interaction, associated to component " + component.getName() + " is a null or empty list of ports to be fired.");
					throw new BIPEngineException("Exception in thread: " + Thread.currentThread().getName() + " In a chosen by the engine interaction, associated to component " + component.getName()
							+ " is a null or empty list of ports to be fired.");
				}
				assert (compPortsToFire != null);

				// This loop will be executed at least once
				while (compPortsToFire.hasNext()) {
					Port port = compPortsToFire.next();
					getDataForOnePort(requiredDataMapping, oneInteraction, component, port);
				}
			}
		}

		// this loop is separated from the previous one, because we should first get all the data and execute only afterwards
		for (Map<BIPComponent, Iterable<Port>> combination : portGroupsToExecute) {
			for (BIPComponent component : combination.keySet()) {
				for (Port port : combination.get(component)) {
					component.execute(port.id, getData(requiredDataMapping, component, port));
				}
			}
		}
		/*
		 * Send null to the components that are not part of the overall interaction
		 */
		for (BIPComponent component : registeredComponents) {
			if (!enabledComponents.contains(component)) {
				component.execute(null);
			}
		}
	}

	private Hashtable<Entry<BIPComponent, Port>, Hashtable<String, Object>> getDataForOnePort(Hashtable<Entry<BIPComponent, Port>, Hashtable<String, Object>> requiredDataMapping,
			Map<BIPComponent, Iterable<Port>> oneInteraction, BIPComponent component, Port port) throws BIPEngineException {
		/*
		 * If the port is null or empty for a chosen component, throw an exception. This should not happen.
		 */
		if (port == null || port.id.isEmpty()) {
			logger.error("In a chosen by the engine interaction, associated to component " + component.getName() + " the port to be fired is null or empty.");
			throw new BIPEngineException("Exception in thread: " + Thread.currentThread().getName() + " In a chosen by the engine interaction, associated to component " + component.getName()
					+ " the port to be fired is null or empty.");
		}
		assert (port != null && !port.id.isEmpty());

		/*
		 * Find out which components are sending data to this component
		 */
		Iterable<Data> portToDataInForTransition = componentBehaviourMapping.get(component).portToDataInForTransition(port);
		Hashtable<String, Object> nameToValue = new Hashtable<String, Object>();
		Entry<BIPComponent, Port> key = new AbstractMap.SimpleEntry<BIPComponent, Port>(component, port);
		/*
		 * if there is no data required, put empty values and continue
		 */
		if (portToDataInForTransition == null || !portToDataInForTransition.iterator().hasNext()) {
			requiredDataMapping.put(key, nameToValue);
			return requiredDataMapping;
		}
		for (Data dataItem : portToDataInForTransition) {
			logger.debug("Component {} execute port with inData {}", component.getName(), dataItem.name());
			for (BIPComponent aComponent : oneInteraction.keySet()) {
				if (component.equals(aComponent)) {
					continue;
				}
				String dataOutName = dataIsProvided(aComponent, componentBehaviourMapping.get(component).getComponentType(), dataItem.name(), oneInteraction.get(aComponent));
				if (dataOutName != null && !dataOutName.isEmpty()) {
					Object dataValue = aComponent.getData(dataOutName, dataItem.type());
					logger.debug("GETTING DATA: from component " + aComponent.getName() + " the value " + dataValue);
					nameToValue.put(dataItem.name(), dataValue);
					break;
				}
			}
		}
		logger.debug("Data<->value table: {}", nameToValue);
		requiredDataMapping.put(key, nameToValue);
		return requiredDataMapping;
	}

	private Map<String, ?> getData(Hashtable<Entry<BIPComponent, Port>, Hashtable<String, Object>> requiredDataMapping, BIPComponent component, Port port) {
		for (Entry<BIPComponent, Port> entry : requiredDataMapping.keySet()) {
			if (component.equals(entry.getKey()) && port.equals(entry.getValue())) {
				return requiredDataMapping.get(entry);
			}
		}
		return null;
	}

	private String dataIsProvided(BIPComponent providingComponent, String requiringComponentType, String dataName, Iterable<Port> port) {
		for (DataWire wire : this.componentDataWires.get(requiringComponentType).get(dataName)) {
			if (wire.from.specType.equals(componentBehaviourMapping.get(providingComponent).getComponentType())) {
				Set<Port> portsProviding = componentBehaviourMapping.get(providingComponent).getDataProvidingPorts(wire.from.id);
				for (Port p : port) {
					for (Port inport : portsProviding) {
						if (inport.id.equals(p.id) && inport.specType.equals(p.specType)) {
							return wire.from.id;
						}
					}
				}
			}
		}
		return null;
	}

	public void start() {
		bipCoordinator.start();
	}

	public void stop() {
		bipCoordinator.stop();
	}

	public void execute() {
		if (this.interactionExecutor == null){
			setInteractionExecutor(this);
		}
		bipCoordinator.execute();
	}

	private void doInformSpecific(BIPComponent component) throws BIPEngineException {
		// mapping port <-> data it needs for computing guards
		Behaviour decidingBehaviour = componentBehaviourMapping.get(component);
		// for each undecided port of each component :
		for (Port port : componentUndecidedPorts.get(component)) {
			// get list of DataIn needed for its guards
			Set<Data> dataIn = decidingBehaviour.portToDataInForGuard(port);

			if (dataIn.isEmpty()) {
				// if the data is empty, then the port is enabled. just send it.
				this.informSpecific(component, port, new HashMap<BIPComponent, Set<Port>>());
				continue;
			}
			// for each data its different evaluations
			Hashtable<String, ArrayList<Object>> dataEvaluation = new Hashtable<String, ArrayList<Object>>();
			// list of data structures build upon receiving the data value
			ArrayList<DataContainer> dataList = new ArrayList<DataContainer>();
			// for each DataIn variable get info which components provide it as
			// their outData
			for (Data inDataItem : dataIn) {
				// for each wire that is incoming for this data of this component (as precomputed)
				for (DataWire wire : this.componentDataWires.get(decidingBehaviour.getComponentType()).get(inDataItem.name())) {

					// for this dataVariable: all the values that it can take
					ArrayList<Object> dataValues = new ArrayList<Object>();
					// for each component of this type, call getData
					for (BIPComponent aComponent : getBIPComponentInstances(wire.from.specType)) {
						// TODO check data is available
						Object inValue = aComponent.getData(wire.from.id, inDataItem.type());
						// get data out variable in order to get the ports
						Set<Port> providingPorts = componentBehaviourMapping.get(aComponent).getDataProvidingPorts(wire.from.id);
						dataList.add(new DataContainer(inDataItem, inValue, aComponent, providingPorts));
						dataValues.add(inValue);

					}
					dataEvaluation.put(inDataItem.name(), dataValues);
				}
			}

			ArrayList<ArrayList<DataContainer>> containerList = getDataValueTable(dataList);
			print(containerList, component);
			ArrayList<Map<String, Object>> dataTable = createDataTable(containerList);

			// the result provided must have the same order - put comment
			// containerList and portActive are dependant on each other
			// TODO change getEnabledness: if data null, return false
			List<Boolean> portActive = component.checkEnabledness(port, dataTable);
			logger.debug("The result of checkEndabledness for component {}: {}.", component.getName(), portActive);
			HashMap<BIPComponent, Set<Port>> disabledCombinations = new HashMap<BIPComponent, Set<Port>>();
			for (int i = 0; i < portActive.size(); i++) {
				disabledCombinations.clear();
				if (!(portActive.get(i))) {
					ArrayList<DataContainer> dataContainer = containerList.get(i);
					for (DataContainer dc : dataContainer) {
						logger.debug(this.count + " CONTAINER CHOSEN: For deciding " + component.hashCode() + " and " + port.id + " disabled is " + dc.component().hashCode() + " with ports "
								+ dc.ports());
						disabledCombinations.put(dc.component(), dc.ports());
					}
				}
				this.informSpecific(component, port, disabledCombinations);
			}
		}
	}

	private void print(ArrayList<ArrayList<DataContainer>> containerList, BIPComponent component) {
		for (ArrayList<DataContainer> dataList : containerList) {
			for (DataContainer container : dataList) {
				logger.debug(this.count + " Deciding " + component.hashCode() + ", Providing " + container.component().hashCode() + " the value " + container.value());
			}
		}

	}

	private ArrayList<Map<String, Object>> createDataTable(ArrayList<ArrayList<DataContainer>> containerList) {
		ArrayList<Map<String, Object>> dataTable = new ArrayList<Map<String, Object>>();
		for (ArrayList<DataContainer> container : containerList) {
			Map<String, Object> row = new Hashtable<String, Object>();
			for (DataContainer dc : container) {
				row.put(dc.name(), dc.value());
			}
			dataTable.add(row);
		}
		return dataTable;
	}

	private ArrayList<ArrayList<DataContainer>> getDataValueTable(ArrayList<DataContainer> dataList) {
		ArrayList<ArrayList<DataContainer>> result = new ArrayList<ArrayList<DataContainer>>();

		if (dataList == null || dataList.isEmpty()) {
			// throw exception
			return null;
		}
		ArrayList<ArrayList<DataContainer>> sortedList = getListList(dataList);

		// for one bipData get iterator over its values
		ArrayList<DataContainer> entry = sortedList.get(0);
		Iterator<DataContainer> iterator = entry.iterator();

		// for each value of this first bipData
		while (iterator.hasNext()) {
			// create one map, where
			// all the different pairs name<->value will be stored
			// put there the current value of the first bipData
			ArrayList<DataContainer> dataRow = new ArrayList<DataContainer>();
			dataRow.add(iterator.next());

			// remove the current data from the initial data table
			// so that it is not treated again further
			sortedList.remove(entry);
			// treat the other bipData variables
			result.addAll(getNextTableRow(sortedList, dataRow));
			// restore the current data
			sortedList.add(entry);
		}
		return result;
	}

	private ArrayList<ArrayList<DataContainer>> getNextTableRow(ArrayList<ArrayList<DataContainer>> sortedList, ArrayList<DataContainer> dataRow) {
		ArrayList<ArrayList<DataContainer>> result = new ArrayList<ArrayList<DataContainer>>();
		// if there is no more data left, it means we have constructed one map
		// of all the bipData variables
		if (sortedList == null || sortedList.isEmpty()) {
			result.add(dataRow);
			return result;
		}

		// for one bipData get iterator over its values
		ArrayList<DataContainer> entry = sortedList.iterator().next();
		Iterator<DataContainer> iterator = entry.iterator();

		// for each value of this bipData
		while (iterator.hasNext()) {
			// create a new map, where
			// all the different pairs name<->value will be stored
			// copy there all the previous values
			// (this must be done to escape
			// change of one variable that leads to change of all its copies
			ArrayList<DataContainer> thisRow = new ArrayList<DataContainer>();
			thisRow.addAll(dataRow);
			// put there the current value of the bipData
			thisRow.add(iterator.next());

			// remove the current data from the initial data table
			// so that it is not treated again further
			sortedList.remove(entry);
			// treat the other bipData variables
			result.addAll(getNextTableRow(sortedList, thisRow));
			// restore the current data
			sortedList.add(entry);
		}
		return result;
	}

	private ArrayList<ArrayList<DataContainer>> getListList(ArrayList<DataContainer> list) {
		ArrayList<ArrayList<DataContainer>> result = new ArrayList<ArrayList<DataContainer>>();

		while (!list.isEmpty()) {
			ArrayList<DataContainer> oneDataList = new ArrayList<DataContainer>();
			DataContainer data = list.get(0);
			oneDataList.add(data);
			list.remove(data);
			for (DataContainer d : list) {
				if (d.name().equals(data.name())) {
					oneDataList.add(d);
				}
			}
			list.removeAll(oneDataList);
			result.add(oneDataList);
		}
		return result;
	}

	private ArrayList<Port> getUndecidedPorts(BIPComponent component, String currentState, Set<Port> disabledPorts) {
		ArrayList<Port> undecidedPorts = new ArrayList<Port>();
		Behaviour behaviour = componentBehaviourMapping.get(component);
		boolean portIsDisabled = false;
		// for each port that we have
		Set<Port> currentPorts = behaviour.getStateToPorts().get(currentState);
		for (Port port : currentPorts) {
			// TODO rewrite with sets after fixing port equals
			for (Port disabledPort : disabledPorts) {
				// if it is equal to one of the disabled ports, we mark it as
				// disabled and do not add to the collection of undecided
				if (port.id.equals(disabledPort.id)) {
					portIsDisabled = true;
					break;
				}
			}
			if (!portIsDisabled) {
				undecidedPorts.add(port);
			}
			portIsDisabled = false;
		}
		logger.debug("For component {} the undecided ports are {}. " + component.getName(), undecidedPorts);
		return undecidedPorts;
	}

	/**
	 * Helper function that returns the registered component instances that correspond to a component type.
	 * 
	 * @throws BIPEngineException
	 */
	public List<BIPComponent> getBIPComponentInstances(String type) throws BIPEngineException {
		ArrayList<BIPComponent> instances = typeInstancesMapping.get(type);
		if (instances == null) {
			try {
				logger.error("No registered component instances for the: {} ", type
						+ " component type. Possible reasons: The name of the component instances was specified in another way at registration.");
				throw new BIPEngineException("Exception in thread " + Thread.currentThread().getName() + " No registered component instances for the component type: " + type
						+ "Possible reasons: The name of the component instances was specified in another way at registration.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		return instances;
	}

	/**
	 * Helper function that given a component returns the corresponding behaviour as a Behaviour Object.
	 */
	public Behaviour getBehaviourByComponent(BIPComponent component) {
		return componentBehaviourMapping.get(component);
	}

	/**
	 * Helper function that returns the total number of ports of the registered components.
	 */
	public int getNoPorts() {
		return nbPorts;
	}

	/**
	 * Helper function that returns the total number of states of the registered components.
	 */
	public int getNoStates() {
		return nbStates;
	}

	public int getNoComponents() {
		return bipCoordinator.getNoComponents();
	}

	public void execute(byte[] valuation) throws BIPEngineException {
		if (interactionExecutor != this) {
			interactionExecutor.execute(valuation);
		}
		else {
			executeInteractions(preparePorts(valuation));		
		}
	}

	private Iterable<Map<BIPComponent, Iterable<Port>>> preparePorts(byte[] valuation) {
		/*
		 * Grouping the interaction into smaller ones (with respect to data transfer)
		 */
		List<Map<BIPComponent, Iterable<Port>>> bigInteraction = new ArrayList<Map<BIPComponent, Iterable<Port>>>();
		Map<BIPComponent, Iterable<Port>> chosenPorts = new Hashtable<BIPComponent, Iterable<Port>>();
		ArrayList<BIPComponent> chosenComponents = new ArrayList<BIPComponent>();

		logger.debug("positionsOfDVariables size: " + positionsOfDVariables.size());

		ArrayList<Port> portsExecuted = new ArrayList<Port>();
		bigInteraction=mergingSubInteractions(valuation, portsExecuted);

		/*
		 * Find ports that participate in the interaction but not in data transfer
		 * and add them as a separate group
		 */
		Map<Port, Integer> portToPosition = bipCoordinator.getBehaviourEncoderInstance().getPortToPosition();
		ArrayList <BIPComponent> componentsEnum =  registeredComponents; 
		for (BIPComponent component: componentsEnum) {
			logger.debug("Component: " + component.getName());

			Iterable<Port> componentPorts = getBehaviourByComponent(component).getEnforceablePorts();
			if (componentPorts == null || !componentPorts.iterator().hasNext()) {
				logger.warn("Component {} does not have any enforceable ports.", component.getName());
			}
			ArrayList<Port> enabledPorts = new ArrayList<Port>();

			for (Port componentPort : componentPorts) {
				if (!portsExecuted.contains(componentPort) && valuation[portToPosition.get(componentPort)] == 1) {
					enabledPorts.add(componentPort);
				}
			}
			if (!enabledPorts.isEmpty()) {
				logger.info("Chosen Component: {}", component.getName());
				logger.info("Chosen Port: {}", enabledPorts.get(0).id);
			}
			if (enabledPorts.size() != 0) {
				chosenPorts.put(component, enabledPorts);
				chosenComponents.add(component);
			}
		}

		logger.info("*************************************************************************");
		logger.info("chosenPorts size: " + chosenPorts.size());
		if (chosenPorts.size() != 0) {
			(bigInteraction).add(chosenPorts);
		}

		/*
		 * Here the ports mentioned above have been added
		 */
		
		for (Map<BIPComponent, Iterable<Port>> inter : bigInteraction) {
			for (Map.Entry<BIPComponent, Iterable<Port>> e : inter.entrySet()) {
				logger.debug("ENGINE ENTRY: " + e.getKey().hashCode() + " - " + e.getValue());
			}
		}
		logger.debug("Interactions: "+bigInteraction.size());

		return bigInteraction;
	}

	public void setInteractionExecutor(InteractionExecutor interactionExecutor) {
		this.interactionExecutor = interactionExecutor;
		
	}

	public void specifyTemporaryConstraints(BDD constraints) {
		bipCoordinator.specifyTemporaryConstraints(constraints);
		
	}

	public BehaviourEncoder getBehaviourEncoderInstance() {
		return bipCoordinator.getBehaviourEncoderInstance();
	}

	
	public BDDFactory getBDDManager() {
		return bipCoordinator.getBDDManager();
	}

	public void specifyPermanentConstraints(BDD constraints) {
		bipCoordinator.specifyPermanentConstraints(constraints);
	}

	public DataEncoder getDataEncoder() {
		return dataEncoder;
	}
	
	/**
	 * @return the dVariablesToPosition
	 */
	public Map<Integer, BiDirectionalPair> getdVariablesToPosition() {
		return dVariablesToPosition;
	}
	

	/**
	 * @return the positionsOfDVariables
	 */
	public List<Integer> getPositionsOfDVariables() {
		return positionsOfDVariables;
	}
	
	/**
	 * @param dVariablesToPosition
	 *            the dVariablesToPosition to set
	 */
	public void setdVariablesToPosition(Map<Integer, BiDirectionalPair> dVariablesToPosition) {
		this.dVariablesToPosition = dVariablesToPosition;
	}


	/**
	 * @param positionsOfDVariables
	 *            the positionsOfDVariables to set
	 */
	public void setPositionsOfDVariables(List<Integer> positionsOfDVariables) {
		this.positionsOfDVariables =  positionsOfDVariables;
	}

}
