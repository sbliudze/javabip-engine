package org.bip.engine;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicInteger;

import net.sf.javabdd.BDDFactory;

import org.bip.api.*;
import org.bip.behaviour.Behaviour;
import org.bip.behaviour.Port;
import org.bip.glue.BIPGlue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO: add comments
public class OSGiBIPEngine implements BIPEngine, Runnable {

	private GlueEncoderImpl glueenc = new GlueEncoderImpl(); //TODO: change to IFs
	private BehaviourEncoderImpl behenc = new BehaviourEncoderImpl();
	private CurrentStateEncoderImpl currstenc = new CurrentStateEncoderImpl();
	private BDDBIPEngineImpl engine = new BDDBIPEngineImpl();

	private Logger logger = LoggerFactory.getLogger(OSGiBIPEngine.class);

	//TODO: start IDs from 0
	public Hashtable<BIPComponent, Integer> reversedIdentityMapping = new Hashtable<BIPComponent, Integer>();  //TODO: give me ID getter?
	public Hashtable<Integer, BIPComponent> identityMapping = new Hashtable<Integer, BIPComponent>(); //TODO: give me BIPComponent getter?
	public Hashtable<Integer, Behaviour> behaviourMapping = new Hashtable<Integer, Behaviour>();
	public ArrayList<Integer> positionsOfPorts = new ArrayList<Integer>(); //move to the BDDEngine
	private volatile int componentCounter = 0; // TODO: hashset of the components registered, if a component informs then check if it registered (exists in the hashset) and add it to the hashset of components informed

	/** Identification number for local use */
	private AtomicInteger idGenerator = new AtomicInteger(1);
	/** Total number of ports */
	private int noPorts;
	
	/** Total number of states */
	private int noStates;
	
	/** Total number of components registered */
	public int noComponents;

	private Thread engineThread;

	private boolean isEngineExecuting;

	
	public OSGiBIPEngine() {
		glueenc.setBehaviourEncoder(behenc);
		glueenc.setEngine(engine);
		glueenc.setOSGiBIPEngine(this);

		behenc.setEngine(engine);
		behenc.setOSGiBIPEngine(this);

		currstenc.setBehaviourEncoder(behenc);
		currstenc.setEngine(engine);
		currstenc.setOSGiBIPEngine(this);

		engine.setOSGiBIPEngine(this);

		engine.bdd_mgr = BDDFactory.init("java", 100, 100); //TODO: parameters as constants

	}


	public synchronized void specifyGlue(BIPGlue glue) {
		// We require to have components registered before glue is specified, so
		// functions of BehEnc are executed properly.
		// TODO, can we move those functions to other place so it is better
		// visible.
		glueenc.specifyGlue(glue);
		engine.informTotalBehaviour(behenc.totalBehaviour());
		engine.informGlue(glueenc.totalGlue());

	}

	public synchronized void register(BIPComponent component, Behaviour behaviour) {
		// TODO, investigate the +1
		logger.error("component {} register started", behaviour.getComponentType());
		int registeredComponentID = idGenerator.getAndIncrement(); // atomically adds one
		reversedIdentityMapping.put(component, registeredComponentID);
		identityMapping.put(registeredComponentID, component);
		behaviourMapping.put(registeredComponentID, behaviour);
		int componentPorts = behaviour.getEnforceablePorts().size();
		int componentStates = behaviour.getStates().size();

		behenc.createBDDNodes(registeredComponentID, componentPorts, componentStates);

		for (int i = 0; i < componentPorts; i++) {
			positionsOfPorts.add(noPorts + noStates + componentStates + i);
		}
		noPorts = noPorts + componentPorts;
		noStates = noStates + componentStates;
		noComponents++;
		logger.error("component {} register finished", behaviour.getComponentType());
	}

	public synchronized void inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) {

		// logger.error("Wrapper inform has been called for {}.", component);
		engine.informCurrentState(component, currstenc.inform(component, currentState, disabledPorts));
		componentCounter++;
		System.out.println("********************************* Inform *************************************"); //TODO, logging with different levels e.g. Info, Trace, Debug, Error
		System.out.println("Component: " + component.getName() + reversedIdentityMapping.get(component) + " is at state " + currentState);
		System.out.println("******************************************************************************");
		// logger.error("Component Counter in Inform: {}", componentCounter);

		synchronized (this) {
			notifyAll();
		}

		// logger.error("Wrapper inform finished.");
	}

	public synchronized void execute(ArrayList<BIPComponent> chosenComponent,
			// TODO: refactor to allComponents
			Hashtable<BIPComponent, ArrayList<Port>> chosenPorts) {

		int size = chosenComponent.size();
		Port[] ports = new Port[size];
		for (int i = 0; i < size; i++) {
			BIPComponent comp = chosenComponent.get(i);
			if ((chosenPorts.get(comp) != null) && (!chosenPorts.get(comp).isEmpty())) {
				ports[i] = chosenPorts.get(comp).get(0);
			}
			if (ports[i] == null) {
				chosenComponent.get(i).execute(null); // Always one Port for now
				// TODO: instead of sending null is there a better solution?
			} else {
				chosenComponent.get(i).execute(ports[i].id); // Always one Port
				// for now
			}
		}
	}


	public void run() { 
		logger.error("Engine thread is started.");
		while (true) {
			
			synchronized (this) {
				logger.error("isEngineExecuting: {} ", isEngineExecuting);
				logger.error("noComponents: {}, componentCounter: {}", noComponents, componentCounter);
				if ( noComponents != 0 && componentCounter == noComponents && isEngineExecuting) {
					componentCounter = 0;
					engine.runOneIteration();
				} else {

					try {
						wait();

					} catch (InterruptedException e) {
						logger.error("Engine run is interrupted: {}", Thread.currentThread().getName());
						return;
					}
				}
			}
		}
	}

	@Override
	public void start() {
		engineThread = new Thread(this, "BIPEngine");
		engineThread.start();
	}

	public void stop() {
		engineThread.interrupt();
		isEngineExecuting = false;
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
}
