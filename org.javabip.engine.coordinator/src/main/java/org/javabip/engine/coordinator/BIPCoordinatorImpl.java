/*
 * Copyright 2012-2016 École polytechnique fédérale de Lausanne (EPFL), Switzerland
 * Copyright 2012-2016 Crossing-Tech SA, Switzerland
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Simon Bliudze, Anastasia Mavridou, Radoslaw Szymanek and Alina Zolotukhina
 */
package org.javabip.engine.coordinator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.javabip.api.BIPActor;
import org.javabip.api.BIPComponent;
import org.javabip.api.BIPGlue;
import org.javabip.api.Behaviour;
import org.javabip.api.OrchestratedExecutor;
import org.javabip.api.Port;
import org.javabip.engine.api.BDDBIPEngine;
import org.javabip.engine.api.BIPCoordinator;
import org.javabip.engine.api.BehaviourEncoder;
import org.javabip.engine.api.CurrentStateEncoder;
import org.javabip.engine.api.GlueEncoder;
import org.javabip.engine.api.InteractionExecutor;
import org.javabip.exceptions.BIPEngineException;
import org.javabip.executor.ExecutorKernel;
import org.javabip.executor.TunellingExecutorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorContext;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.japi.Creator;

/**
 * Orchestrates the execution of the behaviour, glue and current state encoders. At the initialization phase, it
 * receives information about the behaviour of BIP components sends this to the behaviour encoder and orders it to
 * compute the total behaviour BDD. At the initialization phase, it also orders the glue encoder to compute the glue
 * BDD. During each execution cycle, it receives information about the current state of the BIP components and their
 * disabled ports, sends this to the current state encoder and orders it to compute the current state BDDs. When a new
 * interaction is chosen by the engine, it notifies all the BIP components.
 * 
 * @author Anastasia Mavridou
 */
public class BIPCoordinatorImpl implements BIPCoordinator, Runnable {

	private Logger logger = LoggerFactory.getLogger(BIPCoordinatorImpl.class);
	/**
	 * Create instances of all the the Glue Encoder, the Behaviour Encoder, the Current State Encoder, the Symbolic BIP
	 * Engine
	 */
	private GlueEncoder glueenc;
	private BehaviourEncoder behenc;
	private CurrentStateEncoder currstenc;
	private BDDBIPEngine engine;
	private InteractionExecutor interactionExecutor;
	private ActorSystem system;

	Thread currentThread = null;

	private ArrayList<BIPComponent> registeredComponents = new ArrayList<BIPComponent>();

	/**
	 * Helper hashtable with integers representing the local identities of registered components as the keys and the
	 * Behaviours of these components as the values.
	 */
	private Hashtable<BIPComponent, Behaviour> componentBehaviourMapping = new Hashtable<BIPComponent, Behaviour>();

	/**
	 * Helper hashtable with strings as keys representing the component type of the registered components and ArrayList
	 * of BIPComponent instances that correspond to the component type specified in the key.
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

	/**
	 * If a component does not have any enforceable transitions, it will not inform the engine. This integer should be
	 * used to set the haveAllComponentsInformed semaphore
	 */
	// public int nbComponentsWithEnforceableTransitions;

	/** Thread for the BIPCoordinator */
	private Thread engineThread;

	/**
	 * Boolean variable that shows whether the execute() was called.
	 */
	private boolean isEngineExecuting = false;

	/**
	 * Boolean field that shows whether the haveAllComponentsInformed semaphore is acquired for the number of components
	 * and therefore, the components can start informing the engine and releasing the semaphore.
	 */
	private boolean isEngineSemaphoreReady = false;

	/**
	 * Semaphore that controls when the runOneIteration() function of the BDDBIPEngine class can be called. It can be
	 * called after all registered components have inform the BIPCoordinator about their current state.
	 */
	private Semaphore haveAllComponentsInformed;
	private ActorContext typedActorContext;
	private Object typedActorSelf;

	public BIPCoordinatorImpl(ActorSystem system, GlueEncoder glueEncoder, BehaviourEncoder behenc,
			CurrentStateEncoder currentStateEncoder, BDDBIPEngine engine) {

		this.glueenc = glueEncoder;
		this.behenc = behenc;
		this.currstenc = currentStateEncoder;
		this.engine = engine;

		glueenc.setBehaviourEncoder(behenc);
		glueenc.setEngine(engine);
		glueenc.setBIPCoordinator(this);

		behenc.setEngine(engine);
		behenc.setBIPCoordinator(this);

		currstenc.setBehaviourEncoder(behenc);
		currstenc.setEngine(engine);
		currstenc.setBIPCoordinator(this);

		engine.setBIPCoordinator(this);
		this.system = system;

	}

	BIPGlue glueHolder;

	public synchronized void specifyGlue(BIPGlue glue) {
		glueHolder = glue;
	}

	public synchronized void delayedSpecifyGlue(BIPGlue glue) {
		try {
			glueenc.specifyGlue(glue);
		} catch (BIPEngineException e) {
		}
	}

	/**
	 * Order the Glue Encode to compute the total Glue and inform the core Engine.
	 * 
	 * @throws BIPEngineException
	 */
	private synchronized void computeTotalGlueAndInformEngine() throws BIPEngineException {
		engine.informGlue(glueenc.totalGlue());

	}

	/**
	 * When the registration of components has finished, order engine to compute the total Behaviour BDD.
	 * 
	 * Currently, this method is called once in the run() but later, on the deletion of components on the fly the engine
	 * should be re-ordered to compute the total Behaviour BDD. Not that, in case of insertion of components this
	 * function need not be called, since we can just take the conjunction of the previous total Behaviour BDD and the
	 * Behaviour BDD representing the new component to compute the new total Behaviour BDD.
	 * 
	 * @throws BIPEngineException
	 */
	private synchronized void computeTotalBehaviour() throws BIPEngineException {
		engine.totalBehaviourBDD();
	}

	public BIPComponent getComponentFromObject(Object component) {
		return objectToComponent.get(component);
	}

	private HashMap<Object, BIPComponent> objectToComponent = new HashMap<Object, BIPComponent>();
	private final boolean hasBothProxies = true;

	/**
	 * The BIP Engine creates an Actor for every BIP component and registers the component.
	 */
	public synchronized BIPActor register(Object component, String id, boolean useSpec) {
		if (registeredComponents.contains(component)) {
			logger.error("Component " + objectToComponent.get(component).getId() + " has already registered before.");
			throw new BIPEngineException("Component " + objectToComponent.get(component).getId()
					+ " has already registered before.");
		} else {

			final ExecutorKernel executor = new ExecutorKernel(component, id, useSpec);
			OrchestratedExecutor executorActor;

			if (hasBothProxies) {

				try {
					final Object proxyingBoth = TunellingExecutorHandler.newProxyInstance(
							BIPCoordinatorImpl.class.getClassLoader(), executor, component);

					executorActor = (OrchestratedExecutor) TypedActor.get(typedActorContext).typedActorOf(
							new TypedProps<Object>((Class<? super Object>) proxyingBoth.getClass(),
									new Creator<Object>() {
										public Object create() {
											return proxyingBoth;
										}
									}), executor.getId());
				} catch (Exception exception) {
					executorActor = TypedActor.get(typedActorContext).typedActorOf(
							new TypedProps<OrchestratedExecutor>(OrchestratedExecutor.class,
									new Creator<OrchestratedExecutor>() {
										public ExecutorKernel create() {
											return executor;
										}
									}), executor.getId());
				}

			} else {

				executorActor = TypedActor.get(typedActorContext).typedActorOf(
						new TypedProps<OrchestratedExecutor>(OrchestratedExecutor.class,
								new Creator<OrchestratedExecutor>() {
									public ExecutorKernel create() {
										return executor;
									}
								}), executor.getId());
			}

			executor.setProxy(executorActor);

			objectToComponent.put(component, executorActor);

			Behaviour behaviour = executor.getBehavior();
			/*
			 * The condition below checks whether the component has already been registered.
			 */

			logger.info("********************************* Register *************************************");

			/*
			 * Map all component instances of the same type in the typeInstancesMapping Hashtable
			 */
			ArrayList<BIPComponent> componentInstances = new ArrayList<BIPComponent>();

			/*
			 * If this component type already exists in the hashtable, update the ArrayList of BIPComponents that
			 * corresponds to this component type.
			 */
			if (typeInstancesMapping.containsKey(executorActor.getType())) {
				componentInstances.addAll(typeInstancesMapping.get(executorActor.getType()));
			}

			componentInstances.add(executorActor);
			typeInstancesMapping.put(executorActor.getType(), componentInstances);
			registeredComponents.add(executorActor);

			/*
			 * Keep the local ID for now, but use OSGI IDs later
			 */
			logger.info("Component : {}", component);

			componentBehaviourMapping.put(executorActor, behaviour);
			int nbComponentPorts = (behaviour.getEnforceablePorts()).size();
			int nbComponentStates = (behaviour.getStates()).size();

			try {
				behenc.createBDDNodes(executorActor, (behaviour.getEnforceablePorts()), ((new ArrayList<String>(
						behaviour.getStates()))));
			} catch (BIPEngineException e) {
				// e.printStackTrace();
			}
			try {
				engine.informBehaviour(executorActor, behenc.behaviourBDD(executorActor));
			} catch (BIPEngineException e) {
				// e.printStackTrace();
			}

			for (int i = 0; i < nbComponentPorts; i++) {
				behenc.getPositionsOfPorts().add(nbPorts + nbStates + nbComponentStates + i);
				behenc.getPortToPosition().put((behaviour.getEnforceablePorts()).get(i),
						nbPorts + nbStates + nbComponentStates + i);
			}
			nbPorts += nbComponentPorts;
			nbStates += nbComponentStates;
			nbComponents++;
			// if (!behaviour.getEnforceablePorts().isEmpty()) {
			// nbComponentsWithEnforceableTransitions++;
			// }
			logger.info("******************************************************************************");
			org.javabip.api.BIPEngine typedActorEngine = (org.javabip.api.BIPEngine) typedActorSelf;
			executorActor.register(typedActorEngine); // BIG TODO: Try synchronous call

			// return actorWithLifeCycle;
			return executorActor;
		}

	}

	/**
	 * Components call the inform function to give information about their current state and their number of disabled
	 * ports by guards that do not have to do with data transfer.
	 * 
	 * If the guards of a transition do not have information valuable for data transfer then only this inform is called
	 * for a particular component. Otherwise, also the other inform function is called.
	 */
	public synchronized void inform(BIPComponent component, String currentState, Set<Port> disabledPorts) {
		// long time1 = System.currentTimeMillis();
		if (componentsHaveInformed.contains(component)) {
			try {
				logger.debug("************************ Already Have Informed *******************************");
				logger.debug("Component: " + component + "informs that is at state: " + currentState);
				// for (Port disabledPort : disabledPorts) {
				// logger.debug("with disabled port: " + disabledPort.getId());
				// }
				logger.debug("******************************************************************************");
				logger.error("Component " + component.getId()
						+ " has already informed the engine in this execution cycle.");
				throw new BIPEngineException("Component " + component.getId()
						+ " has already informed the engine in this execution cycle.");
			} catch (BIPEngineException e) {
				// e.printStackTrace();
			}
			return;
		}

		/*
		 * If a component informs more than once in the same execution cycle we add the else below to prevent the
		 * re-computation of the current state BDD for the specific component. The deletion of the else will not result
		 * in any data corruption but overhead will be added.
		 */
		/**
		 * This condition checks whether the component has already registered.
		 */
		if (registeredComponents.contains(component)) {
			synchronized (componentsHaveInformed) {
				componentsHaveInformed.add(component);
				try {
					engine.informCurrentState(component, currstenc.inform(component, currentState, disabledPorts));
				} catch (BIPEngineException e) {
					e.printStackTrace();
				}

				// logger.trace("Number of components that have informed {}",
				// componentsHaveInformed.size());
				logger.debug("********************************* Inform *************************************");
				logger.debug("Component: " + component + "informs that is at state: " + currentState);
				// for (Port disabledPort : disabledPorts) {
				// logger.debug("with disabled port: " + disabledPort.getId());
				// }
				logger.debug("******************************************************************************");

				/*
				 * The haveAllComponentsInformed semaphore is used to indicate whether all registered components have
				 * informed and to order one execution cycle of the engine. The semaphore is acquired in run().
				 * 
				 * When a component informs, we first check if the haveAllComponentsInformed semaphore has been acquired
				 * before and then we release.
				 * 
				 * This block is synchronized with the number of components that have informed. Therefore, the
				 * haveAllComponentsInformed semaphore cannot be released by any other component at the same time.
				 */
				if (isEngineSemaphoreReady) {
					haveAllComponentsInformed.release();
					logger.trace("Number of available permits in the semaphore: {}",
							haveAllComponentsInformed.availablePermits());
				}
			}
			/**
			 * An exception is thrown when a component informs the Coordinator without being registered first.
			 */
		} else {
			try {
				logger.error("No component with name" + component.getId() + " specified in the inform 	was registered."
						+ "\tPossible reason: "
						+ "Name attribute in ComponentType annotation does not match the name of the Class.");
				throw new BIPEngineException("Component " + component.getId()
						+ " specified in the inform was registered." + "\tPossible reason: "
						+ "Name attribute in ComponentType annotation does not match the name of the Class.");
			} catch (BIPEngineException e) {
				// e.printStackTrace();
			}
		}
		// System.out.println("BC:" + (System.currentTimeMillis() - time1));

	}

	/**
	 * The BDDBIPEngine is not aware whether it should send the ports to be executed to the DataCoordinator or to the
	 * BIPCoordinator. This is decided what the interactionExecutor is set to at the tests.
	 * 
	 * @throws BIPEngineException
	 */
	public void execute(byte[] valuation) throws BIPEngineException {
		if (interactionExecutor != this && isEngineExecuting) {
			interactionExecutor.execute(valuation);
		} else if (isEngineExecuting) {
			executeInteractions(preparePorts(valuation));
		}
	}

	private List<List<Port>> preparePorts(byte[] valuation) {
		/*
		 * Prepare the list of ports to be executed.
		 */
		List<List<Port>> bigInteraction = new ArrayList<List<Port>>();
		ArrayList<Port> portsExecuted = new ArrayList<Port>();

		Map<Port, Integer> portToPosition = getBehaviourEncoderInstance().getPortToPosition();
		for (Port port : portToPosition.keySet()) {
			if (valuation[portToPosition.get(port)] == 1 || valuation[portToPosition.get(port)] == -1) {
				portsExecuted.add(port);
			}
		}
		logger.trace("chosenPorts size: " + portsExecuted.size());
		if (portsExecuted.size() != 0) {
			bigInteraction.add(portsExecuted);
		}

		// for (Port port : portsExecuted) {
		// logger.debug("ENGINE ENTRY: " + port.component() + " - " + port);
		// System.out.println("ENGINE ENTRY: " + port.component() + " - " + port);
		// }
		logger.debug("*************************************************************************");

		return bigInteraction;
	}

	/**
	 * BDDBIPEngine informs the BIPCoordinator for the components (and their associated ports) that are part of the same
	 * chosen interaction.
	 * 
	 * Through this function all the components need to be notified. If they are participating in an interaction then
	 * their port to be fired is sent to them through the execute function of the BIPExecutor. If they are not
	 * participating in an interaction then null is sent to them.
	 * 
	 * @throws BIPEngineException
	 */
	public void executeInteractions(List<List<Port>> portsToFire) throws BIPEngineException {

		if (portsToFire == null) {
			logger.warn("BIP Coordinator: Empty interaction requested for execution -- nothing to do.");

			/*
			 * Send null to the components that are not part of the overall interaction.
			 */
			for (BIPComponent component : registeredComponents) {
				component.execute(null);

			}

			return;
		}
		assert (portsToFire != null);

		/*
		 * This is a list of components participating in the chosen-by-the-engine interactions. This keeps track of the
		 * chosen components in order to differentiate them from the non chosen ones. Through this function all the
		 * components need to be notified. Either by sending null to them or the port to be fired.
		 */
		ArrayList<BIPComponent> waitingComponents = (ArrayList<BIPComponent>) registeredComponents.clone();
		for (Iterable<Port> portGroup : portsToFire) {
			Iterator<Port> ports = portGroup.iterator();
			while (ports.hasNext() && isEngineExecuting) {
				Port port = ports.next();
				/*
				 * Throw an exception if the port is empty. This should not happen.
				 */
				if (port.getId().isEmpty() && isEngineExecuting) {
					try {
						logger.error("Exception in thread: " + Thread.currentThread().getName()
								+ "In the interaction chosen by the engine the port, associated to component "
								+ port.component().getId() + ", is empty.");
						throw new BIPEngineException("Exception in thread: " + Thread.currentThread().getName()
								+ "In the interaction chosen by the engine the port, associated to component "
								+ port.component().getId() + ", is empty.");
					} catch (NullPointerException e) {
						if (port.component() != null) {
							throw e;
						} else {
							logger.error("Exception in thread: "
									+ Thread.currentThread().getName()
									+ "In the interaction chosen by the engine a port is empty and does not have an associated component.");
							throw new BIPEngineException(
									"Exception in thread: "
											+ Thread.currentThread().getName()
											+ "In the interaction chosen by the engine a port is empty and does not have an associated component.");
						}
					}
				}

				/*
				 * Throw an exception if the port does not have a component. This should not happen either.
				 */
				if (port.component() == null) {
					logger.error("Exception in thread: " + Thread.currentThread().getName()
							+ "In the interaction chosen by the engine the port with id = " + port.getId()
							+ " does not have an associated component.");
					throw new BIPEngineException("Exception in thread: " + Thread.currentThread().getName()
							+ "In the interaction chosen by the engine the port with id = " + port.getId()
							+ " does not have an associated component.");
				}

				/* Execute the port */

				logger.debug("Chosen port: " + port.getId() + " of component: " + port.component().getId());
				if (isEngineExecuting)
					port.component().execute(port.getId());

				/*
				 * Remove the corresponding component from the list of those that do not move
				 */
				waitingComponents.remove(port.component());
			}
		}

		/*
		 * Send null to the components that are not part of the overall interaction.
		 */
		for (BIPComponent component : waitingComponents) {
			component.execute(null);
		}

	}

	/**
	 * Initialization phase. Orders the Behaviour and Current State Encoders to compute their total BDDs and send these
	 * to the BDDBIPEngine.
	 * 
	 * @throws BIPEngineException
	 * @throws InterruptedException
	 */
	private void coordinatorCycleInitialization() throws BIPEngineException {

		/*
		 * Wait until the execute() has been called signaling that all the components have registered
		 */
		synchronized (this) {
			while (!isEngineExecuting) {
				try {
					logger.trace("Waiting for the engine execute to be called...");
					wait();
					logger.trace("Waiting for the engine execute done.");
				} catch (InterruptedException e) {
					logger.warn("Engine run is interrupted: {}", Thread.currentThread().getName());
				}
			}
		}

		/*
		 * For the moment, all components must be registered before execute() is called. Therefore the engine might as
		 * well quit if the following test fails. However, in the future we want components to be able to register and
		 * unregister on the fly. In this case running the engine with no registered components becomes legitimate.
		 */
		if (nbComponents == 0) {
			logger.error("Thread started but no components have been registered yet.");
		}

		/*
		 * To order the engine to begin its execution cycle we need to know first whether all components have informed
		 * the BIP Coordinator about their current state. For this reason, the semaphore haveAllComponentsInformed is
		 * used that it is initialized here with the number of registered components in the system. Note that, if
		 * components can be registered and unregistered on the fly the semaphore has to be updated with the new number
		 * of components in the system.
		 * 
		 * Acquire permits for the number of registered components, which have not informed about their current state
		 * yet. NB: Components may have inform the BIPCoordinator before the execute() is called
		 */
		synchronized (componentsHaveInformed) {
			haveAllComponentsInformed = new Semaphore(nbComponents);
			try {
				logger.trace("Waiting for the engine semaphore to be initialized to 0...");
				haveAllComponentsInformed.acquire(nbComponents);
				isEngineSemaphoreReady = true;
				logger.trace("Engine semaphore initialised");
			} catch (InterruptedException e1) {
				logger.error("Semaphore's haveAllComponentsInformed acquire method for the number of registered components in the system was interrupted.");
				// e1.printStackTrace();
			}
		}

		try {
			logger.trace("Waiting for the cycle initialisation acquire...");
			haveAllComponentsInformed.acquire(nbComponents - componentsHaveInformed.size());
			logger.trace("The cycle initialisation acquire successful");
		} catch (InterruptedException e1) {
			logger.error("Semaphore's haveAllComponentsInformed acquire method for the number of components that still have to inform was interrupted.");
			// e1.printStackTrace();
		}

		/*
		 * Compute behaviour and glue BDDs with the components that have registered before the call to execute(). If
		 * components were to register after the call to execute() these BDDs must be recomputed accordingly.
		 */
		// For performance info
		// long startTime = System.currentTimeMillis();
		computeTotalBehaviour();
		computeTotalGlueAndInformEngine();
		// For performance info
		// long estimatedTime = System.currentTimeMillis() - startTime;
		// System.out.println("Init time : " + estimatedTime);

	}

	public void run() {

		logger.info("Engine thread is started.");

		try {
			coordinatorCycleInitialization();
		} catch (BIPEngineException e1) {
			// e1.printStackTrace();
			isEngineExecuting = false;
			engineThread.interrupt();
		}

		/**
		 * Start the Engine cycle
		 */

		while (isEngineExecuting) {

			logger.trace("isEngineExecuting: {} ", isEngineExecuting);
			logger.trace("noComponents: {}, componentCounter: {}", nbComponents, componentsHaveInformed.size());
			logger.trace("Number of available permits in the semaphore: {}",
					haveAllComponentsInformed.availablePermits());

			componentsHaveInformed.clear();

			try {

				// long time1 = System.currentTimeMillis();
				engine.runOneIteration();
				// System.out.printf("E: %s ", (System.currentTimeMillis() - time1));
			} catch (BIPEngineException e1) {

				isEngineExecuting = false;
				// e1.printStackTrace();
			}

			try {
				logger.trace("Waiting for the acquire in run()...");
				haveAllComponentsInformed.acquire(nbComponents);

				logger.trace("run() acquire successful.");
			} catch (InterruptedException e) {
				isEngineExecuting = false;
				// e.printStackTrace();
				// logger.error("Semaphore's haveAllComponentsInformed acquire method for the number of registered components in the system was interrupted.");
			}
		}

		// TODO: unregister components and notify the component that the engine is not working
		// for (BIPComponent component : identityMapping.values()) {
		// component.deregister();
		// }

		return;
	}

	/**
	 * Create a thread for the Engine and start it.
	 */
	public void start() {
		delayedSpecifyGlue(glueHolder);
		engineThread = new Thread(this, "BIPEngine");
		engineThread.start();
	}

	/**
	 * Interrupt the Engine thread.
	 */
	public void stop() {
		if (engineThread == null) {
			logger.error("Stoping the engine before starting it.");
			throw new BIPEngineException("Stoping the engine before starting it.");
		}
		isEngineExecuting = false;
		engineThread.stop();
		// engineThread.interrupt();

	}

	/**
	 * If the execute function is called then set to true the boolean isEngineExecuting and notify the waiting threads.
	 * If the execute function is called more than once then it complains.
	 * 
	 * We do not allow the case that execute is called twice simultaneously by surrounding its code by
	 * synchronized(this).
	 * 
	 * At this implementation, we assume that components have been registered before the execute() is called and
	 * therefore we are aware of the number of components in the system and can initialize the semaphore that shows
	 * whether all registered components have informed. In future, that components may be able to register on the fly
	 * the semaphore needs be re-initialized with the new number of components. If a component unregisters from the
	 * system then we can use the reducePermits(int reduction) on the semaphore to shrink the number of available
	 * permits by the indicated reduction.
	 * 
	 * We also check here if the interactionExecutor has been set to DataCoordinator. Otherwise set it to
	 * BIPCoordinator.
	 */
	public void execute() {
		if (isEngineExecuting) {
			logger.warn("Execute() called more than once");
		} else {
			synchronized (this) {
				isEngineExecuting = true;
				notifyAll();
				if (this.interactionExecutor == null) {
					setInteractionExecutor(this);
				}
			}
		}
	}

	/**
	 * This function should not do anything but give a warning.
	 * 
	 * BIPCoordinator and DataCoordinator both implement the BIPEngine interface, where the informSpecific function is.
	 * DataCoordinator is responsible for sending the disabledCombinations of the informSpecific directly to the
	 * DataEncoder. The BIPCoordinator should not participate in this.
	 */

	public void informSpecific(BIPComponent decidingComponent, Port decidingPort,
			Map<BIPComponent, Set<Port>> disabledCombinations) throws BIPEngineException {
		logger.warn("InformSpecific of BIPCoordinator is called. That should never happen. All the information should be passed directly from the DataCoordinator to the DataEncoder.");
	}

	/**
	 * Give the engine a BDD for a disabledCombination. Data Encoder calls this function.
	 * 
	 * NB: DataCoordinator does not have any connection to the BDDBIPEngine.
	 */
	public void specifyTemporaryConstraints(BDD disabledCombination) {
		engine.specifyTemporaryExtraConstraints(disabledCombination);
	}

	/**
	 * Give to the engine the BDD corresponding to the Data variables and their implications BDD.\ Data Encoder calls
	 * this function.
	 * 
	 * NB: DataCoordinator does not have any connection to the BDDBIPEngine.
	 */
	public void specifyPermanentConstraints(Set<BDD> constraints) {
		engine.specifyPermanentExtraConstraints(constraints);
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

	public BehaviourEncoder getBehaviourEncoderInstance() {
		return behenc;
	}

	public BDDFactory getBDDManager() {
		return engine.getBDDManager();
	}

	/**
	 * Set Interaction Executor to BIPCoordinator in the case there are no data transfer
	 */
	public void setInteractionExecutor(InteractionExecutor interactionExecutor) {
		this.interactionExecutor = interactionExecutor;
	}

	/**
	 * Helper function that returns the registered component instances that correspond to a component type.
	 * 
	 * @throws BIPEngineException
	 */
	public List<BIPComponent> getBIPComponentInstances(String type) throws BIPEngineException {
		List<BIPComponent> instances = typeInstancesMapping.get(type);
		if (instances == null) {
			try {
				logger.error("No registered component instances for the: "
						+ type
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

	public ActorSystem getSystem() {
		return system;
	}

	@Override
	public void initialize() {
		// The following two function calls are causing problems in OSGi context.
		// They need to be executed in special manner (only within the function of TypedActor itself).
		this.typedActorContext = TypedActor.context();
		this.typedActorSelf = TypedActor.self();
	}

}
