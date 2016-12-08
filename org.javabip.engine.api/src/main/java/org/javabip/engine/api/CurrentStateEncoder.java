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
package org.javabip.engine.api;

import java.util.Set;

import net.sf.javabdd.BDD;

import org.javabip.api.BIPComponent;
import org.javabip.api.Port;
import org.javabip.exceptions.BIPEngineException;

// TODO: Auto-generated Javadoc
/**
 * Receives information about the current state and the list of disabled ports of each registered component and computes
 * the current state BDDs.
 * 
 * @author mavridou
 */

public interface CurrentStateEncoder {

	/**
	 * Receives information about the current state and the list of disabled ports of each registered component due to
	 * guards that are not dealing with data transfer. If there are no disabled ports at this current state then this
	 * list is empty. A port that requires data transfer may still be disabled no matter what data transfer occurs
	 * afterwards. Therefore, the ports specified within the list of disabled ports may contain data ports.
	 * 
	 * The Current State Encoder gets this information through the Data Coordinator. When the inform is called, it means
	 * that all the calls to informSpecific have finished at this execution cycle for the particular component.
	 * 
	 * Returns the current state BDD of the specified component.
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
	BDD inform(BIPComponent component, String currentState, Set<Port> disabledPorts) throws BIPEngineException;

	/**
	 * Setter for the BIPCoordinator.
	 *
	 * @param wrapper
	 *            the new BIP coordinator.
	 */
	void setBIPCoordinator(BIPCoordinator wrapper);

	/**
	 * Setter for the BehaviourEncoder.
	 *
	 * @param behaviourEncoder
	 *            the new behaviour encoder.
	 */
	void setBehaviourEncoder(BehaviourEncoder behaviourEncoder);

	/**
	 * Setter for the BDDBIPEngine.
	 *
	 * @param engine
	 *            the new BDD engine.
	 */
	void setEngine(BDDBIPEngine engine);

}
