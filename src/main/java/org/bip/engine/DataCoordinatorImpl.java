package org.bip.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;

import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.api.Behaviour;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;
import org.bip.glue.BIPGlue;
import org.bip.glue.DataWire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// There is no need for DataCoordinator interface, just DataCoordinatorImpl will implement BIP engine interface.
// DataCoordinatorImpl needs BIP coordinator ( BIP Engine again ) that actual does all the computation of BIP
// engine. DataCoordinatorImpl takes care of only of data querying and passing to BIP executors.

// DataCoordinator intercepts call register and inform from BIPExecutor. For each BIPComponent it
// creates a Proxy of BIPComponent (also BIPComponent class) that is registered with BIPCoordinator.
// This BIPComponent proxy is the identity provided to BIPCordinator. Now, DataCordinator implements
// just BIPEngine interface so it able also to intercept informs and translate it into proper informs.
// BIPComponent Proxy can intercept execute functions invoked by BIPCoordinator and enrich with data
// provided by DataCoordinatorImpl. Thus, Proxy BIPComponent knows about DataCoordinatorImpl, and
// original BIPcomponent, so BIPcomponent proxy can query DataCoordinatorIMpl for the data and call
// function execute of the original BIPComponent with proper data.

public class DataCoordinatorImpl implements BIPEngine, InteractionExecutor, Runnable {

	private Logger logger = LoggerFactory.getLogger(BIPCoordinatorImpl.class);

	private ArrayList<BIPComponent> registeredComponents = new ArrayList<BIPComponent>();

	/**
	 * Helper hashtable with integers representing the local identities of
	 * registered components as the keys and the Behaviours of these components
	 * as the values.
	 */
	private Hashtable<BIPComponent, Behaviour> componentBehaviourMapping = new Hashtable<BIPComponent, Behaviour>();
	/**
	 * Helper hashset of the components that have informed in an execution
	 * cycle.
	 */
	private Hashtable<BIPComponent, ArrayList<Port>> componentUndecidedPorts = new Hashtable<BIPComponent, ArrayList<Port>>();
	/**
	 * Helper hashset of the components that have informed in an execution
	 * cycle.
	 */
	private HashSet<BIPComponent> componentsHaveInformed = new HashSet<BIPComponent>();
	/**
	 * Helper hashtable with strings as keys representing the component type of
	 * the registered components and ArrayList of BIPComponent instances that
	 * correspond to the component type specified in the key.
	 */
	private Hashtable<String, ArrayList<BIPComponent>> typeInstancesMapping = new Hashtable<String, ArrayList<BIPComponent>>();

	/**
	 * Create instances of all the the Data Encoder and of the BIPCoordinator
	 */
	private DataEncoder dataEncoder = new DataEncoderImpl();
	private BIPCoordinator BIPCoordinator = new BIPCoordinatorImpl();
	// TODO the dataCoordinator and the BIPcoordinator have different engines.
	// I think it is not good. Has to be investigated.
	private BDDBIPEngine engine = new BDDBIPEngineImpl();

	ArrayList<DataWire> dataWires;

	public DataCoordinatorImpl() {
		dataEncoder.setEngine(engine);
		BIPCoordinator.setInteractionExecutor(this);
	}

	public void specifyGlue(BIPGlue glue) {
		BIPCoordinator.specifyGlue(glue);
		// this.glue = glue;
		this.dataWires = glue.dataWires;
	}

	public void register(BIPComponent component, Behaviour behaviour) {
		/*
		 * The condition below checks whether the component has already been
		 * registered.
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
				BIPCoordinator.register(component, behaviour);
			}
			ArrayList<BIPComponent> componentInstances = new ArrayList<BIPComponent>();

			/*
			 * If this component type already exists in the hashtable, update
			 * the ArrayList of BIPComponents that corresponds to this component
			 * type.
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

	public void inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) {
		// for each component store its undecided ports
		componentUndecidedPorts.put(component, getUndecidedPorts(component, disabledPorts));
		// easy implementation: when all the components have informed
		// TODO the data wiring process does not need all the components having
		// informed

		try {
			wireData();
		} catch (BIPEngineException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void wireData() throws BIPEngineException {
		for (BIPComponent component : registeredComponents) {
			// mapping port <-> data it needs for computing guards
			Map<Port, Iterable<String>> portToDataInForGuard = componentBehaviourMapping.get(component).portToDataInForGuard();
			// for each undecided port of each component :
			for (Port port : componentUndecidedPorts.get(component)) {
				// get list of DataIn needed for its guards
				Iterable<String> dataIn = portToDataInForGuard.get(port);
				// for each DataIn variable get info which components provide it
				// as their outData
				Iterable<Map<String, Object>> dataTable = getDataWires(dataIn, component);
				ArrayList<Boolean> portActive = (ArrayList<Boolean>) component.checkEnabledness(port, dataTable);
			}
		}
		// executor.getData()
		// executor.checkEnabledness()
		// inform the BIPCoordinator
	}

	private Iterable<Map<String, Object>> getDataWires(Iterable<String> dataInNeeded, BIPComponent component) throws BIPEngineException {
		// mapping inData <-> outData, where
		// in outData we have a name and a list of components providing it.
		// for one inData there can be several outData variables
		// for each data its different evaluations
		Hashtable<String, ArrayList<Object>> dataEvaluation = new Hashtable<String, ArrayList<Object>>();
		for (String inDataItem : dataInNeeded) {
			for (DataWire wire : this.dataWires) {
				// for this dataVariable: all the values that it can take
				ArrayList<Object> dataValues = new ArrayList<Object>();
				if (wire.to.id.equals(inDataItem) && wire.to.specType.equals(componentBehaviourMapping.get(component).getComponentType())) {

					ArrayList<BIPComponent> fromComponents = (ArrayList<BIPComponent>) getBIPComponentInstances(wire.from.specType);
					for (BIPComponent aComponent : fromComponents) {
						// TODO add type instead of int
						dataValues.add(aComponent.getData(inDataItem, int.class));
					}
				}
				dataEvaluation.put(inDataItem, dataValues);
			}

			// Iterable<Boolean> checkEnabledness(Port port,
			// Iterable<Map<String, Object>> data)
		}
		Iterable<Map<String, Object>> dataTable = getDataValueTable(dataEvaluation);

		return dataTable;

	}

	/**
	 * This function takes the structure build from data received from the
	 * executors and changes it to the structure with which the executor can be
	 * questioned vie checkEnabledness method
	 * 
	 * @param dataEvaluation
	 *            Table bipData <-> possible evaluations
	 * @return List of all possible entries, where each entry consists of the
	 *         same number of pairs bipData <-> value
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
	 * This function helps the function getDataValueTable to transform the
	 * structure for the checkEnabledness method
	 * 
	 * @param dataEvaluation
	 *            Table bipData <-> possible evaluations
	 * @param dataRow
	 *            The current row of bipData <-> value pairs already treated
	 *            that needs to be augmented
	 * @return List of possible entries, where each entry consists of the a
	 *         number of pairs bipData <-> value
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

	private ArrayList<Port> getUndecidedPorts(BIPComponent component, ArrayList<Port> disabledPorts) {
		ArrayList<Port> undecidedPorts = new ArrayList<Port>();
		Behaviour behaviour = componentBehaviourMapping.get(component);
		boolean portIsDisabled = false;
		// for each port that we have
		for (Port port : behaviour.getEnforceablePorts()) {
			for (Port disabledPort : disabledPorts) {
				// if it is equal to one of the disabled ports, we mark it as
				// disabled and do not add to the collection of undecided
				if (port.equals(disabledPort)) {
					portIsDisabled = true;
					break;
				}
			}
			if (!portIsDisabled) {
				undecidedPorts.add(port);
			}
			portIsDisabled = false;
		}
		return undecidedPorts;
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


public void informSpecific(BIPComponent decidingComponent, Port decidingPort, Map<BIPComponent, Port> disabledCombinations) throws BIPEngineException {
		
		if (disabledCombinations.isEmpty()){
			try {
				logger.error("No disabled combination specified in informSpecific. Map of disabledCombinations is empty.");
				throw new BIPEngineException("No disabled combination specified in informSpecific. Map of disabledCombinations is empty.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}	
		}
		else if(!registeredComponents.contains(decidingComponent)){
			try {
				logger.error("Deciding component specified in informSpecific is not in the list of registered components");
				throw new BIPEngineException("Deciding component specified in informSpecific is not in the list of registered components");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}	
		}
		else if(!componentBehaviourMapping.get(decidingComponent).getEnforceablePorts().iterator().equals(decidingPort)){
			try {
				logger.error("Deciding port in informSpecific is not specified in the behaviour of the deciding component.");
				throw new BIPEngineException("Deciding port in informSpecific is not specified in the behaviour of the deciding component.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}	
		}
		else{
			Iterator <BIPComponent> disabledComponents = disabledCombinations.keySet().iterator();
			while (disabledComponents.hasNext()){
				BIPComponent component = disabledComponents.next();
				if (!registeredComponents.contains(component)) {
					logger.error("Component " + component.getName() + " specified in the disabledCombinations of informSpecific was not registered.");
					throw new BIPEngineException("Component " + component.getName() + " specified in the disabledCombinations of informSpecific was not registered.");
				}
			}
			engine.informSpecific(dataEncoder.informSpecific(decidingComponent, decidingPort, disabledCombinations));

		}
	}

	/**
	 * BDDBIPEngine informs the BIPCoordinator for the components (and their
	 * associated ports) that are part of the same chosen interaction.
	 * 
	 * Through this function all the components need to be notified. If they are
	 * participating in an interaction then their port to be fired is sent to
	 * them through the execute function of the BIPExecutor. If they are not
	 * participating in an interaction then null is sent to them.
	 * 
	 * @throws BIPEngineException
	 */

	// TODO: when changes in Engine are finished test it and delete
	// executeComponent
	public void executeInteractions(Iterable<Map<BIPComponent, Iterable<Port>>> portsToFire) throws BIPEngineException {
		Iterator<Map<BIPComponent, Iterable<Port>>> enabledCombinations = portsToFire.iterator();
		/*
		 * This is a list of components participating in the
		 * chosen-by-the-engine interactions. This keeps track of the chosen
		 * components in order to differentiate them from the non chosen ones.
		 * Through this function all the components need to be notified. Either
		 * by sending null to them or the port to be fired.
		 */
		ArrayList<BIPComponent> enabledComponents = new ArrayList<BIPComponent>();
		while (enabledCombinations.hasNext()) {
			Map<BIPComponent, Iterable<Port>> oneInteraction = enabledCombinations.next();
			Iterator<BIPComponent> interactionComponents = oneInteraction.keySet().iterator();
			while (interactionComponents.hasNext()) {
				BIPComponent component = interactionComponents.next();
				enabledComponents.add(component);
				Iterator<Port> compPortsToFire = oneInteraction.get(component).iterator();
				/*
				 * If the Iterator<Port> is null or empty for a chosen
				 * component, throw an exception. This should not happen.
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
						 * If the port is null or empty for a chosen component,
						 * throw an exception. This should not happen.
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
						logger.debug("Component {} execute port {}", component.getName(), port.id);
						// TODO: Find out which components are sending data to
						// this component
						// TODO: Change the following execute to the one that
						// specifies data for execution of transitions. In
						// particular, change this:
						component.execute(port.id);
						// to this:
						// void execute(String portID, Map<String, ?> data);
					}
				}
			}
		}
		/*
		 * send null to the components that are not part of the overall
		 * interaction
		 */
		for (BIPComponent component : registeredComponents) {
			if (!enabledComponents.contains(component)) {
				component.execute(null);
			}
		}
	}

	@Override
	public void run() {
		// TODO: unregister components and notify the component that the engine
		// is not working
		// for (BIPComponent component : identityMapping.values()) {
		// component.deregister();
		// }
		componentBehaviourMapping.clear();
		componentsHaveInformed.clear();
		return;
	}


}
