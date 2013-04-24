package org.bip.engine;

import java.util.ArrayList;
import java.util.Hashtable;

import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.behaviour.Behaviour;
import org.bip.behaviour.Port;

/**
 * Orchestrates the execution of the behaviour, glue and current state encoders.
 * At the initialization phase, it receives information about the behaviour of 
 * BIP components sends this to the behaviour encoder and orders it to compute the total behaviour BDD.
 * At the initialization phase, it also receives information about the glue, sends this to the glue 
 * encoder and orders it to compute the glue BDD. During each execution cycle, it receives information about 
 * the current state of the BIP components and their disabled ports, sends this to the current state encoder 
 * and orders it to compute the current state BDDs. When a new interaction is chosen by the engine, it notifies all the BIP components.
  * @author mavridou
 */

public interface BIPCoordinator extends BIPEngine {

	/**
	 * @param component 
	 * @return the unique identity of the specified BIP component
	 */
	Integer getBIPComponentIdentity(BIPComponent component);
	
	/**
	 * @param componentIdentity
	 * @return BIP component specified by the identity
	 */
	BIPComponent getBIPComponent(int componentIdentity);
	
	/**
	 * @param componentIdentity 
	 * @return behaviour of the BIP component specified by the identity
	 */
	Behaviour getBIPComponentBehaviour(int componentIdentity);

	/**
	 * @return number of registered components
	 */
	int getNoComponents();
	
	/**
	 * @return number of ports of registered components
	 */
	int getNoPorts();

	/**
	 * @return number of states of registered components
	 */
	int getNoStates();
	
	/**
	 * Notifies all the components whether they need to perform a transition
	 * @param allComponents, all components
	 */
	void execute(ArrayList<BIPComponent> allComponents, Hashtable<BIPComponent, ArrayList<Port>> allPorts);
	
	
}
