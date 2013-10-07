package org.bip.engine;
import java.util.Map;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;

public interface DataEncoder {
	
	/**
	 * Receives information about the disabled ports due to data transfer information
	 * of a registered component. These ports are of different component instances.
	 * In the current implementation of the Port Object there is no information 
	 * about the holder component of a port. Therefore, the information about the 
	 * component holders has to be explicitly provided in the inform function.
	 * 
	 * It can be called several times through one component during one execution
	 * cycle of the engine. When the inform function implemented in the current state encoder
	 * is called for a particular component, this cannot be called anymore for this particular
	 * component.
	 * 
	 * Returns to the core engine the BDD corresponding to the disabled combination of ports of component instances.
	 * 
	 * @param A map that gives information about a disabled interaction of ports of component instances according to data transfer information
	 * @throws BIPEngineException 
	 */
	BDD informSpecific(Map<BIPComponent, Port> disabledCombinations) throws BIPEngineException;
	
	/**
	 * Setter for the BDDBIPEngine
	 */
	void setEngine(BDDBIPEngine engine);
}
