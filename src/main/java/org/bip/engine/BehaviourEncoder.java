package org.bip.engine;

import java.util.List;
import java.util.Map;

import org.bip.api.BIPComponent;
import org.bip.api.Port;
import org.bip.exceptions.BIPEngineException;

import net.sf.javabdd.BDD;


/**
 * Receives information about the behaviour of each registered component and computes the total behaviour BDD.
 * @author mavridou
 */
public interface BehaviourEncoder {

	/**
	 * Creates BDD nodes in the BDD Manager that correspond to the ports and the states of all the registered components.
	 * 
	 * @param componentID
	 * @param componentPorts
	 * @param iterable
	 * @throws BIPEngineException 
	 */
	void createBDDNodes(BIPComponent component, List<Port> componentPorts, List<String> iterable) throws BIPEngineException;
	
	/**
	 * Computes and returns the BDD corresponding to the behaviour of a particular component.
	 * 
	 * @param component
	 * 
	 * @return BDD that corresponds to the behaviour of the component
	 * @throws BIPEngineException 
	 */
	BDD behaviourBDD(BIPComponent component) throws BIPEngineException;
	
	/**
	 * Setter for the BDDBIPEngine
	 */
	void setEngine(BDDBIPEngine engine);

	/**
	 * Setter for the OSGiBIPEngine
	 */
    void setBIPCoordinator(BIPCoordinator wrapper);
    
	/**
	 * @return the BDDs that correspond to the states of each component
	 */
    Map<BIPComponent, BDD[]> getStateBDDs();

	/**
	 * @return the BDDs that correspond to the ports of each component
	 */
    Map<BIPComponent, BDD[]> getPortBDDs();
    
    /**
     * @param component
     * @param port name
     * 
     * @return BDD corresponding to a port of a component
     * @throws BIPEngineException 
     */
    BDD getBDDOfAPort(BIPComponent component, String portName) throws BIPEngineException;
    
    /**
     * @param component
     * @param state name
     * 
     * @return BDD corresponding to a state of a component
     * @throws BIPEngineException 
     */
    BDD getBDDOfAState(BIPComponent component, String stateName) throws BIPEngineException;
    
    /**
     * @param component 
     * @return hashtable with the states as keys and the state BDDs as values
     */
    Map<String, BDD> getStateToBDDOfAComponent (BIPComponent component);
    
    /**
     * @param component 
     * @return hashtable with the ports as keys and the port BDDs as values
     */
    Map<String, BDD> getPortToBDDOfAComponent (BIPComponent component);

	List<Integer> getPositionsOfPorts();

	Map<Port, Integer> getPortToPosition();
	
}



