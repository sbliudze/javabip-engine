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
import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;

import org.javabip.api.BIPComponent;
import org.javabip.api.Behaviour;
import org.javabip.api.Port;
import org.javabip.engine.api.BDDBIPEngine;
import org.javabip.engine.api.BIPCoordinator;
import org.javabip.engine.api.BehaviourEncoder;
import org.javabip.engine.api.CurrentStateEncoder;
import org.javabip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Auto-generated Javadoc
/**
 * Receives information about the current state and the list of disabled ports of each registered component and computes
 * the current state BDDs.
 * 
 * @author mavridou
 */

public class CurrentStateEncoderImpl implements CurrentStateEncoder {

	/** The behaviour encoder. */
	private BehaviourEncoder behaviourEncoder;

	/** The engine. */
	private BDDBIPEngine engine;

	/** The wrapper. */
	private BIPCoordinator wrapper;

	/** The logger. */
	private Logger logger = LoggerFactory.getLogger(CurrentStateEncoderImpl.class);

	/**
	 * Computes the current State BDD. Takes as an argument the current state of the component and computes the
	 * disjunction of the BDD corresponding to this state with the negation of the BDDs of all the other states of this
	 * component. 
	 * BIP Component informs about its current State and its list of disabled Ports due to guards. We find
	 * the index of the current state of the component and the index of the disabledPorts to be used to find the
	 * corresponding BDDs by calling the componentCurrentStateBDD().
	 *
	 * @param component
	 *            the BIP component.
	 * @param currentState
	 *            the current state of the component.
	 * @param disabledPorts
	 *            the set of disabled ports of the component.
	 * @return the current state BDD.
	 * @throws BIPEngineException
	 *             when the current state is null.
	 */
	public synchronized BDD inform(BIPComponent component, String currentState, Set<Port> disabledPorts)
			throws BIPEngineException {
		assert (component != null);
		assert (currentState != null && !currentState.isEmpty());

		// For debugging
		Behaviour behaviour = wrapper.getBehaviourByComponent(component);
		assert (behaviour != null);

		ArrayList<String> componentStates = new ArrayList<String>(behaviour.getStates());
		Map<String, BDD> statesToBDDs = behaviourEncoder.getStateToBDDOfAComponent(component);
		Map<String, BDD> portsToBDDs = behaviourEncoder.getPortToBDDOfAComponent(component);

		if (currentState == null || currentState.isEmpty()) {
			try {
				logger.error("Current state of component {} is null or empty " + component.getId());
				throw new BIPEngineException("Current State of component " + component.getId() + " is null.");
			} catch (BIPEngineException e) {
				// e.printStackTrace();
				throw e;
			}
		}
		BDD result = engine.getBDDManager().one().and(statesToBDDs.get(currentState));
		for (String componentState : componentStates) {

			if (!componentState.equals(currentState)) {
				result.andWith(statesToBDDs.get(componentState).not());
			}
		}

		for (Port disabledPort : disabledPorts) {
			logger.trace("Conjunction of negated disabled ports: " + disabledPort.getId() + " of component "
					+ disabledPort.getSpecType());
			BDD tmp = result.and(portsToBDDs.get(disabledPort.getId()).not());
			result.free();
			result = tmp;
		}

		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.CurrentStateEncoder#setBIPCoordinator(org.bip.engine.api.BIPCoordinator)
	 */
	public void setBIPCoordinator(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.CurrentStateEncoder#setBehaviourEncoder(org.bip.engine.api.BehaviourEncoder)
	 */
	public void setBehaviourEncoder(BehaviourEncoder behaviourEncoder) {
		this.behaviourEncoder = behaviourEncoder;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.bip.engine.api.CurrentStateEncoder#setEngine(org.bip.engine.api.BDDBIPEngine)
	 */
	public void setEngine(BDDBIPEngine engine) {
		this.engine = engine;
	}

}
