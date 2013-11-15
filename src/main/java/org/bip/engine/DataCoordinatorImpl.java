package org.bip.engine;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.api.Behaviour;
import org.bip.behaviour.Data;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;
import org.bip.glue.BIPGlue;
import org.bip.glue.DataWire;
import org.bip.glue.Requires;
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

public class DataCoordinatorImpl implements BIPEngine, InteractionExecutor, Runnable, DataCoordinator {

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
	private BIPCoordinator BIPCoordinator = new BIPCoordinatorImpl();

	private ArrayList<Requires> requires;

	private boolean registrationFinished = false;

	public DataCoordinatorImpl() {
		BIPCoordinator.setInteractionExecutor(this);
		dataEncoder.setDataCoordinator(this);
		dataEncoder.setBehaviourEncoder(BIPCoordinator.getBehaviourEncoderInstance());
		dataEncoder.setEngine(BIPCoordinator.getBDDBIPEngineInstance());
	}

	/**
	 * Sends interactions-glue to the BIP Coordinator Sends data-glue to the Data Encoder.
	 */
	public synchronized void specifyGlue(BIPGlue glue) {
		BIPCoordinator.specifyGlue(glue);
		this.dataWires = glue.dataWires;
		this.requires = glue.requiresConstraints;
		if (dataWires.isEmpty() || dataWires == null) {
			logger.error("Data wires information not specified in XML file, although DataCoordinator is set as the wrapper");
			try {
				throw new BIPEngineException("Data wires information not specified in XML file, although DataCoordinator is set as the wrapper");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}
		} else {
			try {
				BIPCoordinator.specifyDataGlue(dataEncoder.specifyDataGlue(dataWires));

				// NEW
				// BIPCoordinator.specifyDataGlue(dataEncoder.specifyDataGlue(componentBehaviourMapping, glue));
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}
		}
		registrationFinished = true;
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
				;
				nbStates += ((ArrayList<String>) behaviour.getStates()).size();
				BIPCoordinator.register(component, behaviour);
			}
			ArrayList<BIPComponent> componentInstances = new ArrayList<BIPComponent>();

			/*
			 * If this component type already exists in the hashtable, update the ArrayList of BIPComponents that corresponds to this component type.
			 */
			if (typeInstancesMapping.containsKey(component.getName())) {
				componentInstances.addAll(typeInstancesMapping.get(component.getName()));
			}

			componentInstances.add(component);
			typeInstancesMapping.put(component.getName(), componentInstances);
		} catch (BIPEngineException e) {
			e.printStackTrace();
		}
	}

	public synchronized void inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) {
		// for each component store its undecided ports
		componentUndecidedPorts.put(component, getUndecidedPorts(component, currentState, disabledPorts));
		// easy implementation: when all the components have informed
		// TODO the data wiring process does not need all the components having
		// informed
		while (!registrationFinished) {
			;
		}
		//TODO replace by semaphore
		try {
			doInformSpecific(component);
		} catch (BIPEngineException e) {
			e.printStackTrace();
		}
		// TODO: Inform the BIPCoordinator only after all the informSpecifics
		// for the particular component have finished
		BIPCoordinator.inform(component, currentState, disabledPorts);
	}

	public synchronized void informSpecific(BIPComponent decidingComponent, Port decidingPort, Map<BIPComponent, Set<Port>> disabledCombinations) throws BIPEngineException {
		if (disabledCombinations == null) {
			return;
		} else if (disabledCombinations.isEmpty()) {
			logger.warn("No disabled combinations specified in informSpecific. Map of disabledCombinations is empty.");
			return;
		} else if (!registeredComponents.contains(decidingComponent)) {
			try {
				logger.error("Deciding component specified in informSpecific is not in the list of registered components");
				throw new BIPEngineException("Deciding component specified in informSpecific is not in the list of registered components");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}
		} else {
			ArrayList<Port> componentPorts = (ArrayList<Port>) componentBehaviourMapping.get(decidingComponent).getEnforceablePorts();
			if (!componentPorts.contains(decidingPort)) {
				try {
					logger.error("Deciding port {} in informSpecific is not specified in the behaviour of the deciding component {}.", decidingPort, decidingComponent.getName());
					throw new BIPEngineException("Deciding port in informSpecific is not specified in the behaviour of the deciding component.");
				} catch (BIPEngineException e) {
					e.printStackTrace();
				}

			} else {
				Iterator<BIPComponent> disabledComponents = disabledCombinations.keySet().iterator();
				while (disabledComponents.hasNext()) {
					BIPComponent component = disabledComponents.next();
					if (!registeredComponents.contains(component)) {
						logger.error("Component " + component.getName() + " specified in the disabledCombinations of informSpecific was not registered.");
						throw new BIPEngineException("Component " + component.getName() + " specified in the disabledCombinations of informSpecific was not registered.");
					}
				}
				BIPCoordinator.informSpecific(dataEncoder.informSpecific(decidingComponent, decidingPort, disabledCombinations));

			}
		}
	}

	/**
	 * BDDBIPEngine informs the BIPCoordinator for the components (and their associated ports) that are part of the same chosen interaction.
	 * 
	 * Through this function all the components need to be notified. If they are participating in an interaction then their port to be fired is sent
	 * to them through the execute function of the BIPExecutor. If they are not participating in an interaction then null is sent to them.
	 * 
	 * @throws BIPEngineException
	 */
	public void executeInteractions(Iterable<Map<BIPComponent, Iterable<Port>>> portsToFire) throws BIPEngineException {
		this.count++;
		Iterator<Map<BIPComponent, Iterable<Port>>> interactionsToFire = portsToFire.iterator();
		Hashtable<Entry<BIPComponent, Port>, Hashtable<String, Object>> requiredDataMapping = new Hashtable<Entry<BIPComponent, Port>, Hashtable<String, Object>>();

		/**
		 * This is a list of components participating in the chosen-by-the-engine interactions. This keeps track of the chosen components in order to
		 * differentiate them from the non chosen ones. Through this function all the components need to be notified. Either by sending null to them
		 * or the port to be fired.
		 */
		ArrayList<BIPComponent> enabledComponents = new ArrayList<BIPComponent>();
		while (interactionsToFire.hasNext()) {
			Map<BIPComponent, Iterable<Port>> oneInteraction = interactionsToFire.next();
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
					try {
						logger.error("In a chosen by the engine interaction, associated to component " + component.getName() + " is a null or empty list of ports to be fired.");
						throw new BIPEngineException("Exception in thread: " + Thread.currentThread().getName() + " In a chosen by the engine interaction, associated to component "
								+ component.getName() + " is a null or empty list of ports to be fired.");
					} catch (BIPEngineException e) {
						e.printStackTrace();
						throw e;
					}
				} else {
					while (compPortsToFire.hasNext()) {
						Port port = compPortsToFire.next();
						/*
						 * If the port is null or empty for a chosen component, throw an exception. This should not happen.
						 */
						if (port == null || port.id.isEmpty()) {
							try {
								logger.error("In a chosen by the engine interaction, associated to component " + component.getName() + " the port to be fired is null or empty.");
								throw new BIPEngineException("Exception in thread: " + Thread.currentThread().getName() + " In a chosen by the engine interaction, associated to component "
										+ component.getName() + " the port to be fired is null or empty.");
							} catch (BIPEngineException e) {
								e.printStackTrace();
								throw e;
							}
						}
						// Find out which components are sending data to this component
						Iterable<Data> portToDataInForTransition = componentBehaviourMapping.get(component).portToDataInForTransition(port);
						// System.err.println("Data on transition " + port.id + ": " + portToDataInForTransition);
						Hashtable<String, Object> nameToValue = new Hashtable<String, Object>();
						Entry<BIPComponent, Port> key = new AbstractMap.SimpleEntry<BIPComponent, Port>(component, port);
						// if there is no data required, put empty values and continue
						if (portToDataInForTransition == null || !portToDataInForTransition.iterator().hasNext()) {
							requiredDataMapping.put(key, nameToValue);
							continue;
						}
						for (Data dataItem : portToDataInForTransition) {
							logger.info("Component {} execute port with inData {}", component.getName(), dataItem.name());
							for (BIPComponent aComponent : oneInteraction.keySet()) {
								if (component.equals(aComponent)) {
									continue;
								}
								String dataOutName = dataIsProvided(aComponent, component, dataItem.name(), oneInteraction.get(aComponent));

								if (dataOutName != null && !dataOutName.isEmpty()) {
									Object dataValue = aComponent.getData(dataOutName, dataItem.type());
									logger.info("GETTING DATA: from component " + aComponent.getName() + " the value " + dataValue);
									nameToValue.put(dataItem.name(), dataValue);
									break;
								}
							}
						}
						logger.debug("Data<->value table: {}", nameToValue);

						requiredDataMapping.put(key, nameToValue);
					}
				}
			}
		}
		//TODO merge this loop with the upper one
		for (Map<BIPComponent, Iterable<Port>> combination : portsToFire) {
			for (Entry<BIPComponent, Iterable<Port>> combinationEntry : combination.entrySet()) {
				BIPComponent component = combinationEntry.getKey();
				for (Port port : combinationEntry.getValue()) {
					component.execute(port.id, getData(requiredDataMapping, component, port));
				}
			}
		}
		/*
		 * send null to the components that are not part of the overall interaction
		 */
		for (BIPComponent component : registeredComponents) {
			if (!enabledComponents.contains(component)) {
				component.execute(null);
			}
		}
	}

	private Map<String, ?> getData(Hashtable<Entry<BIPComponent, Port>, Hashtable<String, Object>> requiredDataMapping, BIPComponent component, Port port) {
		for (Entry<BIPComponent, Port> entry : requiredDataMapping.keySet()) {
			if (component.equals(entry.getKey()) && port.equals(entry.getValue())) {
				return requiredDataMapping.get(entry);
			}
		}
		return null;
	}

	private String dataIsProvided(BIPComponent providingComponent, BIPComponent requiringComponent, String dataName, Iterable<Port> port) {
		for (DataWire wire : this.dataWires) {
			if (wire.isIncoming(dataName, componentBehaviourMapping.get(requiringComponent).getComponentType())
					&& wire.from.specType.equals(componentBehaviourMapping.get(providingComponent).getComponentType())) {
				Set<Port> portsProviding =  componentBehaviourMapping.get(providingComponent).getDataOut(wire.from.id).allowedPorts();
				for (Port p : port) {
					for (Port inport : portsProviding) {
						if (inport.id.equals(p.id) && inport.specType.equals(p.specType)) {
							return wire.from.id;
						}
					}
				}
			}
		}
		return "";
	}

	public void run() {
		// TODO: unregister components and notify the component that the engine
		// is not working
		// for (BIPComponent component : identityMapping.values()) {
		// component.deregister();
		// }
		componentBehaviourMapping.clear();
		return;
	}

	public void start() {
		BIPCoordinator.start();
	}

	public void stop() {
		BIPCoordinator.stop();
	}

	public void execute() {
		BIPCoordinator.execute();
	}

	private void doInformSpecific(BIPComponent component) throws BIPEngineException {
		// mapping port <-> data it needs for computing guards
		Map<Port, Set<Data>> portToDataInNeededByGuards = componentBehaviourMapping.get(component).portToDataInForGuard();
		// for each undecided port of each component :
		for (Port port : componentUndecidedPorts.get(component)) {
			// get list of DataIn needed for its guards
			Iterable<Data> dataIn = portToDataInNeededByGuards.get(port);

			if (!dataIn.iterator().hasNext()) {
				// if the data is empty, then the port is enabled. just send it.
				this.informSpecific(component, port, new HashMap<BIPComponent, Set<Port>>());
				continue;
			}
			// for each data its different evaluations
			Hashtable<String, ArrayList<Object>> dataEvaluation = new Hashtable<String, ArrayList<Object>>();
			// list of data structures build upon receiving the data value
			ArrayList<DataContainer> dataList = new ArrayList<DataContainer>();
			// for each DataIn variable get info which components provide it as their outData
			for (Data inDataItem : dataIn) {
				// mapping dataValue - components giving this value
				//Hashtable<Object, ArrayList<BIPComponent>> map = new Hashtable<Object, ArrayList<BIPComponent>>();
				for (DataWire wire : this.dataWires) {
					// for this dataVariable: all the values that it can take
					ArrayList<Object> dataValues = new ArrayList<Object>();
					if (wire.isIncoming(inDataItem.name(), componentBehaviourMapping.get(component).getComponentType())) {
						// for each component of this type, call getData
						for (BIPComponent aComponent : getBIPComponentInstances(wire.from.specType)) {
							// for now, it is not allowed to have data from itself
							// without this condition there are some disturbances in the engine
							if (component.equals(aComponent)) {
								continue;
							}
							// TODO check data is available
							Object inValue = aComponent.getData(wire.from.id, inDataItem.type());
							// get data out variable in order to get the ports
							Data dataOut = componentBehaviourMapping.get(aComponent).getDataOut(wire.from.id);
							// if the allowed ports of the given data out do not contain our port, do not add this data
//							if (!(dataOut.allowedPorts().contains(port))) {
//								continue;
//							}
							dataList.add(new DataContainer(inDataItem, inValue, aComponent, dataOut.allowedPorts()));
							dataValues.add(inValue);

							//ArrayList<BIPComponent> componentList = new ArrayList<BIPComponent>();
//							if (map.containsKey(inValue)) {
//								componentList = map.get(inValue);
//							} else {
//								map.put(inValue, componentList);
//							}
//							componentList.add(aComponent);
						}
					}
					dataEvaluation.put(inDataItem.name(), dataValues);
				}
			}

			ArrayList<ArrayList<DataContainer>> containerList = getDataValueTable(dataList);
			print(containerList, component);
			ArrayList<Map<String, Object>> dataTable = createDataTable(containerList);

			// the result provided must have the same order - put comment
			//containerList and portActive are dependant on each other
			// TODO change getEnabledness: if data null, return false
			ArrayList<Boolean> portActive = (ArrayList<Boolean>) component.checkEnabledness(port, dataTable);
			logger.debug("The result of checkEndabledness for component {}: {}.", component.getName(), portActive);
			HashMap<BIPComponent, Set<Port>> disabledCombinations = new HashMap<BIPComponent, Set<Port>>();
			for (int i = 0; i < portActive.size(); i++) {
				disabledCombinations.clear();
				if (!(portActive.get(i))) {
					ArrayList<DataContainer> dataContainer = containerList.get(i);
					for (DataContainer dc : dataContainer) {
						logger.info(this.count + " CONTAINER CHOSEN: For deciding " + component.hashCode() + " and " + port.id + " disabled is " + dc.component().hashCode() + " with ports "
								+ dc.ports());
						disabledCombinations.put(dc.component(), dc.ports());
					}
				}
				// if (component.getName().equals("org.bip.spec.hanoi.RightHanoiPeg"))
				// {} else
				this.informSpecific(component, port, disabledCombinations);
			}

		}
	}
	

	private void print(ArrayList<ArrayList<DataContainer>> containerList, BIPComponent component) {
		for (ArrayList<DataContainer> dataList : containerList) {
			for (DataContainer container : dataList) {
				logger.info(this.count + " Deciding " + component.hashCode() + ", Providing " + container.component().hashCode() + " the value " + container.value());
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
			// dataEvaluation.put(keyCopy, valuesCopy);
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
			// String keyCopy = entry.getKey();
			sortedList.remove(entry);
			// ArrayList<Object> valuesCopy = dataEvaluation.remove(keyCopy);
			// treat the other bipData variables
			result.addAll(getNextTableRow(sortedList, thisRow));
			// restore the current data
			sortedList.add(entry);
			// dataEvaluation.put(keyCopy, valuesCopy);
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

	/**
	 * This function takes the structure build from data received from the executors and changes it to the structure with which the executor can be
	 * questioned vie checkEnabledness method
	 * 
	 * @param dataEvaluation
	 *            Table bipData <-> possible evaluations
	 * @return List of all possible entries, where each entry consists of the same number of pairs bipData <-> value
	 */
	private Iterable<Map<String, Object>> getDataValueTable(Hashtable<String, ArrayList<Object>> dataEvaluation) {
		ArrayList<Map<String, Object>> result = new ArrayList<Map<String, Object>>();

		if (dataEvaluation == null || dataEvaluation.entrySet().isEmpty()) {
			// throw exception
			return null;
		}

		// for one bipData get iterator over its values
		Entry<String, ArrayList<Object>> entry = dataEvaluation.entrySet().iterator().next();
		Iterator<Object> iterator = entry.getValue().iterator();

		// for each value of this first bipData
		while (iterator.hasNext()) {
			// create one map, where
			// all the different pairs name<->value will be stored
			// put there the current value of the first bipData
			Hashtable<String, Object> dataRow = new Hashtable<String, Object>();
			String keyCopy = entry.getKey();
			dataRow.put(keyCopy, iterator.next());

			// remove the current data from the initial data table
			// so that it is not treated again further
			ArrayList<Object> valuesCopy = dataEvaluation.remove(keyCopy);
			// treat the other bipData variables
			result.addAll(getNextTableRow(dataEvaluation, dataRow));
			// restore the current data
			dataEvaluation.put(keyCopy, valuesCopy);
		}
		return result;
	}

	/**
	 * This function helps the function getDataValueTable to transform the structure for the checkEnabledness method
	 * 
	 * @param dataEvaluation
	 *            Table bipData <-> possible evaluations
	 * @param dataRow
	 *            The current row of bipData <-> value pairs already treated that needs to be augmented
	 * @return List of possible entries, where each entry consists of the a number of pairs bipData <-> value
	 */
	private Collection<Map<String, Object>> getNextTableRow(Hashtable<String, ArrayList<Object>> dataEvaluation, Map<String, Object> dataRow) {
		ArrayList<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		// if there is no more data left, it means we have constructed one map
		// of all the bipData variables
		if (dataEvaluation == null || dataEvaluation.entrySet().isEmpty()) {
			result.add(dataRow);
			return result;
		}

		// for one bipData get iterator over its values
		Entry<String, ArrayList<Object>> entry = dataEvaluation.entrySet().iterator().next();
		Iterator<Object> iterator = entry.getValue().iterator();

		// for each value of this bipData
		while (iterator.hasNext()) {
			// create a new map, where
			// all the different pairs name<->value will be stored
			// copy there all the previous values
			// (this must be done to escape
			// change of one variable that leads to change of all its copies
			Map<String, Object> thisRow = new Hashtable<String, Object>();
			thisRow.putAll(dataRow);
			// put there the current value of the bipData
			thisRow.put(entry.getKey(), iterator.next());

			// remove the current data from the initial data table
			// so that it is not treated again further
			String keyCopy = entry.getKey();
			ArrayList<Object> valuesCopy = dataEvaluation.remove(keyCopy);
			// treat the other bipData variables
			result.addAll(getNextTableRow(dataEvaluation, thisRow));
			// restore the current data
			dataEvaluation.put(keyCopy, valuesCopy);
		}
		return result;
	}

	private ArrayList<Port> getUndecidedPorts(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) {
		ArrayList<Port> undecidedPorts = new ArrayList<Port>();
		Behaviour behaviour = componentBehaviourMapping.get(component);
		boolean portIsDisabled = false;
		// for each port that we have
		Set<Port> currentPorts = behaviour.getStateToPorts().get(currentState);
		for (Port port : currentPorts) {
			//TODO rewrite with sets after fixing port equals
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
	 * For this component, find out which ports of it provide this dataOut Independent of the instance of component
	 * 
	 * @param disabledComponent
	 * @param dataOut
	 * @return
	 */
	public Iterable<Port> getDataOutPorts(BIPComponent disabledComponent, Port decidingPort) {
		ArrayList<Port> dataOutPorts = new ArrayList<Port>();
		ArrayList<Requires> requires = this.requires;
		logger.info("Requires size: " + requires.size());
		logger.info("deciding Port: " + decidingPort.id + "of deciding Component: " + decidingPort.specType);
		logger.info("disabledComponent: " + disabledComponent.getName());
		for (Requires oneRequireRule : requires) {
			if (oneRequireRule.effect.id.equals(decidingPort.id) && oneRequireRule.effect.specType.equals(decidingPort.specType)) {
				logger.info("One Require rule found");
				List<List<Port>> requireRuleCauses = oneRequireRule.causes;
				for (List<Port> orPorts : requireRuleCauses) {
					for (Port port : orPorts) {
						if (port.specType.equals(disabledComponent.getName())) {
							dataOutPorts.add(port);
						}
					}
				}
			}
		}
		return dataOutPorts;
	}

	/**
	 * Helper function that returns the registered component instances that correspond to a component type.
	 * 
	 * @throws BIPEngineException
	 */
	public Iterable<BIPComponent> getBIPComponentInstances(String type) throws BIPEngineException {
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

}
