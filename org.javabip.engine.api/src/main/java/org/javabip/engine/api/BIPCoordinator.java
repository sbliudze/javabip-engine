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
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.javabip.api.BIPComponent;
import org.javabip.api.BIPEngine;
import org.javabip.api.Behaviour;
import org.javabip.exceptions.BIPEngineException;

// TODO: Auto-generated Javadoc
/**
 * Orchestrates the execution of the behaviour, glue and current state encoders. At the initialization phase, it
 * receives information about the behaviour of BIP components sends this to the behaviour encoder and orders it to
 * compute the total behaviour BDD. At the initialization phase, it also receives information about the glue, sends this
 * to the glue encoder and orders it to compute the glue BDD. During each execution cycle, it receives information about
 * the current state of the BIP components and their disabled ports, sends this to the current state encoder and orders
 * it to compute the current state BDDs. When a new interaction is chosen by the engine, it notifies all the BIP
 * components.
 */

public interface BIPCoordinator extends BIPEngine, InteractionExecutor {

	/**
	 * Returns the Behaviour of the specified BIP component.
	 *
	 * @param component
	 *            the component to get behaviour of.
	 * @return the behaviour of the BIP component.
	 */
	Behaviour getBehaviourByComponent(BIPComponent component);

	/**
	 * Returns the BIP component instances registered in the system that correspond to the component type provided as a
	 * parameter.
	 *
	 * @param type
	 *            the component type.
	 * @return a list of component instances that correspond to this component type.
	 * @throws BIPEngineException
	 *             when there are no components of this type registered.
	 */
	List<BIPComponent> getBIPComponentInstances(String type) throws BIPEngineException;

	/**
	 * Returns the number of registered component instances in the system.
	 * 
	 * @return number of registered component instances.
	 * 
	 */
	int getNoComponents();

	/**
	 * Returns the total number of ports of registered component instances in the system.
	 * 
	 * @return number of ports of registered components.
	 */
	int getNoPorts();

	/**
	 * Returns the total number of states of registered component instances in the system.
	 * 
	 * @return number of states of registered components.
	 */
	int getNoStates();

	/**
	 * Set the interaction Executor instance either as DataCoordinator or as BIPCoordinator depending on whether there
	 * are data transfer between the components or not respectively.
	 *
	 * @param interactionExecutor
	 *            the new interaction executor.
	 */
	void setInteractionExecutor(InteractionExecutor interactionExecutor);

	/**
	 * Gets the behaviour encoder instance.
	 *
	 * @return the behaviour encoder instance.
	 */
	BehaviourEncoder getBehaviourEncoderInstance();

	/**
	 * Gets the BDD manager.
	 *
	 * @return the BDD manager.
	 */
	BDDFactory getBDDManager();

	/**
	 * Specifies a temporary constraint.
	 *
	 * @param constraint
	 *            the temporary constraint BDD.
	 */
	void specifyTemporaryConstraints(BDD constraint);

	/**
	 * Specifies permanent constraints.
	 *
	 * @param constraints
	 *            the set of permanent constraint BDDs.
	 */
	void specifyPermanentConstraints(Set<BDD> constraints);

	/**
	 * Given an object, returns the corresponding BIP Component instance.
	 * 
	 * @param component
	 *            an object corresponding to the BIP component specification.
	 * @return the BIP component.
	 */
	BIPComponent getComponentFromObject(Object component);

}
