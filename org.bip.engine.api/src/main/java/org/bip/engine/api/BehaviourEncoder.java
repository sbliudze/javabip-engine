package org.bip.engine.api;

import java.util.List;
import java.util.Map;

import org.bip.api.BIPComponent;
import org.bip.api.Behaviour;
import org.bip.api.Port;
import org.bip.exceptions.BIPEngineException;

import net.sf.javabdd.BDD;


// TODO: Auto-generated Javadoc
/**
 * Receives information about the behaviour of each registered component and computes the total behaviour BDD.
 * @author mavridou
 */
public interface BehaviourEncoder {

	/**
	 * Creates BDD nodes in the BDD Manager that correspond to the ports and the states of all the registered components.
	 *
	 * @param component the component
	 * @param componentPorts the component ports
	 * @param iterable the iterable
	 * @throws BIPEngineException the BIP engine exception
	 */
	void createBDDNodes(BIPComponent component, List<Port> componentPorts, List<String> iterable) throws BIPEngineException;
	
	/**
	 * Computes and returns the BDD corresponding to the behaviour of a particular component.
	 *
	 * @param component the component
	 * @return BDD that corresponds to the behaviour of the component
	 * @throws BIPEngineException the BIP engine exception
	 */
	BDD behaviourBDD(BIPComponent component) throws BIPEngineException;
	
	/**
	 * Setter for the BDDBIPEngine.
	 *
	 * @param engine the new engine
	 */
	void setEngine(BDDBIPEngine engine);

	/**
	 * Setter for the OSGiBIPEngine.
	 *
	 * @param wrapper the new BIP coordinator
	 */
    void setBIPCoordinator(BIPCoordinator wrapper);
    
	/**
	 * Gets the state bd ds.
	 *
	 * @return the BDDs that correspond to the states of each component
	 */
    Map<BIPComponent, BDD[]> getStateBDDs();

	/**
	 * Gets the port bd ds.
	 *
	 * @return the BDDs that correspond to the ports of each component
	 */
    Map<BIPComponent, BDD[]> getPortBDDs();
    
    /**
     * Gets the BDD of a port.
     *
     * @param component the component
     * @param portName the port name
     * @return BDD corresponding to a port of a component
     * @throws BIPEngineException the BIP engine exception
     */
    BDD getBDDOfAPort(BIPComponent component, String portName) throws BIPEngineException;
    
    /**
     * Gets the BDD of a state.
     *
     * @param component the component
     * @param stateName the state name
     * @return BDD corresponding to a state of a component
     * @throws BIPEngineException the BIP engine exception
     */
    BDD getBDDOfAState(BIPComponent component, String stateName) throws BIPEngineException;
    
    /**
     * Gets the state to bdd of a component.
     *
     * @param component the component
     * @return hashtable with the states as keys and the state BDDs as values
     */
    Map<String, BDD> getStateToBDDOfAComponent (BIPComponent component);
    
    /**
     * Gets the port to bdd of a component.
     *
     * @param component the component
     * @return hashtable with the ports as keys and the port BDDs as values
     */
    Map<String, BDD> getPortToBDDOfAComponent (BIPComponent component);

	/**
	 * Gets the positions of ports.
	 *
	 * @return the positions of ports
	 */
	List<Integer> getPositionsOfPorts();

	/**
	 * Gets the port to position.
	 *
	 * @return the port to position
	 */
	Map<Port, Integer> getPortToPosition();
	
	void deleteBDDNodes(BIPComponent component, Behaviour componentBehaviour);
	
}



