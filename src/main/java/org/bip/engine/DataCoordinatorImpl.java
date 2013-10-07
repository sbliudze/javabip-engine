package org.bip.engine;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.api.Behaviour;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;
import org.bip.glue.BIPGlue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// There is no need for DataCoordinator interface, just DataCoordinatorImpl will implement BIP engine interface.
// DataCoordinatorImpl needs BIP coordinator ( BIP Engine again ) that actual does all the computation of BIP
// engine. DataCoordinatorImpl takes care of only of data querying and passing to BIP executors.

// TODO
// DataCoordinator intercepts call register and inform from BIPExecutor. For each BIPComponent it
// creates a Proxy of BIPComponent (also BIPComponent class) that is registered with BIPCoordinator.
// This BIPComponent proxy is the identity provided to BIPCordinator. Now, DataCordinator implements
// just BIPEngine interface so it able also to intercept informs and translate it into proper informs.
// BIPComponent Proxy can intercept execute functions invoked by BIPCoordinator and enrich with data
// provided by DataCoordinatorImpl. Thus, Proxy BIPComponent knows about DataCoordinatorImpl, and
// original BIPcomponent, so BIPcomponent proxy can query DataCoordinatorIMpl for the data and call
// function execute of the original BIPComponent with proper data.

public class DataCoordinatorImpl implements BIPEngine, InteractionExecutor {

	private Logger logger = LoggerFactory.getLogger(BIPCoordinatorImpl.class);

	private ArrayList<BIPComponent> registeredComponents = new ArrayList<BIPComponent>();

	/**
	 * Helper hashtable with integers representing the local identities of
	 * registered components as the keys and the Behaviours of these components
	 * as the values.
	 */
	private Hashtable<BIPComponent, Behaviour> componentBehaviourMapping = new Hashtable<BIPComponent, Behaviour>();

	/**
	 * Create instances of all the the Data Encoder and of the BIPCoordinator
	 */
	private DataEncoder dataEncoder = new DataEncoderImpl();
	private BIPCoordinator BIPCoordinator = new BIPCoordinatorImpl();
	private BDDBIPEngine engine = new BDDBIPEngineImpl();

	public DataCoordinatorImpl() {
		dataEncoder.setEngine(engine);
		BIPCoordinator.setInteractionExecutor(this);
	}

	public void specifyGlue(BIPGlue glue) {
		BIPCoordinator.specifyGlue(glue);
	}

	public void register(BIPComponent component, Behaviour behaviour) {
		/*
		 *  The condition below checks whether the component has already been registered.
		 */
		if (registeredComponents.contains(component)){
			try {
				logger.error("Component "+component.getName()+" has already registered before.");
				throw new BIPEngineException("Component "+component.getName()+" has already registered before.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}
		} 
		else {				
			registeredComponents.add(component);		
			componentBehaviourMapping.put(component, behaviour);
			BIPCoordinator.register(component, behaviour);
		}
	}

	public void inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) {
		// received inform about globally disabled ports for one component
		// now we know which ports have not decided on their availability
		// after all informs (? easy version)
		// for each undecided port of each component :
		// port -> transition -> guard expression -> guard tree -> tree of Guards -> list of DataIn needed
		// for each DataIn variable get from the new Glue info about components providing it
		// executor.getData()
		// executor.checkEnabledness()
		// inform the BIPCoordinator
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

	public void informSpecific(Map<BIPComponent, Port> disabledCombinations) throws BIPEngineException {
		if (disabledCombinations.isEmpty()){
			try {
				logger.error("No disabled combination specified in informSpecific. Map of disabledCombinations is empty.");
				throw new BIPEngineException("No disabled combination specified in informSpecific. Map of disabledCombinations is empty.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}	
		}
		else{
			Iterator <BIPComponent> disabledComponents = disabledCombinations.keySet().iterator();
			while (disabledComponents.hasNext()){
				BIPComponent component = disabledComponents.next();
				if (!registeredComponents.contains(component)){
					logger.error("Component "+component.getName()+" specified in the disabledCombinations of informSpecific was not registered.");
					throw new BIPEngineException("Component "+component.getName()+" specified in the disabledCombinations of informSpecific was not registered.");
				} 
			}
			//TODO: Throw exceptions about the ports in the DataEncoder (?)
			engine.informSpecific(dataEncoder.informSpecific(disabledCombinations));
//			dataEncoder.informSpecific(disabledCombinations);
		}
	}
	
	/**
	 * BDDBIPEngine informs the BIPCoordinator for the components (and their associated ports) that are part of the same chosen interaction.
	 * 
	 * Through this function all the components need to be notified.
 	 * If they are participating in an interaction then their port to be fired is sent to them through the execute function of the BIPExecutor.
	 * If they are not participating in an interaction then null is sent to them.
	 * @throws BIPEngineException 
	 */
	
	//TODO: when changes in Engine are finished test it and delete executeComponent
	public void executeInteractions(Iterable<Map<BIPComponent, Iterable<Port>>> portsToFire) throws BIPEngineException {
		Iterator <Map<BIPComponent, Iterable<Port>>> enabledCombinations = portsToFire.iterator();
		/*
		 * This is a list of components participating in the chosen-by-the-engine interactions. 
		 * This keeps track of the chosen components in order to differentiate them from the non chosen ones. 
		 * Through this function all the components need to be notified. Either by sending null to them or the 
		 * port to be fired.
		 */
		ArrayList <BIPComponent> enabledComponents = new ArrayList<BIPComponent>();
		while (enabledCombinations.hasNext()){
			Map <BIPComponent, Iterable<Port>> oneInteraction = enabledCombinations.next();
			Iterator <BIPComponent> interactionComponents = oneInteraction.keySet().iterator();
			while(interactionComponents.hasNext()){
				BIPComponent component = interactionComponents.next();
				enabledComponents.add(component);
				Iterator<Port> compPortsToFire = oneInteraction.get(component).iterator();
				/*
				 * If the Iterator<Port> is null or empty for a chosen component, throw an exception.
				 * This should not happen.
				 */
				if (compPortsToFire == null || !compPortsToFire.hasNext()) {
					try {
						logger.error("In a chosen by the engine interaction, associated to component "+ component.getName() +
								" is a null or empty list of ports to be fired.");
						throw new BIPEngineException("Exception in thread: "+Thread.currentThread().getName()+" In a chosen by the engine interaction, associated to component "+ component.getName() +
								" is a null or empty list of ports to be fired.");
					} catch (BIPEngineException e) {
						e.printStackTrace();
						throw e;
					}
				}
				else{
					while (compPortsToFire.hasNext()){
						Port port = compPortsToFire.next();
						/*
						 * If the port is null or empty for a chosen component, throw an exception.
						 * This should not happen.
						 */
						if (port == null || port.id.isEmpty()) {
							try {
								logger.error("In a chosen by the engine interaction, associated to component "+ component.getName() +
										" the port to be fired is null or empty.");
								throw new BIPEngineException("Exception in thread: "+Thread.currentThread().getName()+" In a chosen by the engine interaction, associated to component "+ component.getName() +
										" the port to be fired is null or empty.");
							} catch (BIPEngineException e) {
								e.printStackTrace();
								throw e;
							}
						}
						logger.debug("Component {} execute port {}", component.getName(), port.id);
						//TODO: Find out which components are sending data to this component
						//TODO: Change the following execute to the one that specifies data for execution of transitions. In particular, change this:
						component.execute(port.id);
						//to this:
					    // void execute(String portID, Map<String, ?> data);
					}
				}
			}	
		}
		/* 
		 * send null to the components that are not part of the overall interaction
		 */
		for (BIPComponent component: registeredComponents){
			if (!enabledComponents.contains(component)){
				component.execute(null);
			}
		}
	}

}
