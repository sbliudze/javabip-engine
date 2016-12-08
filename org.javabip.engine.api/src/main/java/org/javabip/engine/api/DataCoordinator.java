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
import java.util.Map.Entry;

import org.javabip.api.BIPEngine;
import org.javabip.api.Port;

// TODO: Auto-generated Javadoc
/**
 * The Interface DataCoordinator. Wraps the BIP Coordinator and deals with data transfer. Queries all the ports which
 * require data for guard computation, retrieving the data values from other participating components. Notifies the
 * engine kernel of the constraints due to data invalid for guards.
 */

public interface DataCoordinator extends BIPEngine, BIPCoordinator, InteractionExecutor {

	/**
	 * Gets the data encoder.
	 *
	 * @return the data encoder.
	 */
	DataEncoder getDataEncoder();

	/**
	 * Gets the mapping between d variable positions and data wires representing the d variables.
	 *
	 * @return the mapping between d variable positions and data wires
	 */
	Map<Integer, Entry<Port, Port>> getdVarPositionsToWires();

	/**
	 * Gets the positions of d variables in the system BDD.
	 *
	 * @return the list of d variables positions
	 */
	public List<Integer> getPositionsOfDVariables();
}
