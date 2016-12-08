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

import java.util.List;
import java.util.Map;

import org.javabip.api.BIPComponent;
import org.javabip.api.Port;
import org.javabip.exceptions.BIPEngineException;

import net.sf.javabdd.BDD;

/**
 * Receives information about the behaviour of each registered component and computes the total behaviour BDD.
 * 
 * @author mavridou
 */
public interface BehaviourEncoder {

	/**
	 * Creates BDD nodes in the BDD Manager that correspond to the ports and the states of all the registered
	 * components.
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
	void createBDDNodes(BIPComponent component, List<Port> componentPorts, List<String> componentStates)
			throws BIPEngineException;

	/**
	 * Computes and returns the BDD corresponding to the behaviour of a particular component.
	 *
	 * @param component
	 *            the component for which the BDD is computed.
	 * @return BDD that corresponds to the behaviour of the component.
	 * @throws BIPEngineException
	 *             when the component behaviour is null.
	 */
	BDD behaviourBDD(BIPComponent component) throws BIPEngineException;

	/**
	 * Setter for the BDDBIPEngine.
	 *
	 * @param engine
	 *            the new engine.
	 */
	void setEngine(BDDBIPEngine engine);

	/**
	 * Setter for the BIPCoordinator.
	 *
	 * @param wrapper
	 *            the new BIP coordinator.
	 */
	void setBIPCoordinator(BIPCoordinator wrapper);

	/**
	 * Provides a mapping between components and their state BDDs.
	 *
	 * @return the mapping between components and their state BDDs.
	 */
	Map<BIPComponent, BDD[]> getStateBDDs();

	/**
	 * Provides a mapping between components and their port BDDs.
	 *
	 * @return the mapping between components and their port BDDs.
	 */
	Map<BIPComponent, BDD[]> getPortBDDs();

	/**
	 * Gets the BDD of a particular port of a given component.
	 *
	 * @param component
	 *            the BIP component to which the port belongs.
	 * @param portName
	 *            the port name.
	 * @return BDD corresponding to the given port of a given component.
	 * @throws BIPEngineException
	 *             when the port BDD is null.
	 */
	BDD getBDDOfAPort(BIPComponent component, String portName) throws BIPEngineException;

	/**
	 * Gets the BDD of a particular state of a given component.
	 *
	 * @param component
	 *            the BIP component to which the state belongs.
	 * @param stateName
	 *            the state name.
	 * @return BDD corresponding to the given state of a given component.
	 * @throws BIPEngineException
	 *             when the state BDD is null.
	 */
	BDD getBDDOfAState(BIPComponent component, String stateName) throws BIPEngineException;

	/**
	 * Provides a mapping between component states and their BDDs.
	 *
	 * @param component
	 *            the BIP component.
	 * @return map with the states as keys and the state BDDs as values.
	 */
	Map<String, BDD> getStateToBDDOfAComponent(BIPComponent component);

	/**
	 * Provides a mapping between component ports and their BDDs.
	 *
	 * @param component
	 *            the BIP component.
	 * @return map with the ports as keys and the port BDDs as values.
	 */
	Map<String, BDD> getPortToBDDOfAComponent(BIPComponent component);

	/**
	 * Gets the positions of ports.
	 *
	 * @return the positions of ports
	 */
	List<Integer> getPositionsOfPorts();

	/**
	 * Provides a mapping between ports and their positions.
	 *
	 * @return the port to position
	 */
	Map<Port, Integer> getPortToPosition();

}
