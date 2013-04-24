package org.bip.engine;
import java.util.ArrayList;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;

/**
 * Receives information about the current state and the list of disabled ports of each registered component and computes the current state BDDs.
 * @author mavridou
 */

public interface CurrentStateEncoder {
	
	/**
	 * Receives information about the current state and the list of disabled ports of each registered component.
	 * Returns the current state BDD of the specified component.
	 * @param component
	 * @param currentState
	 * @param disabledPorts
	 */
	BDD inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts);
	
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



