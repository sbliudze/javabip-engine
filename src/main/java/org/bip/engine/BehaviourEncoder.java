package org.bip.engine;

import java.util.Hashtable;

import org.bip.api.BIPComponent;

import net.sf.javabdd.BDD;


/**
 * Receives information about the behaviour of each registered component and computes the total behaviour BDD.
 * @author mavridou
 */
public interface BehaviourEncoder {

	/**
	 * Creates BDD nodes in the BDD Manager that correspond to the ports and the states of all the registered components.
	 * @param componentID
	 * @param noComponentPorts
	 * @param noComponentStates
	 */
	void createBDDNodes(BIPComponent component, int noComponentPorts, int noComponentStates);
	
	/**
	 * 
	 * @param componentID
	 * @return BDD that corresponds to the behaviour of the component
	 */
	BDD behaviourBDD(int componentID);
	
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
    Hashtable<BIPComponent, BDD[]> getStateBDDs();

	/**
	 * @return the BDDs that correspond to the ports of each component
	 */
    Hashtable<BIPComponent, BDD[]> getPortBDDs();
	
}



