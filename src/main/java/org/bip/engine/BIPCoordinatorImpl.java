package org.bip.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicInteger;


import org.bip.api.*;
import org.bip.behaviour.Behaviour;
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
 */
public class BIPCoordinatorImpl implements BIPCoordinator, Runnable {

	private Logger logger = LoggerFactory.getLogger(BIPCoordinatorImpl.class);
	private GlueEncoder glueenc = new GlueEncoderImpl();
	private BehaviourEncoder behenc = new BehaviourEncoderImpl();
	private CurrentStateEncoder currstenc = new CurrentStateEncoderImpl();
	private BDDBIPEngine engine = new BDDBIPEngineImpl();

	private Hashtable<BIPComponent, Integer> reversedIdentityMapping = new Hashtable<BIPComponent, Integer>();
	private Hashtable<Integer, BIPComponent> identityMapping = new Hashtable<Integer, BIPComponent>();
	private Hashtable<Integer, Behaviour> behaviourMapping = new Hashtable<Integer, Behaviour>();
	private HashSet <BIPComponent> componentsHaveInformed = new HashSet<BIPComponent> ();

	/** Identification number for local use */
	private AtomicInteger idGenerator = new AtomicInteger(0);
	
	/** Number of ports of components registered */
	private int noPorts;

	/** Number of states of components registered  */
	private int noStates;

	/** 
	 * Number of components registered
	 */
	public int noComponents;

	private Thread engineThread;

	private boolean isEngineExecuting;

	public BIPCoordinatorImpl() {
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
	
	/** We require to have components registered before glue is specified, so
	 * functions of BehEnc are executed properly.
	 */
	public synchronized void specifyGlue(BIPGlue glue) {
		glueenc.specifyGlue(glue);
		computeGlueAndInformEngine();
		computeTotalBehaviourAndInformEngine();
	}
	

	public synchronized void computeTotalBehaviourAndInformEngine(){
		engine.informTotalBehaviour(behenc.totalBehaviour());
	}
	
	public synchronized void computeGlueAndInformEngine(){
		engine.informGlue(glueenc.totalGlue());
	}

	public synchronized void register(BIPComponent component, Behaviour behaviour) {
		logger.info("********************************* Register *************************************");
		int registeredComponentID = idGenerator.getAndIncrement(); // atomically adds one //TODO: print an identifier through getName (?) instead of local identities
		reversedIdentityMapping.put(component, registeredComponentID);
		logger.info("Component: {} with identity {}",component.getName(), reversedIdentityMapping.get(component));
		identityMapping.put(registeredComponentID, component);
		behaviourMapping.put(registeredComponentID, behaviour);
		int componentPorts = behaviour.getEnforceablePorts().size();
		int componentStates = behaviour.getStates().size();

		behenc.createBDDNodes(registeredComponentID, componentPorts, componentStates);

		for (int i = 0; i < componentPorts; i++) {
			engine.getPositionsOfPorts().add(noPorts + noStates + componentStates + i);
		}
		noPorts = noPorts + componentPorts;
		noStates = noStates + componentStates;
		noComponents++;
		logger.info("******************************************************************************");
	}

	public synchronized void inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) {

		if(componentsHaveInformed.contains(component)){
			try {
				throw new BIPEngineException("Component has already informed the engine in this execution cycle.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				logger.error(e.getMessage());	
			} 
		}

		if(reversedIdentityMapping.containsKey(component)==true){
			componentsHaveInformed.add(component);
			logger.debug("Number of components that have informed {}", componentsHaveInformed.size());
		}
		else{
			try {
				throw new BIPEngineException("Component has not registered yet.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				logger.error(e.getMessage());	
			} 
		}
		
		engine.informCurrentState(component, currstenc.inform(component, currentState, disabledPorts));
		
		logger.info("********************************* Inform *************************************");
		logger.info("Component: {} with identity {}",component.getName(), reversedIdentityMapping.get(component));
		logger.info("informs that is at state: {}", currentState);
		logger.info("******************************************************************************");

		synchronized (this) {
			notifyAll();
		}
	}

	public synchronized void execute(ArrayList<BIPComponent> allComponents, Hashtable<BIPComponent, ArrayList<Port>> allPorts) {

		int size = allComponents.size();
		Port[] ports = new Port[size];
		for (int i = 0; i < size; i++) {
			BIPComponent comp = allComponents.get(i);
			if ((allPorts.get(comp) != null) && (!allPorts.get(comp).isEmpty())) {
				ports[i] = allPorts.get(comp).get(0);
			}
			if (ports[i] == null) {
				allComponents.get(i).execute(null); // TODO: instead of sending null is there a better solution?
			} else {
				allComponents.get(i).execute(ports[i].id); 
			}
		}
	}

	public void run() {
		
		if (glueenc.totalGlue()==null)
       {
              try {
                   throw new BIPEngineException("Glue BDD is null after execute");
              } catch (BIPEngineException e) {
                      e.printStackTrace();
                      logger.error("Total Glue BDD is null");
              }
   }
		logger.info("Engine thread is started.");
		while (true) {
			synchronized (this) {
				logger.debug("isEngineExecuting: {} ", isEngineExecuting);
				logger.debug("noComponents: {}, componentCounter: {}", noComponents, componentsHaveInformed.size());
				if (noComponents != 0 && componentsHaveInformed.size() == noComponents && isEngineExecuting) {
					componentsHaveInformed.clear();
					engine.runOneIteration();
				} else {

					try {
						wait();

					} catch (InterruptedException e) {
						logger.warn("Engine run is interrupted: {}", Thread.currentThread().getName());
						//TODO: notify the component that the engine is not working?
						return;
					}
				}
			}
		}
	}

	public void start() {
		engineThread = new Thread(this, "BIPEngine");
		engineThread.start();
	}

	public void stop() {
		engineThread.interrupt();
		isEngineExecuting = false;
		//TODO: unregister components
		//TODO: notify the component that the engine is not working
	}

	public void execute() {
		isEngineExecuting = true;
		synchronized (this) {
			notifyAll();
		}
	}

	public int getNoPorts() {
		return noPorts;
	}

	public int getNoStates() {
		return noStates;
	}
	
	public Integer getBIPComponentIdentity(BIPComponent component){
		return reversedIdentityMapping.get(component);
	}
	
	public BIPComponent getBIPComponent(int identity){
		return identityMapping.get(identity);
	}
	
	public Behaviour getBIPComponentBehaviour(int identity){
		return behaviourMapping.get(identity);
	}

	public int getNoComponents() {
		return noComponents;
	}
}
