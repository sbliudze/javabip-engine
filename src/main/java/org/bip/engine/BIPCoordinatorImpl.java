package org.bip.engine;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

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

	private Hashtable<BIPComponent, Integer> reversedIdentityMapping = new Hashtable<BIPComponent, Integer>();
	private Hashtable<Integer, BIPComponent> identityMapping = new Hashtable<Integer, BIPComponent>();
	private Hashtable<Integer, Behaviour> behaviourMapping = new Hashtable<Integer, Behaviour>();
	private HashSet<BIPComponent> componentsHaveInformed = new HashSet<BIPComponent>();

	/** Identification number for local use */
	private AtomicInteger idGenerator = new AtomicInteger(0);

	/** Number of ports of components registered */
	private int nbPorts;

	/** Number of states of components registered */
	private int nbStates;

	/** Number of components registered */
	public int nbComponents;

	private Thread engineThread;

	private boolean isEngineExecuting;
	
	private Semaphore haveAllComponentsInformed;

	public BIPCoordinatorImpl() {

		// redirectSystemErr();
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

	/**
	 * We require to have components registered before glue is specified, so
	 * functions of BehEnc are executed properly.
	 */
	public synchronized void specifyGlue(BIPGlue glue) {
		glueenc.specifyGlue(glue);
		// engine.informGlue(glueenc.specifyGlue()); // This assumes that glueenc.specifyGlue() returns the reference to the glue BDD
		computeGlueAndInformEngine();
		computeTotalBehaviourAndInformEngine();
	}

	public synchronized void computeTotalBehaviourAndInformEngine() {
		//TODO: send one by one (insertion and deletion)
		engine.informTotalBehaviour(behenc.totalBehaviour());
	}

	public synchronized void computeGlueAndInformEngine() {
		engine.informGlue(glueenc.totalGlue());
	}

	public synchronized void register(BIPComponent component, Behaviour behaviour) {
		/**
		 *  This condition checks whether the component has already registered.
		 */
		if (reversedIdentityMapping.contains(component)) {
			try {
				throw new BIPEngineException("Component has already registered before.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				logger.error(e.getMessage());
			}
		} else {
			logger.info("********************************* Register *************************************");
	
			// atomically adds one
			int registeredComponentID = idGenerator.getAndIncrement(); 
	
			reversedIdentityMapping.put(component, registeredComponentID);
			logger.info("Component: {} ", component.getName());
			identityMapping.put(registeredComponentID, component);
			behaviourMapping.put(registeredComponentID, behaviour);
			int nbComponentPorts = ((ArrayList<Port>)behaviour.getEnforceablePorts()).size();
			int nbComponentStates = ((ArrayList<String>)behaviour.getStates()).size();
	
			behenc.createBDDNodes(registeredComponentID, nbComponentPorts, nbComponentStates);
			// TODO: compute BDD and send to engine (replaces the call to informTotalBehaviour() in specifyGlue() )

			// TODO: (minor) think whether a better data structure is possible for associating the variable 
			// position to a port.  To access the position defined below one has to first obtain the corresponding
			// index in the engine.getPositionsOfPorts() array.  Is it possible to get the position directly instead
			// of passing through this index?
			for (int i = 0; i < nbComponentPorts; i++) {
				engine.getPositionsOfPorts().add(nbPorts + nbStates + nbComponentStates + i);
			}
			nbPorts += nbComponentPorts;
			nbStates += nbComponentStates;
			nbComponents++;
			logger.info("******************************************************************************");
		}
	}
	/**
	 * Components call the inform function to give information about their current state and their number of
	 * disabled ports by guards.
	 */

	public synchronized void inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) {
		if (componentsHaveInformed.contains(component)) {
			try {
				logger.info("************************** Second Inform *************************************");
				logger.info("Component: {}", component.getName());
				logger.info("informs that is at state: {}", currentState);
				logger.info("******************************************************************************");

				throw new BIPEngineException("Component has already informed the engine in this execution cycle.");
			} catch (BIPEngineException e) {
				logger.error(e.getMessage());
			}
		} 
		
		/**
		 * If a component informs more than once in the same execution cycle we add the else below to prevent the 
		 * re-computation of the current state BDD for the specific component. The  deletion of the else will not
		 * result in any data corruption but overhead will be added.
		 */
		else { 
	
			/** 
			 * This condition checks whether the component has already registered.
			 */
			if (reversedIdentityMapping.containsKey(component)) { 
				componentsHaveInformed.add(component);
				engine.informCurrentState(component, currstenc.inform(component, currentState, disabledPorts));
	
				logger.debug("Number of components that have informed {}", componentsHaveInformed.size());
				logger.info("********************************* Inform *************************************");
				logger.info("Component: {}", component.getName());
				logger.info("informs that is at state: {}", currentState);
				logger.info("******************************************************************************");

				/**
				 * The haveAllComponentsInformed semaphore is used to indicate whether all registered components
				 * have informed and to order one execution cycle of the engine. The semaphore is initialised with the
				 * number of registered components. When a component informs we release one permit of the semaphore.
				 */
				if (isEngineExecuting){
					haveAllComponentsInformed.release();
				}

				/**
				 * An exception is thrown when a component informs the Coordinator without being registered first.
				 */
			} else {
				try {
					throw new BIPEngineException("Component has not registered yet.");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					logger.error(e.getMessage());
				}
			}
		}
	}

	public synchronized void executeComponents(ArrayList<BIPComponent> allComponents, Hashtable<BIPComponent, ArrayList<Port>> portsToFire) {
		Port port = null;
		int size = allComponents.size();
		
		for (int i = 0; i < size; i++) {
			BIPComponent comp = allComponents.get(i); // Better to use an iterator
			ArrayList<Port> compPortsToFire = portsToFire.get(comp);
			
			if ((compPortsToFire != null) && (!compPortsToFire.isEmpty())) {
				port = compPortsToFire.get(0);
				assert(port != null);
				comp.execute(port.id);
			}
			else
				comp.execute(null);
		}
	}

	public void run() {
//		/**
//		 * This should never happen in this implementation because the glue encoder is created in the constructor.
//		 * However, one could imagine a situation where the glue encoder be obtained by calling a function that 
//		 * could return null.
//		 */
//		if (glueenc.totalGlue() == null) {
//			logger.info("Total Glue BDD is null");
//			try {
//				throw new BIPEngineException("Glue BDD is null after execute");
//			} catch (BIPEngineException e) {
//				e.printStackTrace();
//				logger.error("Total Glue BDD is null");
//			}
//		}
		logger.info("Engine thread is started.");
		
		/**
		 * Wait until the execute() has been called signaling that all the components have registered 
		 */	
		synchronized (this){ 
			if (!isEngineExecuting ) {
				try {
					wait();
				} catch (InterruptedException e) {
					logger.warn("Engine run is interrupted: {}", Thread.currentThread().getName());
				}	
			}
		}
		
		/**
		 * For the moment, all components must be registered before execute() is called.  Therefore the engine might
		 * as well quit if the following test fails.  However, in the future we want components to be able to 
		 * register and unregister on the fly.  In this case running the engine with no registered components becomes
		 * legitimate.
		 */
		if (nbComponents == 0) {
			logger.error("Thread started but no components have been registered yet.");
		}
		
		/**
		 * To order the engine to begin its execution cycle we need to know first whether all components have informed
		 * the BIP Coordinator about their current state. For this reason, the semaphore haveAllComponentsInformed is used
		 * that it is initialized  here with the number of registered components in the system. Note that, if components
		 * can be registered and unregistered on the fly the semaphore has to be updated with the new number of components
		 * in the system.
		 */
		haveAllComponentsInformed= new Semaphore(nbComponents);
		

		/**
		 * Acquire permits for the number of registered components.
		 */
		try {
			haveAllComponentsInformed.acquire(nbComponents);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
			logger.error("Semaphore have all components informed acquire method for the number of registered components in the system was interrupted.");
		}
		
		/**
		 * Components may have inform the BIPCoordinator before the execute() is called
		 * According to the size of the components informed we release some of the permits of the semaphore.
		 */
		haveAllComponentsInformed.release(componentsHaveInformed.size());

		while (isEngineExecuting) {

			logger.debug("isEngineExecuting: {} ", isEngineExecuting);
			logger.debug("noComponents: {}, componentCounter: {}", nbComponents, componentsHaveInformed.size());
			logger.debug("Number of available permits in the semaphore: {}", haveAllComponentsInformed.availablePermits());
			
			if (haveAllComponentsInformed.availablePermits()==nbComponents){
				try {
					haveAllComponentsInformed.acquire(nbComponents);
				} catch (InterruptedException e) {
					e.printStackTrace();
					logger.error("Semaphore have all components informed acquire method for the number of registered components in the system was interrupted.");
				}	
				componentsHaveInformed.clear();
				engine.runOneIteration();
			} 
		}
		
		//TODO: for (BIPComponent component : identityMapping.values()) {
		//	component.deregister();
		//}
		reversedIdentityMapping.clear();
		identityMapping.clear();
		behaviourMapping.clear();
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
		// TODO: unregister components
		// TODO: notify the component that the engine is not working
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
	 */
	public void execute() {
		synchronized(this){
			if (isEngineExecuting) {
				logger.warn("Execute() called more than once");
			}	
			else {
				isEngineExecuting = true;
				
				notifyAll();
			}
		}
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
	 * Helper function that given the local identity of a BIPComponent Object returns the component's local identity.
	 */
	public Integer getBIPComponentIdentity(BIPComponent component) {
		return reversedIdentityMapping.get(component);
	}

	/**
	 * Helper function that given the local identity of a component returns the component as a BIPComponent Object.
	 */
	public BIPComponent getBIPComponent(int identity) {
		return identityMapping.get(identity);
	}

	/**
	 * Helper function that given the local identity of a component returns the behaviour as a Behaviour Object.
	 */
	public Behaviour getBIPComponentBehaviour(int identity) {
		return behaviourMapping.get(identity);
	}

	/**
	 * Helper function that returns the number of registered components in the system.
	 */
	public int getNoComponents() {
		return nbComponents;
	}

	/**
	 * Redirects System.err and sends all data to a file.
	 * 
	 */
	public void redirectSystemErr() {

		try {

			System.setErr(new PrintStream(new FileOutputStream("system_err.txt")));

			// String nullString = null;
			//
			// Forcing an exception to have the stacktrace printed on System.err
			// nullString = nullString.toUpperCase();

		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		}
	}
}
