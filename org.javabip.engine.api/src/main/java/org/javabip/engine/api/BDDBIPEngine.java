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
import org.javabip.exceptions.BIPEngineException;

/**
 * Receives the current state, glue and behaviour BDDs. Computes the possible maximal interactions and picks one
 * non-deterministically. Notifies the OSGiBIPEngine about the outcome.
 * 
 * @author mavridou
 */
public interface BDDBIPEngine {

	/**
	 * Inform the kernel engine of the current state BDD of the specified component.
	 *
	 * @param component
	 *            the specific BIP component.
	 * @param componentBDD
	 *            BDD corresponding to the current state of the particular component.
	 */
	void informCurrentState(BIPComponent component, BDD componentBDD);

	/**
	 * Inform the kernel engine of the behaviour BDD of the specified component.
	 *
	 * @param component
	 *            the specific BIP component.
	 * @param componentBDD
	 *            BDD corresponding to the behavior of the particular component.
	 */
	void informBehaviour(BIPComponent component, BDD componentBDD);

	/**
	 * Inform the kernel engine of the glue BDDs of the system.
	 *
	 * @param totalGlue
	 *            BDD corresponding to the total glue of the components of the system.
	 */
	void informGlue(List<BDD> totalGlue);

	/**
	 * Computes the total behaviour BDD.
	 *
	 */
	void totalBehaviourBDD();

	/**
	 * Computes possible maximal interactions and chooses one non-deterministically.
	 *
	 * @throws BIPEngineException
	 *             when current state or disabled combinations BDD is null and in case of deadlock.
	 */
	void runOneIteration() throws BIPEngineException;

	/**
	 * Setter for the BIPCoordinator.
	 *
	 * @param wrapper
	 *            the new BIP Coordinator.
	 */
	void setBIPCoordinator(BIPCoordinator wrapper);

	/**
	 * Getter for the BDD Manager.
	 *
	 * @return the BDD manager.
	 */
	BDDFactory getBDDManager();

	/**
	 * Specifies additional temporary constraints, for example, the ones imposed by data exchange.
	 *
	 * @param extraConstraint
	 *            the BDD corresponding to the extra constraint.
	 */
	void specifyTemporaryExtraConstraints(BDD extraConstraint);

	/**
	 * Specifies additional permanent constraints, for example, the ones imposed by data wires.
	 *
	 * @param extraConstraints
	 *            the set of BDDs corresponding to additional permanent constraint
	 */
	void specifyPermanentExtraConstraints(Set<BDD> extraConstraints);

}
