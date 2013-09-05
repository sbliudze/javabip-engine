package org.bip.engine;
import java.util.Map;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;

public interface DataEncoder {
	
	/**
	 * Receives information about the current state and the list of 
	 * disabled ports of each registered component.
	 * Returns the current state BDD of the specified component.
	 * @param component
	 * @param currentState
	 * @param disabledPorts
	 * @throws BIPEngineException 
	 */
	void inform(Map<BIPComponent, Port> disabledCombinations);
	
	/**
	 * Setter for the OSGiBIPEngine
	 */
	void setBIPCoordinator(BIPCoordinator wrapper);
	/**
	 * Setter for the BehaviourEncoder
	 */
	void setBehaviourEncoder(BehaviourEncoder behaviourEncoder);
	/**
	 * Setter for the BDDBIPEngine
	 */
	void setEngine(BDDBIPEngine engine);
}
