package org.bip.engine.api;

import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;
import org.bip.api.DataWire;
import org.bip.api.Port;
import org.bip.exceptions.BIPEngineException;

// TODO: Auto-generated Javadoc
/**
 * Deals with the DataGlue.
 * Encodes the informSpecific information.
 * @author Anastasia Mavridou
 */
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
	 *
	 * @param decidingComponent the deciding component
	 * @param decidingPort the deciding port
	 * @param disabledCombinations the disabled combinations
	 * @return the bdd
	 * @throws BIPEngineException the BIP engine exception
	 */
	BDD encodeDisabledCombinations(BIPComponent decidingComponent, Port decidingPort, Map<BIPComponent, Set<Port>> disabledCombinations) throws BIPEngineException;
	
    /**
     * Receives the information about the data wires of the system.
     *
     * @param dataGlue the data glue
     * @return the bdd
     * @throws BIPEngineException the BIP engine exception
     */
	Set<BDD> specifyDataGlue(Iterable<DataWire> dataGlue) throws BIPEngineException;
    
	/**
	 * Setter for the BDDBIPEngine.
	 *
	 * @param manager the new BDD manager
	 */
	void setBDDManager(BDDFactory manager);
	
	/**
	 * Setter for the DataCoordinator.
	 *
	 * @param dataCoordinator the new data coordinator
	 */
	void setDataCoordinator(DataCoordinator dataCoordinator);
	
	/**
	 * Setter for the Behaviour Encoder.
	 *
	 * @param behaviourEncoder the new behaviour encoder
	 */
	void setBehaviourEncoder(BehaviourEncoder behaviourEncoder);


	Set<BDD> extendDataBDDNodes(Iterable<DataWire> wires, Set<BIPComponent> newComponents);

}
