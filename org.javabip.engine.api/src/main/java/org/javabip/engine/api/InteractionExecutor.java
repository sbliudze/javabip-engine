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

import org.javabip.api.Port;
import org.javabip.exceptions.BIPEngineException;

/**
 * The Interface InteractionExecutor.
 * 
 * @author mavridou
 */
public interface InteractionExecutor {

	/**
	 * Called by the engine, receives the byte valuation which is then transformed to a list of interactions and sent
	 * either to the BIP Coordinator or to Data Coordinator.
	 * 
	 * @param valuation
	 *            the valuation containing the engine decision of the ports to be executed and data transfers to be
	 *            performed.
	 * @throws BIPEngineException
	 *             the BIP engine exception
	 */
	void execute(byte[] valuation) throws BIPEngineException;

	/**
	 * Notifies all the components whether they need to perform a transition.
	 * 
	 * @param portsToFire
	 *            the list of interactions, each containing one or several ports.
	 * @throws BIPEngineException
	 *             if one of the ports is empty or does not have an associated component.
	 */
	public void executeInteractions(List<List<Port>> portsToFire) throws BIPEngineException;
}
