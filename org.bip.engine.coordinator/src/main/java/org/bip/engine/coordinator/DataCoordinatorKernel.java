package org.bip.engine.coordinator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPActor;
import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.api.BIPGlue;
import org.bip.api.Behaviour;
import org.bip.api.Data;
import org.bip.api.DataWire;
import org.bip.api.Port;
import org.bip.engine.api.BIPCoordinator;
import org.bip.engine.api.BehaviourEncoder;
import org.bip.engine.api.DataCoordinator;
import org.bip.engine.api.DataEncoder;
import org.bip.engine.api.InteractionExecutor;
import org.bip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * There is no need for DataCoordinator interface, just DataCoordinatorImpl will implement BIP
 * engine interface. DataCoordinatorImpl needs BIP coordinator ( BIP Engine again ) that actual does
 * all the computation of BIP engine. DataCoordinatorImpl takes care of only of data querying and
 * passing to BIP executors.
 * 
 * DataCoordinator intercepts call register and inform from BIPExecutor.
 * 
 * @authors: mavridou, zolotukhina
 */
public class DataCoordinatorKernel implements BIPEngine, InteractionExecutor, DataCoordinator {

	// private volatile Boolean isBIPCoordinatorStopping = false;

	/** The logger. */
	private Logger logger = LoggerFactory.getLogger(DataCoordinatorKernel.class);

	/** The registered components. */
	private ArrayList<BIPComponent> registeredComponents = new ArrayList<BIPComponent>();

	/**
	 * Helper hashtable with integers representing the local identities of registered components as
	 * the keys and the Behaviours of these components as the values.
	 */
	private Hashtable<BIPComponent, Behaviour> componentBehaviourMapping = new Hashtable<BIPComponent, Behaviour>();

	/**
	 * Helper hashset of the components that have informed in an execution cycle.
	 */
	private ArrayList<BIPComponent> informedComponents = new ArrayList<BIPComponent>();
	private ArrayList<String> informedComponentsState = new ArrayList<String>();
	private ArrayList<Set<Port>> informedComponentsPorts = new ArrayList<Set<Port>>();

	/**
	 * Helper hashtable with strings as keys representing the component type of the registered
	 * components and ArrayList of BIPComponent instances that correspond to the component type
	 * specified in the key.
	 */
	private Hashtable<String, ArrayList<BIPComponent>> typeInstancesMapping = new Hashtable<String, ArrayList<BIPComponent>>();

	/** The data wires. */
	private List<DataWire> dataWires;

	/** Number of ports of components registered. */
	private int nbPorts;

	/** Number of states of components registered. */
	private int nbStates;

	/** The count. */
	private int count;

	/** Create instances of all the the Data Encoder and of the BIPCoordinator. */
	private DataEncoder dataEncoder;

	/** The bip coordinator. */
	private BIPCoordinator bipCoordinator = null;

	/** The registration finished. */
	private boolean registrationFinished = false;

	/** The component data wires. */
	private Map<String, Map<String, Set<DataWire>>> componentDataWires;

	/** The interaction executor. */
	private InteractionExecutor interactionExecutor;

	/** The d var positions to wires. */
	private Map<Integer, Entry<Port, Port>> dVarPositionsToWires = new Hashtable<Integer, Entry<Port, Port>>();

	/** The positions of d variables. */
	private List<Integer> positionsOfDVariables = new ArrayList<Integer>();

	/**
	 * Boolean variable that shows whether the execute() was called.
	 */
	private boolean isEngineExecuting = false;

	/**
	 * Instantiates a new data coordinator impl.
	 * 
	 * @param bipCoordinator
	 *            the bip coordinator
	 */
	public DataCoordinatorKernel(BIPCoordinator bipCoordinator, DataEncoder dataEncoder) {

		this.dataEncoder = dataEncoder;

		assert (bipCoordinator != null);

		this.bipCoordinator = bipCoordinator;

		this.bipCoordinator.setInteractionExecutor(this);
		dataEncoder.setDataCoordinator(this);
		dataEncoder.setBehaviourEncoder(this.bipCoordinator.getBehaviourEncoderInstance());
		dataEncoder.setBDDManager(this.bipCoordinator.getBDDManager());
		componentDataWires = new HashMap<String, Map<String, Set<DataWire>>>();
	}

	/**
	 * Sends interactions-glue to the BIP Coordinator Sends data-glue to the Data Encoder.
	 * 
	 * @param glue
	 *            the glue
	 */
	public synchronized void specifyGlue(BIPGlue glue) {
		bipCoordinator.specifyGlue(glue);
		this.dataWires = glue.getDataWires();
		try {
			/*
			 * Send the information about the dataWires to the DataEncoder to create the d-variables
			 * BDD nodes.
			 * 
			 * specifyDataGlue checks the validity of wires and throws an exception if necessary.
			 */
			Set<BDD> dataConstraints = dataEncoder.specifyDataGlue(dataWires);
			// logger.debug("Data constraints from the encoder not null: " + (dataConstraints !=
			// null));
			bipCoordinator.specifyPermanentConstraints(dataConstraints);
		} catch (BIPEngineException e) {
			e.printStackTrace();
		}

		for (String componentType : typeInstancesMapping.keySet()) {
			// here we just get the behaviour of some component of this type
			// for all other components the Data and the Wires will be exactly
			// the same
			Behaviour behaviour = componentBehaviourMapping.get(typeInstancesMapping.get(componentType).get(0));
			Map<String, Set<DataWire>> dataWire = new Hashtable<String, Set<DataWire>>();
			for (Port port : behaviour.getEnforceablePorts()) {
				for (Data<?> data : behaviour.portToDataInForGuard(port)) {
					// if this data has already been treated for another port
					if (dataWire.containsKey(data)) {
						continue;
					}
					Set<DataWire> wireSet = new HashSet<DataWire>();
					for (DataWire wire : this.dataWires) {
						if (wire.isIncoming(data.name(), behaviour.getComponentType())) {
							wireSet.add(wire);
							// logger.trace("Added wire " + wire.getFrom() + " to data " +
							// data.name() + " of component "
							// + componentType);
						}
					}
					dataWire.put(data.name(), wireSet);
				}
				// Fixing bug: It should not only consider data associated to guards as above
				for (Data<?> data : behaviour.portToDataInForTransition(port)) {
					// if this data has already been treated for another port
					if (dataWire.containsKey(data)) {
						continue;
					}
					Set<DataWire> wireSet = new HashSet<DataWire>();
					for (DataWire wire : this.dataWires) {
						if (wire.isIncoming(data.name(), behaviour.getComponentType())) {
							wireSet.add(wire);
							// logger.trace("Added wire " + wire.getFrom() + " to data " +
							// data.name() + " of component "
							// + componentType);
						}
					}
					dataWire.put(data.name(), wireSet);
				}
			}

			componentDataWires.put(componentType, dataWire);
		}
		registrationFinished = true;
		int nbComponent = informedComponents.size();
		for (int i = 0; i < nbComponent; i++) {
			inform(informedComponents.get(i), informedComponentsState.get(i), informedComponentsPorts.get(i));
		}
	}



	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.api.BIPEngine#register(org.bip.api.BIPComponent, org.bip.api.Behaviour)
	 */
	public synchronized BIPActor register(Object object, String id, boolean useSpec) {
		/*
		 * The condition below checks whether the component has already been registered.
		 */
		BIPActor actor = null;
		try {
			if (object == null) {
				throw new BIPEngineException("Registering a null component.");
			}
			actor = bipCoordinator.register(object, id, useSpec);

			BIPComponent component = bipCoordinator.getComponentFromObject(object);
			Behaviour behaviour = bipCoordinator.getBehaviourByComponent(component);

			if (behaviour == null) {
				throw new BIPEngineException("Registering a component with null behaviour.");
			}
			assert (component != null && behaviour != null);

			if (registeredComponents.contains(component)) {
				logger.error("Component " + component + " has already registered before.");
				throw new BIPEngineException("Component " + component + " has already registered before.");
			} else {
				registeredComponents.add(component);
				componentBehaviourMapping.put(component, behaviour);
				nbPorts += behaviour.getEnforceablePorts().size();
				nbStates += behaviour.getStates().size();

			}
			ArrayList<BIPComponent> componentInstances = new ArrayList<BIPComponent>();

			/*
			 * If this component type already exists in the hashtable, update the ArrayList of
			 * BIPComponents that corresponds to this component type.
			 */
			if (typeInstancesMapping.containsKey(component.getType())) {
				componentInstances.addAll(typeInstancesMapping.get(component.getType()));
			}

			componentInstances.add(component);
			// SB: Not sure this is necessary, but should not harm
			typeInstancesMapping.remove(component.getType());
			typeInstancesMapping.put(component.getType(), componentInstances);
		} catch (BIPEngineException e) {
			// e.printStackTrace();
		}
		return actor;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.api.BIPEngine#inform(org.bip.api.BIPComponent, java.lang.String, java.util.Set)
	 */
	public synchronized void inform(BIPComponent component, String currentState, Set<Port> disabledPorts) {
		// for each component store its undecided ports
		// TODO create undecided port with the help of set.removeAll
		// long time1 = System.currentTimeMillis();

		/*
		 * If all components have not finished registering the informSpecific cannot be done: In the
		 * informSpecific information is required from all the registered components.
		 */
		if (!registrationFinished) {
			informedComponents.add(component);
			informedComponentsState.add(currentState);
			informedComponentsPorts.add(disabledPorts);
			return;
		}

		try {
			doInformSpecific(component, currentState, disabledPorts);
		} catch (BIPEngineException e) {
			// e.printStackTrace();
		}
		/*
		 * Inform the BIPCoordinator only after all the informSpecifics for the particular component
		 * have finished
		 */

		bipCoordinator.inform(component, currentState, disabledPorts);
		// System.out.println((System.currentTimeMillis() - time1));
	}

	/**
	 * Send each disabled combination of each deciding Component directly to the Data Encoder.
	 * 
	 * Exceptions are thrown if: 1. DecidingComponent are not in the list of registered components.
	 * 2. Deciding Port of the deciding component (holder component) is not specified in the
	 * Behaviour of the holder. 3. DisabledComponents in the disabledCombinations are not in the
	 * list of registered components. 4. Disabled Ports in the disabledCombinations are not
	 * specified in the Behaviour of the holder component.
	 * 
	 * @param decidingComponent
	 *            the deciding component
	 * @param decidingPort
	 *            the deciding port
	 * @param disabledCombinations
	 *            the disabled combinations
	 * @throws BIPEngineException
	 *             the BIP engine exception
	 */
	public synchronized void informSpecific(BIPComponent decidingComponent, Port decidingPort,
			Map<BIPComponent, Set<Port>> disabledCombinations) throws BIPEngineException {
		if (disabledCombinations == null || disabledCombinations.isEmpty()) {
			logger.debug("No disabled combinations specified in informSpecific for deciding component."
					+ decidingComponent.getId() + " for deciding port " + decidingPort.getId()
					+ " Map of disabledCombinations is empty.");
			/*
			 * This is not a bad situation, since that only means that all combinations are
			 * acceptable. Hence nothing to do.
			 */
			return;
		}
		assert (disabledCombinations != null && !disabledCombinations.isEmpty());

		if (!registeredComponents.contains(decidingComponent)) {
			logger.error("Deciding component specified in informSpecific is not in the list of registered components");
			throw new BIPEngineException(
					"Deciding component specified in informSpecific is not in the list of registered components");
		}
		assert (registeredComponents.contains(decidingComponent));
		assert (componentBehaviourMapping.get(decidingComponent) != null);

		ArrayList<Port> componentPorts = (ArrayList<Port>) componentBehaviourMapping.get(decidingComponent)
				.getEnforceablePorts();
		if (!componentPorts.contains(decidingPort)) {
			logger.error(
					"Deciding port {} in informSpecific is not specified in the behaviour of the deciding component {}.",
					decidingPort, decidingComponent.getId());
			throw new BIPEngineException(
					"Deciding port in informSpecific is not specified in the behaviour of the deciding component.");
		}
		assert (componentPorts.contains(decidingPort));

		Set<BIPComponent> disabledComponents = disabledCombinations.keySet();
		for (BIPComponent component : disabledComponents) {
			if (!registeredComponents.contains(component)) {
				logger.error("Component " + component.getId()
						+ " specified in the disabledCombinations of informSpecific was not registered."
						+ "\tPossible reason: "
						+ "Name attribute in ComponentType annotation does not match the name of the Class.");
				throw new BIPEngineException("Component " + component.getId()
						+ " specified in the disabledCombinations of informSpecific was not registered." + "\n"
						+ "\tPossible reason: "
						+ "Name attribute in ComponentType annotation does not match the name of the Class.");
			}
			assert (registeredComponents.contains(component));
			assert (componentBehaviourMapping.get(component) != null);

			Set<Port> disabledPortsOfOneComponent = disabledCombinations.get(component);
			for (Port port : disabledPortsOfOneComponent) {
				List<Port> componentEnforceablePorts = componentBehaviourMapping.
						get(component).
						getEnforceablePorts();
				if (!componentEnforceablePorts.contains(port)) {
					logger.error("Disabled port " + port
							+ " in informSpecific is not specified in the behaviour of the disabled component: "
							+ component.getId());
					throw new BIPEngineException("Disabled port " + port
							+ " in informSpecific is not specified in the behaviour of the disabled component: "
							+ component.getId());

				}
				assert (componentEnforceablePorts.contains(port));
			}
		}

		/*
		 * At this point we know that all the involved components are registered and the specified
		 * ports belong to corresponding behaviours.
		 * 
		 * Send each disabled combination of each deciding Component directly to the Data Encoder.
		 */

		bipCoordinator.specifyTemporaryConstraints(dataEncoder.encodeDisabledCombinations(decidingComponent,
				decidingPort, disabledCombinations));

	}

	/*
	 * Merging the "subinteractions". Some port can in more than one of the enabled d-variables.
	 * This port should not be sent to the Executors twice
	 */
	/**
	 * Merging sub interactions.
	 * 
	 * @param chosenInteraction
	 *            the chosen interaction
	 * @param portsExecuted
	 *            the ports executed
	 * @return the list
	 */
	private List<List<Port>> mergingSubInteractions(byte[] chosenInteraction, ArrayList<Port> portsExecuted) {
		for (Integer i : positionsOfDVariables) {
			if (chosenInteraction[i] == 1) {
				Entry<Port, Port> pair = dVarPositionsToWires.get(i);
				Port firstPair = pair.getKey();
				Port secondPair = pair.getValue();
				// logger.trace("D variable for ports: " + "\n\t" + firstPair + "\n\t of component "
				// + firstPair.component() + "\n\t " + secondPair + "\n\t of component " +
				// secondPair.component());

				// TODO DISCUSS DANGER remove from merged interactions those where component communicates with itself
				if (firstPair.component().equals(secondPair.component())) {
					continue;
				}
	
				//  check for data for the first component
				setDataValuationToExecutor(firstPair, secondPair);
			    //  check for data for the second component
				setDataValuationToExecutor(secondPair, firstPair);

				if (!portsExecuted.contains(firstPair)) {
					portsExecuted.add(firstPair);
				}
				if (!portsExecuted.contains(secondPair)) {
					portsExecuted.add(secondPair);
				}
			}
		}
		List<List<Port>> bigInteraction = new ArrayList<List<Port>>();
		bigInteraction.add(portsExecuted);
		return bigInteraction;
	}

	/**
	 * by Alina
	 * This method sets the data value in the Executor of a component which asks for it in order to execute the port askingData.
	 * The data is provided through the port providingData of another component.
	 */
	private synchronized void setDataValuationToExecutor(Port askingData, Port providingData) {
		if (isEngineExecuting) {
			Behaviour behaviour = componentBehaviourMapping.get(askingData.component());
		
		if (behaviour!=null){
		Iterable<Data<?>> portToDataInForTransition = behaviour.portToDataInForTransition(askingData);
				for (Data<?> dataItem : portToDataInForTransition) {
					assert (componentBehaviourMapping.get(askingData.component()) != null);
					String dataOutName = dataIsProvided(providingData,
							componentBehaviourMapping.get(askingData.component()).getComponentType(), dataItem.name());
					BIPComponent component = providingData.component();
					if (dataOutName != null && !dataOutName.isEmpty() && isEngineExecuting) {
						Object dataValue = component.getData(dataOutName, dataItem.type());
						// logger.trace("GETTING DATA: from component " + providingData.component()
						// + " the value "
						// + dataValue);
						// this condition makes it impossible for a method providing a dataOut to return null, it must always return some particular value
						if (dataValue == null) {
							logger.error("Component: " + askingData.component()
									+ " is asking data from component: "
									+ providingData.component() + " , with name: " + dataOutName + " and type: "
									+ dataItem.type() + " The function getData of the Executor kernel returns null. ");

							throw new IllegalArgumentException("Component: " + askingData.component()
									+ " is asking data from component: " + providingData.component() + " , with name: "
									+ dataOutName + " and type: " + dataItem.type()
									+ " The function getData of the Executor kernel returns null for these arguments. ");
						}
						askingData.component().setData(dataItem.name(), dataValue);

					}
				}
			}
		}

	}

	/**
	 * BDDBIPEngine informs the BIPCoordinator for the components (and their associated ports) that
	 * are part of the same chosen interaction.
	 * 
	 * Through this function all the components need to be notified. If they are participating in an
	 * interaction then their port to be fired is sent to them through the execute function of the
	 * BIPExecutor. If they are not participating in an interaction then null is sent to them.
	 * 
	 * @param portGroupsToExecute
	 *            the port groups to execute
	 * @throws BIPEngineException
	 *             the BIP engine exception
	 */
	public void executeInteractions(List<List<Port>> portGroupsToExecute) throws BIPEngineException {
		this.count++;
		/**
		 * This is a list of components participating in the chosen-by-the-engine interactions. This
		 * keeps track of the chosen components in order to differentiate them from the non chosen
		 * ones. Through this function all the components need to be notified. Either by sending
		 * null to them or the port to be fired.
		 */
		if (isEngineExecuting)
			bipCoordinator.executeInteractions(portGroupsToExecute);
	}

	/**
	 * Data is provided.
	 * 
	 * @param providingPort
	 *            the providing port
	 * @param requiringComponentType
	 *            the requiring component type
	 * @param dataName
	 *            the data name
	 * @return the string
	 */
	private String dataIsProvided(Port providingPort, String requiringComponentType, String dataName) {
		BIPComponent providingComponent = providingPort.component();
		assert (componentBehaviourMapping.get(providingComponent) != null);
		for (DataWire wire : this.componentDataWires.get(requiringComponentType).get(dataName)) {
			if (wire.getFrom().getSpecType()
					.equals(componentBehaviourMapping.get(providingComponent).getComponentType())) {
				Set<Port> portsProviding = componentBehaviourMapping.get(providingComponent).getDataProvidingPorts(
						wire.getFrom().getId());
				for (Port outport : portsProviding) {
					if (outport.getId().equals(providingPort.getId())
							&& outport.getSpecType().equals(providingPort.getSpecType())) {
						return wire.getFrom().getId();
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
		isEngineExecuting = false;
		bipCoordinator.stop();

	}

	public void execute() {
		if (this.interactionExecutor == null) {
			setInteractionExecutor(this);
		}
		isEngineExecuting = true;
		bipCoordinator.execute();
	}


	/**
	 * Do inform specific. Alina's code
	 * 
	 * @param component
	 *            the component
	 * @throws BIPEngineException
	 *             the BIP engine exception
	 */
	private void doInformSpecific(BIPComponent component, String currentState, Set<Port> disabledPorts)
			throws BIPEngineException {
		// mapping port <-> data it needs for computing guards
		Behaviour decidingBehaviour = componentBehaviourMapping.get(component);
		// for each undecided port of each component :
		for (Port port : getUndecidedPorts(component, currentState, disabledPorts)) {
			// get list of DataIn needed for its guards
			Set<Data<?>> dataIn = decidingBehaviour.portToDataInForGuard(port);

			if (dataIn.isEmpty()) {
				// if the data is empty, then the port is enabled. just send it.
				this.informSpecific(component, port, new HashMap<BIPComponent, Set<Port>>());
				continue;
			}
			// for each data its different evaluations
			Hashtable<String, ArrayList<Object>> dataEvaluation = new Hashtable<String, ArrayList<Object>>();
			// list of data structures build upon receiving the data value
			ArrayList<DataContainerImpl> dataList = new ArrayList<DataContainerImpl>();
			// for each DataIn variable get info which components provide it as
			// their outData
			for (Data<?> inDataItem : dataIn) {
				// for each wire that is incoming for this data of this
				// component (as precomputed)
				for (DataWire wire : this.componentDataWires.get(decidingBehaviour.getComponentType()).get(
						inDataItem.name())) {

					// for this dataVariable: all the values that it can take
					ArrayList<Object> dataValues = new ArrayList<Object>();
					// for each component of this type, call getData
					for (BIPComponent aComponent : getBIPComponentInstances(wire.getFrom().getSpecType())) {
						// TODO quick fix for the component not to receive data from itself
						// discuss it and check whether it works fine in all situations and
						if (aComponent.equals(component)) {
							continue;
						}
						Object inValue = aComponent.getData(wire.getFrom().getId(), inDataItem.type());
						// get data out variable in order to get the ports
						if (inValue != null) {
							assert (componentBehaviourMapping.get(aComponent) != null);
							assert (wire.getFrom() != null);
							Set<Port> providingPorts = componentBehaviourMapping.get(aComponent).getDataProvidingPorts(
									wire.getFrom().getId());
							dataList.add(new DataContainerImpl(inDataItem, inValue, aComponent, providingPorts));
							dataValues.add(inValue);
						}
					}
					dataEvaluation.put(inDataItem.name(), dataValues);
				}
			}

			ArrayList<ArrayList<DataContainerImpl>> containerList = getDataValueTable(dataList);
			// if (logger.isTraceEnabled()) {
			// print(containerList, component);
			// }
			ArrayList<Map<String, Object>> dataTable = createDataTable(containerList);

			// the result provided must have the same order - put comment
			// containerList and portActive are dependant on each other
			List<Boolean> portActive = component.checkEnabledness(port, dataTable);
			logger.trace("The result of checkEndabledness for component {}: {}.", component, portActive);
			HashMap<BIPComponent, Set<Port>> disabledCombinations = new HashMap<BIPComponent, Set<Port>>();
			for (int i = 0; i < portActive.size(); i++) {
				disabledCombinations.clear();
				if (!(portActive.get(i))) {
					ArrayList<DataContainerImpl> dataContainer = containerList.get(i);
					for (DataContainerImpl dc : dataContainer) {
						// logger.debug(this.count + " CONTAINER CHOSEN: For deciding " +
						// component.hashCode() + " and "
						// + port.getId() + " disabled is " + dc.component() + " with ports " +
						// dc.ports());
						disabledCombinations.put(dc.component(), dc.ports());						
					}
				}
				this.informSpecific(component, port, disabledCombinations);
			}
			//TODO HACK this was added to  make the component unable to interact with itself.
			// TODO quick fix for the component not to receive data from itself
			// discuss it and check whether it works fine in all situations and
			disabledCombinations.clear();
			disabledCombinations.put(component, new HashSet(decidingBehaviour.getEnforceablePorts()));
			this.informSpecific(component, port, disabledCombinations);
		}


	}

	/**
	 * Prints the. Alina' code
	 * 
	 * @param containerList
	 *            the container list
	 * @param component
	 *            the component
	 */
	// private void print(ArrayList<ArrayList<DataContainerImpl>> containerList, BIPComponent
	// component) {
	// for (ArrayList<DataContainerImpl> dataList : containerList) {
	// for (DataContainerImpl container : dataList) {
	// logger.trace(this.count + " Deciding " + component.hashCode() + ", Providing " +
	// container.component()
	// + " the value " + container.value());
	// }
	// }
	//
	// }

	/**
	 * Creates the data table. Alina
	 * 
	 * @param containerList
	 *            the container list
	 * @return the array list
	 */
	private ArrayList<Map<String, Object>> createDataTable(ArrayList<ArrayList<DataContainerImpl>> containerList) {
		ArrayList<Map<String, Object>> dataTable = new ArrayList<Map<String, Object>>();
		for (ArrayList<DataContainerImpl> container : containerList) {
			Map<String, Object> row = new Hashtable<String, Object>();
			for (DataContainerImpl dc : container) {
				row.put(dc.name(), dc.value());
			}
			dataTable.add(row);
		}
		return dataTable;
	}

	/**
	 * Gets the data value table. Alina's code
	 * 
	 * @param dataList
	 *            the data list
	 * @return the data value table
	 */
	private ArrayList<ArrayList<DataContainerImpl>> getDataValueTable(ArrayList<DataContainerImpl> dataList) {
		ArrayList<ArrayList<DataContainerImpl>> result = new ArrayList<ArrayList<DataContainerImpl>>();

		if (dataList == null || dataList.isEmpty()) {
			// throw exception
			return result;
		}
		ArrayList<ArrayList<DataContainerImpl>> sortedList = getListList(dataList);

		// for one bipData get iterator over its values
		ArrayList<DataContainerImpl> entry = sortedList.get(0);
		Iterator<DataContainerImpl> iterator = entry.iterator();

		// for each value of this first bipData
		while (iterator.hasNext()) {
			// create one map, where
			// all the different pairs name<->value will be stored
			// put there the current value of the first bipData
			ArrayList<DataContainerImpl> dataRow = new ArrayList<DataContainerImpl>();
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

	/**
	 * Gets the next table row. Alina's code
	 * 
	 * @param sortedList
	 *            the sorted list
	 * @param dataRow
	 *            the data row
	 * @return the next table row
	 */
	private ArrayList<ArrayList<DataContainerImpl>> getNextTableRow(ArrayList<ArrayList<DataContainerImpl>> sortedList,
			ArrayList<DataContainerImpl> dataRow) {
		ArrayList<ArrayList<DataContainerImpl>> result = new ArrayList<ArrayList<DataContainerImpl>>();
		// if there is no more data left, it means we have constructed one map
		// of all the bipData variables
		if (sortedList == null || sortedList.isEmpty()) {
			result.add(dataRow);
			return result;
		}

		// for one bipData get iterator over its values
		ArrayList<DataContainerImpl> entry = sortedList.iterator().next();
		Iterator<DataContainerImpl> iterator = entry.iterator();

		// for each value of this bipData
		while (iterator.hasNext()) {
			// create a new map, where
			// all the different pairs name<->value will be stored
			// copy there all the previous values
			// (this must be done to escape
			// change of one variable that leads to change of all its copies
			ArrayList<DataContainerImpl> thisRow = new ArrayList<DataContainerImpl>();
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

	/**
	 * Gets the list list. Alina's code
	 * 
	 * @param list
	 *            the list
	 * @return the list list
	 */
	private ArrayList<ArrayList<DataContainerImpl>> getListList(ArrayList<DataContainerImpl> list) {
		ArrayList<ArrayList<DataContainerImpl>> result = new ArrayList<ArrayList<DataContainerImpl>>();

		while (!list.isEmpty()) {
			ArrayList<DataContainerImpl> oneDataList = new ArrayList<DataContainerImpl>();
			DataContainerImpl data = list.get(0);
			oneDataList.add(data);
			list.remove(data);
			for (DataContainerImpl d : list) {
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
	 * Gets the undecided ports. Alina's code
	 * 
	 * @param component
	 *            the component
	 * @param currentState
	 *            the current state
	 * @param disabledPorts
	 *            the disabled ports
	 * @return the undecided ports
	 */
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
				if (port.getId().equals(disabledPort.getId())) {
					portIsDisabled = true;
					break;
				}
			}
			if (!portIsDisabled) {
				undecidedPorts.add(port);
			}
			portIsDisabled = false;
		}
		// logger.trace("For component {} the undecided ports are {}. " + component.getId(),
		// undecidedPorts);
		return undecidedPorts;
	}

	/**
	 * Helper function that returns the registered component instances that correspond to a
	 * component type.
	 * 
	 * @param type
	 *            the type
	 * @return the BIP component instances
	 * @throws BIPEngineException
	 *             the BIP engine exception
	 */
	public List<BIPComponent> getBIPComponentInstances(String type) throws BIPEngineException {
		ArrayList<BIPComponent> instances = typeInstancesMapping.get(type);
		if (instances == null) {
			try {
				logger.error(
						"No registered component instances for the: {} ",
						type
								+ " component type. Possible reasons: The name of the component instances was specified in another way at registration.");
				throw new BIPEngineException(
						"Exception in thread "
								+ Thread.currentThread().getName()
								+ " No registered component instances for the component type: "
								+ "'"
								+ type
								+ "'"
								+ " Possible reasons: The name of the component instances was specified in another way at registration.");
			} catch (BIPEngineException e) {
				// e.printStackTrace();
				throw e;
			}
		}
		return instances;
	}

	/**
	 * Helper function that given a component returns the corresponding behaviour as a Behaviour
	 * Object.
	 * 
	 * @param component
	 *            the component
	 * @return the behaviour by component
	 */
	public Behaviour getBehaviourByComponent(BIPComponent component) {
		return componentBehaviourMapping.get(component);
	}

	/**
	 * Helper function that returns the total number of ports of the registered components.
	 * 
	 * @return the no ports
	 */
	public int getNoPorts() {
		return nbPorts;
	}

	/**
	 * Helper function that returns the total number of states of the registered components.
	 * 
	 * @return the no states
	 */
	public int getNoStates() {
		return nbStates;
	}

	public int getNoComponents() {
		return bipCoordinator.getNoComponents();
	}


	public void execute(byte[] valuation) throws BIPEngineException {
		if (interactionExecutor != this && isEngineExecuting) {
			interactionExecutor.execute(valuation);
		} else if (isEngineExecuting) {
			executeInteractions(preparePorts(valuation));
		}
		logger.debug("*************************************************************************");
	}

	/**
	 * Prepare ports.
	 * 
	 * @param valuation
	 *            the valuation
	 * @return the list
	 */
	private List<List<Port>> preparePorts(byte[] valuation) {
		/*
		 * Grouping the interaction into smaller ones (with respect to data transfer)
		 */
		// Map<BIPComponent, Iterable<Port>> chosenPorts = new Hashtable<BIPComponent,
		// Iterable<Port>>();
		// logger.trace("positionsOfDVariables size: " + positionsOfDVariables.size());
		ArrayList<Port> portsExecuted = new ArrayList<Port>();
		List<List<Port>> bigInteraction = mergingSubInteractions(valuation, portsExecuted);

		/*
		 * Find ports that participate in the interaction but not in data transfer and add them as a
		 * separate group
		 */
		ArrayList<Port> enabledPorts = new ArrayList<Port>();
		Map<Port, Integer> portToPosition = bipCoordinator.getBehaviourEncoderInstance().getPortToPosition();
		ArrayList<BIPComponent> componentsEnum = registeredComponents;
		for (BIPComponent component : componentsEnum) {
			Iterable<Port> componentPorts = null;
			Behaviour behaviour = getBehaviourByComponent(component);
			if (behaviour == null) {
				isEngineExecuting = false;
				return null;
			}

				componentPorts = behaviour.getEnforceablePorts();



			// if (componentPorts == null || !componentPorts.iterator().hasNext()) {
			// logger.trace("Component {} does not have any enforceable ports.", component);
			// }
			for (Port port : componentPorts) {
				if (!portsExecuted.contains(port)
						&& (valuation[portToPosition.get(port)] == 1 || valuation[portToPosition.get(port)] == -1)
						&& isEngineExecuting) {
					// logger.trace("Chosen Port: {}" + port.getId() + "of component: " +
					// port.component());
					enabledPorts.add(port);
				}
			}
		}

		if (enabledPorts.size() != 0) {
			bigInteraction.add(enabledPorts);
		}
		/*
		 * Here the ports mentioned above have been added For debug only //TODO: Comment out before
		 * performance evaluation
		 */
		// System.out.println("++++++++++++++++++++++++++++++++++++++++++++");
		// for (Iterable<Port> inter : bigInteraction) {
		// for (Port port : inter) {
		// logger.debug("ENGINE choice: " + "Chosen Port: {}" + port.getId() + " of component: "
		// + port.component());
		// System.out.println("ENGINE choice: " + "Chosen Port: " + port.getId() + " of component: "
		// + port.component());
		// }
		// }
		// System.out.println("++++++++++++++++++++++++++++++++++++++++++++");
		// logger.trace("Interactions: " + bigInteraction.size());
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


	public void specifyPermanentConstraints(Set<BDD> constraints) {
		bipCoordinator.specifyPermanentConstraints(constraints);
	}


	public DataEncoder getDataEncoder() {
		return dataEncoder;
	}

	/**
	 * Gets the d var positions to wires.
	 * 
	 * @return the dVariablesToPosition
	 */
	public Map<Integer, Entry<Port, Port>> getdVarPositionsToWires() {
		return dVarPositionsToWires;
	}

	/**
	 * Gets the positions of d variables.
	 * 
	 * @return the positionsOfDVariables
	 */
	public List<Integer> getPositionsOfDVariables() {
		return positionsOfDVariables;
	}

	/**
	 * Setd variables to position.
	 * 
	 * @param dVarPositionsToWires
	 *            the dVariablesToPosition to set
	 */
	public void setdVariablesToPosition(Map<Integer, Entry<Port, Port>> dVarPositionsToWires) {
		this.dVarPositionsToWires = dVarPositionsToWires;
	}

	/**
	 * Sets the positions of d variables.
	 * 
	 * @param positionsOfDVariables
	 *            the positionsOfDVariables to set
	 */
	public void setPositionsOfDVariables(List<Integer> positionsOfDVariables) {
		this.positionsOfDVariables = positionsOfDVariables;
	}

	@Override
	public BIPComponent getComponentFromObject(Object component) {
		return bipCoordinator.getComponentFromObject(component);
	}

	@Override
	public void initialize() {
		bipCoordinator.initialize();
	}

}
