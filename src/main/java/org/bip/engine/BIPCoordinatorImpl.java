package org.bip.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Semaphore;

import net.sf.javabdd.BDD;

import org.bip.api.*;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;
import org.bip.glue.BIPGlue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the execution of the behaviour, glue and current state encoders.
 * At the initialization phase, it receives information about the behaviour of 
 * BIP components sends this to the behaviour encoder and orders it to compute the total behaviour BDD.
 * At the initialization phase, it also orders the glue encoder to compute the glue BDD. During each execution cycle, it receives information about 
 * the current state of the BIP components and their disabled ports, sends this to the current state encoder 
 * and orders it to compute the current state BDDs. When a new interaction is chosen by the engine, it notifies all the BIP components.
 * @author mavridou
 */
public class BIPCoordinatorImpl implements BIPCoordinator, Runnable {

	private Logger logger = LoggerFactory.getLogger(BIPCoordinatorImpl.class);
	/**
	 * Create instances of all the the Glue Encoder, the Behaviour Encoder, 
	 * the Current State Encoder, the Symbolic BIP Engine
	 */
	private GlueEncoder glueenc = new GlueEncoderImpl();
	private BehaviourEncoder behenc = new BehaviourEncoderImpl();
	private CurrentStateEncoder currstenc = new CurrentStateEncoderImpl();
	private BDDBIPEngine engine = new BDDBIPEngineImpl();
	private InteractionExecutor interactionExecutor;
	
	Thread currentThread = null;

	private ArrayList <BIPComponent> registeredComponents = new ArrayList <BIPComponent> ();
	
	/**
	 * Helper hashtable with integers representing the local identities of registered components 
	 * as the keys and the Behaviours of these components as the values.
	 */
	private Hashtable<BIPComponent, Behaviour> componentBehaviourMapping = new Hashtable<BIPComponent, Behaviour>();
	
	/**
	 * Helper hashtable with strings as keys representing the component type of the registered components
	 * and ArrayList of BIPComponent instances that correspond to the component type specified in the key.
	 */
	private Hashtable<String, ArrayList<BIPComponent>> typeInstancesMapping = new Hashtable<String, ArrayList<BIPComponent>>();
	/**
	 * Helper hashset of the components that have informed in an execution cycle.
	 */
	private HashSet<BIPComponent> componentsHaveInformed = new HashSet<BIPComponent>();

	/** Number of ports of components registered */
	private int nbPorts;

	/** Number of states of components registered */
	private int nbStates;

	/** Number of components registered */
	public int nbComponents;

	/** Thread for the BIPCoordinator*/
	private Thread engineThread;

	/** 
	 * Boolean variable that shows whether the execute() was called.
	 */
	private boolean isEngineExecuting = false;
	
	/**
	 * Boolean field that shows whether the haveAllComponentsInformed semaphore is acquired
	 * for the number of components and therefore, the components can start informing the engine 
	 * and releasing the semaphore.
	 */
	private boolean isEngineSemaphoreReady = false;
	
	/**
	 * Semaphore that controls when the runOneIteration() function of the BDDBIPEngine 
	 * class can be called. It can be called after all registered components 
	 * have inform the BIPCoordinator about their current state.
	 */
	private Semaphore haveAllComponentsInformed;

	public BIPCoordinatorImpl() {

		//TODO: simplify dependencies
		glueenc.setBehaviourEncoder(behenc);
		glueenc.setEngine(engine);
		glueenc.setBIPCoordinator(this);

		behenc.setEngine(engine);
		behenc.setBIPCoordinator(this);

		currstenc.setBehaviourEncoder(behenc);
		currstenc.setEngine(engine);
		currstenc.setBIPCoordinator(this);

		engine.setOSGiBIPEngine(this);
	}

	public synchronized void specifyGlue(BIPGlue glue) {
		try {
			glueenc.specifyGlue(glue);
		} catch (BIPEngineException e) {
			e.printStackTrace();
		}
	}
	
	private synchronized void orderGlueEncoderToComputeTotalGlueAndInformEngine() throws BIPEngineException{
		engine.informGlue(glueenc.totalGlue());
	}
	
	/**
	 * When the registration of components has finished, order engine to compute the total Behaviour BDD.
	 * 
	 * Currently, this method is called once in the run() but later, on the deletion of components on the fly
	 * the engine should be re-ordered to compute the total Behaviour BDD. Not that, in case of insertion of
	 * components this function need not be called, since we can just take the conjunction of the previous total 
	 * Behaviour BDD and the Behaviour BDD representing the new component to compute the new total Behaviour BDD.
	 * @throws BIPEngineException 
	 */
	private synchronized void orderEngineToComputeTotalBehaviour() throws BIPEngineException {
		engine.totalBehaviourBDD();
	}

	public synchronized void register(BIPComponent component, Behaviour behaviour) {
		
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
			logger.info("********************************* Register *************************************");
			
			/*
			 * Map all component instances of the same type in the typeInstancesMapping Hashtable
			 */
			ArrayList<BIPComponent> componentInstances = new ArrayList<BIPComponent> ();
			
			/*
			 * If this component type already exists in the hashtable, update the ArrayList of BIPComponents
			 * that corresponds to this component type.
			 */
			if (typeInstancesMapping.containsKey(component.getName())){
				componentInstances.addAll(typeInstancesMapping.get(component.getName()));
			}
			
			componentInstances.add(component);
			typeInstancesMapping.put(component.getName(), componentInstances);
			registeredComponents.add(component);

			/*
			 * Keep the local ID for now, but use OSGI IDs later
			 */
			logger.info("Component : {}", component.getName());
			
			componentBehaviourMapping.put(component, behaviour);
			int nbComponentPorts = ((ArrayList<Port>)behaviour.getEnforceablePorts()).size();
			int nbComponentStates = ((ArrayList<String>)behaviour.getStates()).size();
	
			try {
				behenc.createBDDNodes(component, ((ArrayList<Port>)behaviour.getEnforceablePorts()), ((ArrayList<String>)behaviour.getStates()));
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}
			try {
				engine.informBehaviour(component, behenc.behaviourBDD(component));
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}

			for (int i = 0; i < nbComponentPorts; i++) {
				//TODO: use one of the two in the BDDEngine
				engine.getPositionsOfPorts().add(nbPorts + nbStates + nbComponentStates + i);
				engine.getPortToPosition().put(((ArrayList<Port>)behaviour.getEnforceablePorts()).get(i), nbPorts + nbStates + nbComponentStates + i);
			}
			nbPorts += nbComponentPorts;
			nbStates += nbComponentStates;
			nbComponents++;
			logger.info("******************************************************************************");
		}
	}
	
	
	/**
	 * Components call the inform function to give information about their current state and their number of
	 * disabled ports by guards that do not have to do with data transfer.
	 * 
	 * If the guards of a transition do not have information valuable for data transfer then only this inform is called for a particular component.
	 * Otherwise, also the other inform function is called.
	 */
	public synchronized void inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) {
		if (componentsHaveInformed.contains(component)) {
			try {
				logger.info("************************ Already Have Informed *******************************");
				logger.info("Component: {}", component.getName());
				logger.info("informs that is at state: {}", currentState);
				for (Port disabledPort : disabledPorts){
					logger.info("with disabled port: "+disabledPort.id);
				}
				logger.info("******************************************************************************");
				logger.error("Component "+ component.getName() +" has already informed the engine in this execution cycle.");
				throw new BIPEngineException("Component "+ component.getName() +" has already informed the engine in this execution cycle.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}
		} 
		
		/*
		 * If a component informs more than once in the same execution cycle we add the else below to prevent the 
		 * re-computation of the current state BDD for the specific component. The  deletion of the else will not
		 * result in any data corruption but overhead will be added.
		 */
		// TODO: After code review for the current state encoder, remove the else
		else { 
	
			/** 
			 * This condition checks whether the component has already registered.
			 */
			if (registeredComponents.contains(component)){
				synchronized (componentsHaveInformed) {
					componentsHaveInformed.add(component);
					try {
						engine.informCurrentState(component, currstenc.inform(component, currentState, disabledPorts));
					} catch (BIPEngineException e) {
						e.printStackTrace();
					}
		
					logger.debug("Number of components that have informed {}", componentsHaveInformed.size());
					logger.info("********************************* Inform *************************************");
					logger.info("Component: {}", component.getName());
					logger.info("informs that is at state: {}", currentState);
					for (Port disabledPort : disabledPorts){
						logger.info("with disabled port: "+disabledPort.id);
					}
					logger.info("******************************************************************************");
	
					/*
					 * The haveAllComponentsInformed semaphore is used to indicate whether all registered components
					 * have informed and to order one execution cycle of the engine. The semaphore is acquired in run().
					 * 
					 * When a component informs, we first check if the haveAllComponentsInformed semaphore has been acquired before
					 * and then we release.
					 *  
					 * This block is synchronized with the number of components that have informed. Therefore, the haveAllComponentsInformed
					 * semaphore cannot be released by any other component at the same time.   
					 */
					// TODO: If we remove the else above, we have to make sure that the semaphore is not released for the second time 
					if (isEngineSemaphoreReady){
						haveAllComponentsInformed.release();
						logger.debug("Number of available permits in the semaphore: {}", haveAllComponentsInformed.availablePermits());
						if (haveAllComponentsInformed.availablePermits()==0){
							//TODO: inform DataEncoder that all informSpecific have finished
						}
					}
				}
				/**
				 * An exception is thrown when a component informs the Coordinator without being registered first.
				 */
			} else {
				try {
					logger.error("Component " +component.getName()+" has not registered yet.");
					throw new BIPEngineException("Component " +component.getName()+" has not registered yet.");
				} catch (BIPEngineException e) {
					e.printStackTrace();					
				}
			}
		}
	}


	/**
	 * BDDBIPEngine informs the BIPCoordinator for the components (and their associated ports) that are part of the chosen interactionS.
	 */
	//TODO: after finishing with the changes in the BDDBIPEngine this function should not be called any more.
	public synchronized void executeComponents(ArrayList<BIPComponent> allComponents, Hashtable<BIPComponent, ArrayList<Port>> portsToFire) {
		Port port = null;
		int size = allComponents.size();
		
		for (int i = 0; i < size; i++) {
			BIPComponent component = allComponents.get(i);
			ArrayList<Port> compPortsToFire = portsToFire.get(component);
			
			if ((compPortsToFire != null) && (!compPortsToFire.isEmpty())) {
				port = compPortsToFire.get(0);
				assert(port != null);
				logger.debug("Component {} execute port {}", component.getName(), port.id);
				component.execute(port.id);
			}
			else{
				logger.debug("BIPCoordinator sends null to BIPComponent: "+component.getName());
				component.execute(null);
			}
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
	//TODO: when changes in Engine are finished TEST it
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
						component.execute(port.id);
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
	
	/**
	 * Initialization phase. Orders the Behaviour and Current State Encoders to compute their total BDDs 
	 * and send these to the BDDBIPEngine. 
	 * @throws BIPEngineException 
	 * @throws InterruptedException 
	 */
	private void coordinatorCycleInitialization() throws BIPEngineException{
		/*
		 * Wait until the execute() has been called signaling that all the components have registered 
		 */	
		synchronized (this) { 
			while (!isEngineExecuting) {
				try {
					logger.debug("Waiting for the engine execute to be called...");
					wait();
					logger.debug("Waiting for the engine execute done.");
				} catch (InterruptedException e) {
					logger.warn("Engine run is interrupted: {}", Thread.currentThread().getName());
				}	
			}
		}
			
		/*
		 * For the moment, all components must be registered before execute() is called.  Therefore the engine might
		 * as well quit if the following test fails.  However, in the future we want components to be able to 
		 * register and unregister on the fly.  In this case running the engine with no registered components becomes
		 * legitimate.
		 */
		if (nbComponents == 0) {
			logger.error("Thread started but no components have been registered yet.");
		}
		
		/*
		 * To order the engine to begin its execution cycle we need to know first whether all components have informed
		 * the BIP Coordinator about their current state. For this reason, the semaphore haveAllComponentsInformed is used
		 * that it is initialized  here with the number of registered components in the system. Note that, if components
		 * can be registered and unregistered on the fly the semaphore has to be updated with the new number of components
		 * in the system.
		 */
		/*
		 * Acquire permits for the number of registered components, which have not informed about their current state yet.
		 * NB: Components may have inform the BIPCoordinator before the execute() is called
		 */
		synchronized (componentsHaveInformed) {
			haveAllComponentsInformed= new Semaphore(nbComponents);
			try {
				logger.debug("Waiting for the engine semaphore to be initialized to 0...");
				haveAllComponentsInformed.acquire(nbComponents);
				isEngineSemaphoreReady = true;
				logger.debug("Engine semaphore initialised");
			} catch (InterruptedException e1) {
				logger.error("Semaphore's haveAllComponentsInformed acquire method for the number of registered components in the system was interrupted.");
				e1.printStackTrace();		
			}
		}
		
		try {
			logger.debug("Waiting for the cycle initialisation acquire...");
			haveAllComponentsInformed.acquire(nbComponents - componentsHaveInformed.size());
			logger.debug("The cycle initialisation acquire successful");
		} catch (InterruptedException e1) {
			logger.error("Semaphore's haveAllComponentsInformed acquire method for the number of components that still have to inform was interrupted.");
			e1.printStackTrace();
		}

		/* 
		 * Compute behaviour and glue BDDs with the components that have registered before the call to execute(). 
		 * If components were to register after the call to execute() these BDDs must be recomputed accordingly.
		 */
		orderEngineToComputeTotalBehaviour();
		orderGlueEncoderToComputeTotalGlueAndInformEngine();
	}
	
	public void run(){
		logger.info("Engine thread is started.");
		
		try {
			coordinatorCycleInitialization();
		} catch (BIPEngineException e1) {
			e1.printStackTrace();
			isEngineExecuting=false;
			engineThread.interrupt();
		}
		/**
		 * Start the Engine cycle
		 */
		while (isEngineExecuting) {

			logger.debug("isEngineExecuting: {} ", isEngineExecuting);
			logger.debug("noComponents: {}, componentCounter: {}", nbComponents, componentsHaveInformed.size());
			logger.debug("Number of available permits in the semaphore: {}", haveAllComponentsInformed.availablePermits());
			
			componentsHaveInformed.clear();
			try {
				engine.runOneIteration();
			} catch (BIPEngineException e1) {
				e1.printStackTrace();
			}

			try {
				logger.debug("Waiting for the acquire in run()...");
				haveAllComponentsInformed.acquire(nbComponents);
				logger.debug("run() acquire successful.");
			} catch (InterruptedException e) {
				isEngineExecuting = false;
				e.printStackTrace();
				logger.error("Semaphore's haveAllComponentsInformed acquire method for the number of registered components in the system was interrupted.");
			}	
		}
		
		// TODO: unregister components and notify the component that the engine is not working
		//for (BIPComponent component : identityMapping.values()) {
		//	component.deregister();
		//}
		componentBehaviourMapping.clear();
		componentsHaveInformed.clear();
		return;
	}
	
	/**
	 * Create a thread for the BIPCoordinator and start the thread
	 */
	public void start() {
		engineThread = new Thread(this, "BIPEngine");
		engineThread.start();
	}

	public void stop() {
		isEngineExecuting = false;
		engineThread.interrupt();
	}

	/**
	 * If the execute function is called then set to true the boolean isEngineExecuting and notify the waiting threads. 
	 * If the execute function is called more than once then it complains. 
	 * 
	 * We do not allow the case that execute is called twice simultaneously by surrounding its code by synchronized(this).
	 * 
	 * At this implementation, we assume that components have been registered before the execute() is called and therefore 
	 * we are aware of the number of components in the system and can initialize the semaphore that shows whether all 
	 * registered components have informed. In future, that components may be able to register on the fly the semaphore needs 
	 * be re-initialized with the new number of components. If a component unregisters from the system then we can use the 
	 * reducePermits(int reduction) on the semaphore to shrink the number of available permits by the indicated reduction. 
	 * 
	 * We also check here if the interactionExecutor has been set to DataCoordinator. Otherwise set it to BIPCoordinator.				
	 */
	public void execute() {
		if (isEngineExecuting) {
			logger.warn("Execute() called more than once");
		}	
		else {
			synchronized (this){
				isEngineExecuting = true;
				notifyAll();
				if (this.interactionExecutor == null){
					setInteractionExecutor(this);
				}
			}
		}
	}
	
	/**
	 * This function should not do anything but give a warning.
	 * 
	 * BIPCoordinator and DataCoordinator both implement the BIPEngine interface, where the 
	 * informSpecific function is. DataCoordinator is responsible for sending the disabledCombinations 
	 * of the informSpecific directly to the DataEncoder. The BIPCoordinator should not participate in this.
	 */

//	public void informSpecific(BIPComponent decidingComponent, Port decidingPort, Map<BIPComponent, Iterable<Port>> disabledCombinations) throws BIPEngineException {
//		logger.warn("InformSpecific of BIPCoordinator is called. That should never happen. All the information should be passed directly from the DataCoordinator to the DataEncoder.");
//	}
	
	public void informSpecific(BIPComponent decidingComponent, Port decidingPort, Iterable<BIPComponent> disabledComponents) throws BIPEngineException {
		logger.warn("InformSpecific of BIPCoordinator is called. That should never happen. All the information should be passed directly from the DataCoordinator to the DataEncoder.");
	}
	
	public void informSpecific(BDD disabledCombination){
		engine.informSpecific(disabledCombination);
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
	
	/**
	 * Helper function that returns the number of registered components in the system.
	 */
	public int getNoComponents() {
		return nbComponents;
	}
	
	/**
	 * Helper function that given a component returns the corresponding behaviour as a Behaviour Object.
	 */
	public Behaviour getBehaviourByComponent(BIPComponent component) {
		return componentBehaviourMapping.get(component);
	}
	
	public BehaviourEncoder getBehaviourEncoderInstance(){
		return behenc;
	}
	
	public void setInteractionExecutor(InteractionExecutor interactionExecutor){
		this.interactionExecutor = interactionExecutor;
	}
	/**
	 * Helper function that returns the registered component instances that correspond to a component type.
	 * @throws BIPEngineException 
	 */
	public Iterable<BIPComponent> getBIPComponentInstances(String type) throws BIPEngineException{
		ArrayList<BIPComponent> instances = typeInstancesMapping.get(type);
		if (instances == null){
			try {
				logger.error("No registered component instances for the: {} ",type +
						" component type. Possible reasons: The name of the component instances was specified in another way at registration.");
				throw new BIPEngineException("Exception in thread "+Thread.currentThread().getName()+" No registered component instances for the component type: " + type + 
						"Possible reasons: The name of the component instances was specified in another way at registration.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		return instances; 
	}



	
}
