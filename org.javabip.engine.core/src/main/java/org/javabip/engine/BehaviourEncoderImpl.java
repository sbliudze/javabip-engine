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
 */

package org.javabip.engine;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;

import org.javabip.api.BIPComponent;
import org.javabip.api.Behaviour;
import org.javabip.api.Port;
import org.javabip.engine.api.BDDBIPEngine;
import org.javabip.engine.api.BIPCoordinator;
import org.javabip.engine.api.BehaviourEncoder;
import org.javabip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Auto-generated Javadoc
/**
 * Receives information about the behaviour of each registered component and computes the total behaviour BDD.
 * 
 * @author mavridou
 */

public class BehaviourEncoderImpl implements BehaviourEncoder {

	/** The logger. */
	private Logger logger = LoggerFactory.getLogger(BehaviourEncoderImpl.class);

	/** The state BDDs. */
	private volatile Hashtable<BIPComponent, BDD[]> stateBDDs = new Hashtable<BIPComponent, BDD[]>();

	/** The port BDDs. */
	private volatile Hashtable<BIPComponent, BDD[]> portBDDs = new Hashtable<BIPComponent, BDD[]>();

	/** The component to port to bdd. */
	private volatile Hashtable<BIPComponent, Hashtable<String, BDD>> componentToPortToBDD = new Hashtable<BIPComponent, Hashtable<String, BDD>>();

	/** The component to state to bdd. */
	private volatile Hashtable<BIPComponent, Hashtable<String, BDD>> componentToStateToBDD = new Hashtable<BIPComponent, Hashtable<String, BDD>>();

	/** The aux sum. */
	private int auxSum;

	/** The engine. */
	private BDDBIPEngine engine;

	/** The wrapper. */
	private BIPCoordinator wrapper;

	/** The positions of ports. */
	private ArrayList<Integer> positionsOfPorts = new ArrayList<Integer>();

	/** The port to position. */
	Map<Port, Integer> portToPosition = new Hashtable<Port, Integer>();

	/**
	 * Creates one-node BDDs that correspond to the ports and the states of all the registered components.
	 *
	 * @param component
	 *            the component being registered.
	 * @param componentPorts
	 *            the list of component ports.
	 * @param componentStates
	 *            the list of component states.
	 * @throws BIPEngineException
	 *             when a state or a port BDD has failed to be created.
	 */
	public synchronized void createBDDNodes(BIPComponent component, List<Port> componentPorts,
			List<String> componentStates) throws BIPEngineException {

		int nbComponentPorts = componentPorts.size();
		int nbComponentStates = componentStates.size();
		int initialNoNodes = nbComponentPorts + nbComponentStates + auxSum;

		if (engine.getBDDManager().varNum() < initialNoNodes) {
			engine.getBDDManager().setVarNum(initialNoNodes);
		}

		BDD[] singleNodeBDDsForStates = new BDD[nbComponentStates];
		Hashtable<String, BDD> stateToBDD = new Hashtable<String, BDD>();
		for (int i = 0; i < nbComponentStates; i++) {
			/* Create new variable in the BDD manager for the state of each component instance. */
			singleNodeBDDsForStates[i] = engine.getBDDManager().ithVar(i + auxSum);
			if (singleNodeBDDsForStates[i] == null) {
				try {
					logger.error("Single node BDD for state {} is equal to null", componentStates.get(i));
					throw new BIPEngineException("Single node BDD for state " + componentStates.get(i)
							+ " is equal to null");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;
				}
			}
			stateToBDD.put(componentStates.get(i), singleNodeBDDsForStates[i]);
			if (stateToBDD.get(componentStates.get(i)) == null) {
				try {
					logger.error("BDD node that corresponds to the state {} of component {} is not created.",
							componentStates.get(i), component.getId());
					throw new BIPEngineException("BDD node that corresponds to the state " + componentStates.get(i)
							+ " of component " + component.getId() + " is not created.");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;
				}
			}
		}
		componentToStateToBDD.put(component, stateToBDD);
		stateBDDs.put(component, singleNodeBDDsForStates);

		BDD[] singleNodeBDDsForPorts = new BDD[nbComponentPorts];
		Hashtable<String, BDD> portToBDD = new Hashtable<String, BDD>();
		logger.trace("Behaviour Encoder: Number of component ports: " + nbComponentPorts + " for component: "
				+ component);
		logger.trace("Behaviour Encoder: Number of component states: " + nbComponentStates + " for component: "
				+ component);

		for (int i = 0; i < nbComponentPorts; i++) {
			/* Create new variable in the BDD manager for the port of each component instance. */
			singleNodeBDDsForPorts[i] = engine.getBDDManager().ithVar(i + nbComponentStates + auxSum);
			if (singleNodeBDDsForPorts[i] == null) {
				try {
					logger.error("Single node BDD for port {} is equal to null", componentPorts.get(i));
					throw new BIPEngineException("Single node BDD for port " + componentPorts.get(i)
							+ " is equal to null.");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;
				}
			}
			portToBDD.put(componentPorts.get(i).getId(), singleNodeBDDsForPorts[i]);
			if (portToBDD.get(componentPorts.get(i).getId()) == null) {
				try {
					logger.error("BDD node that corresponds to the port {} of component {} is not created.",
							componentPorts.get(i).getId(), component.getId());
					throw new BIPEngineException("BDD node that corresponds to the port "
							+ componentPorts.get(i).getId() + " of component " + component.getId() + " is not created.");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;
				}
			}
		}
		componentToPortToBDD.put(component, portToBDD);
		portBDDs.put(component, singleNodeBDDsForPorts);
		auxSum = auxSum + nbComponentPorts + nbComponentStates;
	}

	/**
	 * Computes and returns the BDD corresponding to the behaviour of a particular component.
	 *
	 * @param component
	 *            the component for which the BDD is computed.
	 * @return BDD that corresponds to the behaviour of the component.
	 * @throws BIPEngineException
	 *             when the component behaviour is null
	 */
	public synchronized BDD behaviourBDD(BIPComponent component) throws BIPEngineException {

		BDD componentBehaviourBDD = engine.getBDDManager().zero();
		Behaviour behaviour = wrapper.getBehaviourByComponent(component);
		if (behaviour == null) {
			try {
				logger.error("Behaviour of component {} is null", component.getId());
				throw new BIPEngineException("Behaviour of component " + component.getId() + " is null.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		List<Port> componentPorts = behaviour.getEnforceablePorts();
		List<String> componentStates = new ArrayList<String>(behaviour.getStates());
		Hashtable<String, BDD> portToBDD = componentToPortToBDD.get(component);
		Hashtable<String, BDD> stateToBDD = componentToStateToBDD.get(component);

		BDD tmp;
		for (String componentState : componentStates) {
			logger.trace("BE: Component State: " + componentState);

			BDD onlyState = engine.getBDDManager().one().and(stateToBDD.get(componentState));

			for (String otherState : componentStates) {
				if (!componentState.equals(otherState)) {
					logger.trace("BE: Negated State: " + otherState);
					tmp = onlyState.and(stateToBDD.get(otherState).not());
					onlyState.free();
					onlyState = tmp;
				}
			}
			Set<Port> statePorts = behaviour.getStateToPorts().get(componentState);
			if (!statePorts.isEmpty()) {
				for (Port port : statePorts) {
					logger.trace("BE: Component state port: " + port);
					BDD ports = engine.getBDDManager().one().and(onlyState);
					tmp = ports.and(portToBDD.get(port.getId()));
					ports.free();
					ports = tmp;
					for (Port otherPort : componentPorts) {
						if (!port.getId().equals(otherPort.getId())) {
							logger.trace("BE: Negated ports: " + otherPort);
							ports.andWith(portToBDD.get(otherPort.getId()).not());
						}
					}
					componentBehaviourBDD.orWith(ports);
				}
			} else {
				for (Port otherPort : componentPorts) {
					logger.trace("BE: All negated ports: " + otherPort);
					onlyState.andWith(portToBDD.get(otherPort.getId()).not());
				}
				componentBehaviourBDD.orWith(onlyState);
			}
		}

		BDD allNegatedPortsBDD = engine.getBDDManager().one();
		for (Port port : componentPorts) {
			allNegatedPortsBDD.andWith(portToBDD.get(port.getId()).not());
		}
		return componentBehaviourBDD.orWith(allNegatedPortsBDD);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.BehaviourEncoder#setEngine(org.bip.engine.api.BDDBIPEngine)
	 */
	public void setEngine(BDDBIPEngine engine) {
		this.engine = engine;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.BehaviourEncoder#setBIPCoordinator(org.bip.engine.api.BIPCoordinator)
	 */
	public void setBIPCoordinator(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.BehaviourEncoder#getStateBDDs()
	 */
	public synchronized Hashtable<BIPComponent, BDD[]> getStateBDDs() {
		return stateBDDs;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.BehaviourEncoder#getPortBDDs()
	 */
	public synchronized Hashtable<BIPComponent, BDD[]> getPortBDDs() {
		return portBDDs;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.BehaviourEncoder#getBDDOfAPort(org.bip.api.BIPComponent, java.lang.String)
	 */
	public synchronized BDD getBDDOfAPort(BIPComponent component, String portName) throws BIPEngineException {
		Hashtable<String, BDD> aux = componentToPortToBDD.get(component);
		if (aux.get(portName) == null) {
			try {
				logger.error(
						"BDD node of port {} of component {} is null. Possible reason: Port name in the glue not the same as in the BIP Spec.",
						portName, component.getId());
				throw new BIPEngineException("BDD node of a port " + portName + " of component " + component.getId()
						+ " is null. Possible reason: Port name in the glue not the same as in the BIP Spec.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		return aux.get(portName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.BehaviourEncoder#getBDDOfAState(org.bip.api.BIPComponent, java.lang.String)
	 */
	public synchronized BDD getBDDOfAState(BIPComponent component, String stateName) throws BIPEngineException {
		Hashtable<String, BDD> aux = componentToStateToBDD.get(component);
		if (aux.get(stateName) == null) {
			try {
				logger.error("BDD node of state {} is null", stateName);
				throw new BIPEngineException("BDD node of state " + stateName + " is null.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		return aux.get(stateName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.BehaviourEncoder#getStateToBDDOfAComponent(org.bip.api.BIPComponent)
	 */
	public synchronized Hashtable<String, BDD> getStateToBDDOfAComponent(BIPComponent component) {
		return componentToStateToBDD.get(component);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.BehaviourEncoder#getPortToBDDOfAComponent(org.bip.api.BIPComponent)
	 */
	public synchronized Hashtable<String, BDD> getPortToBDDOfAComponent(BIPComponent component) {
		return componentToPortToBDD.get(component);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.BehaviourEncoder#getPositionsOfPorts()
	 */
	public List<Integer> getPositionsOfPorts() {
		return positionsOfPorts;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.BehaviourEncoder#getPortToPosition()
	 */
	public Map<Port, Integer> getPortToPosition() {
		return portToPosition;
	}

}
