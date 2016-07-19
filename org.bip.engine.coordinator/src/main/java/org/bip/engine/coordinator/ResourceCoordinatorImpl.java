package org.bip.engine.coordinator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.RecognitionException;
import org.bip.api.BIPActor;
import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.api.BIPGlue;
import org.bip.api.Behaviour;
import org.bip.api.Port;
import org.bip.api.ResourceManager;
import org.bip.api.ResourceProvider;
import org.bip.constraints.jacop.JacopSolver;
import org.bip.engine.api.BIPCoordinator;
import org.bip.engine.api.Coordinator;
import org.bip.engine.api.DataCoordinator;
import org.bip.engine.api.InteractionExecutor;
import org.bip.engine.api.ResourceCoordinator;
import org.bip.engine.api.ResourceEncoder;
import org.bip.exceptions.BIPEngineException;
import org.bip.resources.DNetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceCoordinatorImpl implements ResourceCoordinator {

	//TODO think when we should call the dataCoordinator and when the bipCoordinator
	
	/** The logger. */
	private Logger logger = LoggerFactory.getLogger(ResourceCoordinatorImpl.class);

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

	/** Number of ports of components registered. */
	private int nbPorts;

	/** Number of states of components registered. */
	private int nbStates;

	/** The interactions count (for logging purposes). */
	private int count;

	/** Create instances of all the the Data Encoder and of the BIPCoordinator. */
	private ResourceEncoder resourceEncoder;

	/** The bip coordinator. */
	private Coordinator bipCoordinator = null;
	private BIPEngine prevCoordinator = null; //either dataCoordinator or BIPCoordinator
	// it cannot be just bip engine (or we should extend it) - I cannot call execute ports on it

	/** The registration finished. */
	private boolean registrationFinished = false;

	/** The interaction executor. */
	private InteractionExecutor interactionExecutor;

	/** The d var positions to wires. */
	//private Map<Integer, Entry<Port, Port>> dVarPositionsToWires = new Hashtable<Integer, Entry<Port, Port>>();

	/** The positions of d variables. */
	//private List<Integer> positionsOfDVariables = new ArrayList<Integer>();

	/**
	 * Boolean variable that shows whether the execute() was called.
	 */
	private boolean isEngineExecuting = false;
	
	private List<Set> interactionPorts;
	private HashMap<BIPComponent, List<Port>> componentToPortsRequestingResource;
	private HashMap<BIPComponent, List<Port>> componentToPortsReleasingResource;
	
	private  List<Port> portsRequestingResource;
	private  List<Port> portsReleasingResource;

	private HashMap<String, ResourceManager> resourceNameToManagers = new HashMap<String, ResourceManager>();
	private Set<ResourceManager> resourceManagers = new HashSet<ResourceManager>();
	
	private ResourceHelper resourceHelper;

	/**
	 * Instantiates a new data coordinator impl.
	 * 
	 * @param bipCoordinator
	 *            the bip coordinator
	 * @param cfNetPath 
	 * @throws DNetException 
	 * @throws IOException 
	 * @throws RecognitionException 
	 */
	public ResourceCoordinatorImpl(BIPCoordinator bipCoordinator, BIPEngine nextCoordinator, ResourceEncoder resourceEncoder, String cfNetPath) {

		this.resourceEncoder = resourceEncoder;

		assert (bipCoordinator != null);

		this.bipCoordinator = bipCoordinator;
		this.prevCoordinator = nextCoordinator;
		
		//this.prevCoordinator.setInteractionExecutor(this);
		this.bipCoordinator.setInteractionExecutor(this);
		resourceEncoder.setResourceCoordinator(this);
		//resourceEncoder.setBehaviourEncoder(this.bipCoordinator.getBehaviourEncoderInstance());
		resourceEncoder.setBDDManager(bipCoordinator.getBDDManager());
		//componentDataWires = new HashMap<String, Map<String, Set<DataWire>>>();
		interactionPorts = new ArrayList<Set>();
		componentToPortsRequestingResource = new HashMap<BIPComponent, List<Port>>();
		componentToPortsReleasingResource = new HashMap<BIPComponent, List<Port>>();
		portsRequestingResource = new ArrayList<Port>();
		portsReleasingResource = new ArrayList<Port>();

		resourceHelper = new ResourceHelper(cfNetPath);
	}
	
	@Override
	public void initialize() {
		bipCoordinator.initialize();
	}
	
	public void start() {
		delayedSpecifyGlue(glueHolder);
		bipCoordinator.start();
	}

	public void stop() {
		isEngineExecuting = false;
		bipCoordinator.stop();

	}
	
	BIPGlue glueHolder;

	public synchronized void specifyGlue(BIPGlue glue) {
		glueHolder = glue;
	}

	public void execute() {
		if (this.interactionExecutor == null) {
			setInteractionExecutor(this);
		}
		isEngineExecuting = true;
		bipCoordinator.execute();
	}
	
	private synchronized void delayedSpecifyGlue(BIPGlue glue) {
		prevCoordinator.specifyGlue(glue); // the call to the bipCoordinator happens within
		registrationFinished = true;
		int nbComponent = informedComponents.size();
		for (int i = 0; i < nbComponent; i++) {
			inform(informedComponents.get(i), informedComponentsState.get(i), informedComponentsPorts.get(i));
		}
	}
	
	public synchronized BIPActor register(Object object, String id, boolean useSpec) {
		/*
		 * The condition below checks whether the component has already been registered.
		 */
		BIPActor actor = null;
		try {
			if (object == null) {
				throw new BIPEngineException("Registering a null component.");
			}
			actor = prevCoordinator.register(object, id, useSpec);

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
			
			//componentToPortsRequestingResource.put(component, component.getPortsRequestingResources());
			//componentToPortsReleasingResource.put(component, component.getPortsReleasingResources());
			
			//TODO put ports releasing and requesting in the corresponding lists
			if (component.resourceName()!=null && !(component.resourceName().equals(""))) {
				resourceManagers.add(component);
				resourceNameToManagers.put(component.resourceName(), component);
				resourceHelper.addResource(component);
			}
			
		} catch (BIPEngineException e) {
			e.printStackTrace();
		}
		return actor;
	}
	
	
	
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
		 * have finished --- why I wonder?
		 */

		prevCoordinator.inform(component, currentState, disabledPorts);
		// System.out.println((System.currentTimeMillis() - time1));
	}
	
	private void doInformSpecific(BIPComponent component, String currentState, Set<Port> disabledPorts) {
		// it is either called at the last inform, or at each inform.
		Set<Port> resourceRequestingPorts = getRequestingPort(component, currentState, disabledPorts); // ports
		Behaviour decidingBehaviour = componentBehaviourMapping.get(component);
		// for each undecided port of each component :
		for (Port port : resourceRequestingPorts) {
			String request = decidingBehaviour.getRequest(port);
			String interactionID = "1";
			// TODO create a assembled utility for the requests if needed
			try {
				resourceHelper.specifyRequest(request, interactionID);
				if (!resourceHelper.canAllocate(interactionID)) {
					resourceEncoder.encodeDisabledCombinations(component, port, null);
				}
			} catch (DNetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		
	}

		private Set<Port> getRequestingPort(BIPComponent component,
				String currentState, Set<Port> disabledPorts) {
			Set<Port> undecidedPorts = new HashSet<Port>();
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
				if (!portIsDisabled && behaviour.getPortsRequestingResources().contains(port)) {
					undecidedPorts.add(port);
				}
				portIsDisabled = false;
			}
			logger.trace("For component {} the undecided ports are {}. ",
					component.getId(), undecidedPorts);
			return undecidedPorts;
		}

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

		bipCoordinator.specifyTemporaryConstraints(resourceEncoder.encodeDisabledCombinations(decidingComponent,
				decidingPort, disabledCombinations));

	}
	
	public void execute(byte[] valuation) throws BIPEngineException {
		if (interactionExecutor != this && isEngineExecuting) {
			interactionExecutor.execute(valuation);
		} else if (isEngineExecuting) {
			executeInteractions(preparePorts(valuation));
		}
		logger.debug("*************************************************************************");
	}

	private List<List<Port>> preparePorts(byte[] valuation) {
		/*
		 * Grouping the interaction into smaller ones (with respect to data transfer)
		 */
		// Map<BIPComponent, Iterable<Port>> chosenPorts = new Hashtable<BIPComponent,
		// Iterable<Port>>();
		// logger.trace("positionsOfDVariables size: " + positionsOfDVariables.size());
		ArrayList<Port> portsExecuted = new ArrayList<Port>();
		ArrayList<Port> portsReqResources = new ArrayList<Port>();
		List<List<Port>> bigInteraction = null;
		Map<Port, Integer> portToPosition = bipCoordinator.getPortsToPosition();
		for (Port port : portToPosition.keySet()) {
			if (valuation[portToPosition.get(port)] == 1 || valuation[portToPosition.get(port)] == -1) {
				portsExecuted.add(port);
			}
		}
		logger.trace("chosenPorts size: " + portsExecuted.size());
		if (portsExecuted.size() != 0) {
			bigInteraction.add(portsExecuted);
		}
		
		for (Port port: portsExecuted) {
			// if the chosen port releases resources, release its resources.
			if (portsReleasingResource.contains(port)) {
				String amounts = "0";//port.component().getReleasedAmounts(port);
				String resourceName = "";
				//TODO parse amounts to get resource names and numbers
				//for each resource
				resourceNameToManagers.get(resourceName).augmentCost(amounts);
			}
			// if the chosen port requests resources
			if (portsRequestingResource.contains(port)) {
				portsReqResources.add(port);
				// somehow find other ports in the interaction in order to know which solution exactly to use....
			}
		}
		
		//at this stage we have found all the ports that require resources
		// now, either ask if allocation possible, or extract the saved allocation.
		// but first, get the combined utility
		String globalUtility = getInteractionUtility(portsReqResources);
		
		/*
		 * Find ports that participate in the interaction but not in data transfer and add them as a
		 * separate group
		 */
		ArrayList<Port> enabledPorts = new ArrayList<Port>();
		//Map<Port, Integer> portToPosition = bipCoordinator.getBehaviourEncoderInstance().getPortToPosition();
		ArrayList<BIPComponent> componentsEnum = registeredComponents;
		for (BIPComponent component : componentsEnum) {
			Iterable<Port> componentPorts = null;
			Behaviour behaviour = componentBehaviourMapping.get(component);
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
		 * Here the ports mentioned above have been added For debug only 
		 * //TODO: Comment out before performance evaluation
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

	private String getInteractionUtility(ArrayList<Port> portsReqResources) {
		for (Port port: portsReqResources)
		{
			String request = componentBehaviourMapping.get(port.component()).getRequest(port);
			String interactionID = "0";
			try {
				resourceHelper.specifyRequest(request, interactionID);
			} catch (DNetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
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
			//TODO shouldn't it be the prevCoordinator here?
			bipCoordinator.executeInteractions(portGroupsToExecute);
	}

	@Override
	public void registerResourceProvider(ResourceProvider provider) {
		// TODO Auto-generated method stub
		
	}

	public void setInteractionExecutor(InteractionExecutor interactionExecutor) {
		this.interactionExecutor = interactionExecutor;

	}
	
}
