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

import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.javabip.api.BIPComponent;
import org.javabip.api.DataWire;
import org.javabip.api.Port;
import org.javabip.exceptions.BIPEngineException;

// TODO: Auto-generated Javadoc
/**
 * Receives information about the data wires at initialization and disabled combinations at each execution cycle for
 * each registered component and computes the data-related BDDs. Encodes the informSpecific information.
 * 
 * @author Anastasia Mavridou
 */
public interface DataEncoder {

	/**
	 * Receives information about the disabled ports due to data transfer information of a registered component. These
	 * ports are of different component instances. In the current implementation of the Port Object there is no
	 * information about the holder component of a port. Therefore, the information about the component holders has to
	 * be explicitly provided in the inform function.
	 * 
	 * It can be called several times through one component during one execution cycle of the engine. When the inform
	 * function implemented in the current state encoder is called for a particular component, this function cannot be
	 * called anymore for this particular component.
	 * 
	 * Returns to the core engine the BDD corresponding to the disabled combination of ports of component instances.
	 * 
	 *
	 * @param decidingComponent
	 *            the deciding component (component which checks its guards).
	 * @param decidingPort
	 *            the deciding port (whose guard require data).
	 * @param disabledCombinations
	 *            the combinations disabled due to data transfer.
	 * @return the BDD encoding the disabled combinations.
	 * @throws BIPEngineException
	 *             when the ports of disabled combinations are null.
	 */
	BDD encodeDisabledCombinations(BIPComponent decidingComponent, Port decidingPort,
			Map<BIPComponent, Set<Port>> disabledCombinations) throws BIPEngineException;

	/**
	 * Receives the information about the data wires of the system.
	 *
	 * @param dataGlue
	 *            the list of the data wires.
	 * @return the set of BDDs encoding the data wires.
	 * @throws BIPEngineException
	 *             when the data glue is null.
	 */
	Set<BDD> specifyDataGlue(Iterable<DataWire> dataGlue) throws BIPEngineException;

	/**
	 * Setter for the BDDBIPEngine.
	 *
	 * @param manager
	 *            the new BDD manager.
	 */
	void setBDDManager(BDDFactory manager);

	/**
	 * Setter for the DataCoordinator.
	 *
	 * @param dataCoordinator
	 *            the new data coordinator.
	 */
	void setDataCoordinator(DataCoordinator dataCoordinator);

	/**
	 * Setter for the Behaviour Encoder.
	 *
	 * @param behaviourEncoder
	 *            the new behaviour encoder.
	 */
	void setBehaviourEncoder(BehaviourEncoder behaviourEncoder);

}
