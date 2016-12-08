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

import net.sf.javabdd.BDD;

import org.javabip.api.BIPGlue;
import org.javabip.exceptions.BIPEngineException;

/**
 * Receives information about the glue and computes the glue BDD.
 * 
 * @author Anastasia Mavridou
 */

public interface GlueEncoder {

	/**
	 * Receives the information about the glue of the system.
	 * 
	 * @param glue
	 *            the BIP glue.
	 * @throws BIPEngineException
	 *             when glue is null.
	 */
	void specifyGlue(BIPGlue glue) throws BIPEngineException;

	/**
	 * Setter for the BehaviourEncoder
	 * 
	 * @param behaviourEncoder
	 *            the new behaviour encoder.
	 */
	void setBehaviourEncoder(BehaviourEncoder behaviourEncoder);

	/**
	 * Setter for the BDDBIPEngine
	 * 
	 * @param engine
	 *            the new BDD engine.
	 */
	void setEngine(BDDBIPEngine engine);

	/**
	 * Setter for the BIP Coordinator
	 * 
	 * @param wrapper
	 *            the new BIP coordinator.
	 */
	void setBIPCoordinator(BIPCoordinator wrapper);

	/**
	 * Computes the total Glue BDD.
	 * 
	 * @return the total Glue BDD.
	 * @throws BIPEngineException
	 *             when glue is not correctly specified.
	 */
	List<BDD> totalGlue() throws BIPEngineException;

}
