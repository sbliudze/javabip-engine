package org.bip.engine.api;

import java.util.Set;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.api.Port;
import org.bip.exceptions.BIPEngineException;

// TODO: Auto-generated Javadoc
/**
 * Receives information about the current state and the list of disabled ports of 
 * each registered component and computes the current state BDDs.
 * @author mavridou
 */

public interface CurrentStateEncoder {
	
	/**
	 * Receives information about the current state and the list of 
	 * disabled ports of each registered component due to guards that are not dealing with data transfer.
	 * If there are no disabled ports at this current state then this list is empty. 
	 * A port that requires data transfer may still be disabled no matter what data 
	 * transfer occurs afterwards. Therefore, the ports specified within the list of 
	 * disabled ports may contain data ports.
	 * 
	 * The Current State Encoder gets this information through the Data Coordinator. When the inform is called,
	 * it means that all the calls to informSpecific have finished at this execution cycle for the particular component.
	 * 
	 * Returns the current state BDD of the specified component.
	 *
	 * @param component the component
	 * @param currentState the current state
	 * @param disabledPorts the disabled ports
	 * @return the bdd
	 * @throws BIPEngineException the BIP engine exception
	 */
	BDD inform(BIPComponent component, String currentState, Set<Port> disabledPorts) throws BIPEngineException;
	
	/**
	 * Setter for the BIPCoordinator.
	 *
	 * @param wrapper the new BIP coordinator
	 */
	void setBIPCoordinator(BIPCoordinator wrapper);
	
	/**
	 * Setter for the BehaviourEncoder.
	 *
	 * @param behaviourEncoder the new behaviour encoder
	 */
	void setBehaviourEncoder(BehaviourEncoder behaviourEncoder);
	
	/**
	 * Setter for the BDDBIPEngine.
	 *
	 * @param engine the new engine
	 */
	void setEngine(BDDBIPEngine engine);
	
}



