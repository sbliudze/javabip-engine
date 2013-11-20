package org.bip.engine;

import java.util.Map;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.api.Behaviour;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;

/**
 * Orchestrates the execution of the behaviour, glue and current state encoders.
 * At the initialization phase, it receives information about the behaviour of 
 * BIP components sends this to the behaviour encoder and orders it to compute 
 * the total behaviour BDD. At the initialization phase, it also receives information
 *  about the glue, sends this to the glue encoder and orders it to compute the glue BDD.
 *  During each execution cycle, it receives information about the current state of 
 *  the BIP components and their disabled ports, sends this to the current state encoder 
 * and orders it to compute the current state BDDs. When a new interaction is chosen
 *  by the engine, it notifies all the BIP components.
  * @author mavridou
 */

public interface BIPCoordinator extends BIPEngine, InteractionExecutor {

	/**
	 * Returns the Behaviour of the BIP component.
	 * 
	 * @param a component instance
	 * @return behaviour of the BIP component
	 */
	Behaviour getBehaviourByComponent(BIPComponent component);
	
	/**
	 * Returns the BIP component instances registered in the system that correspond
	 * to the component type provided as a parameter.
	 * 
	 * 
	 * @param BIP component type
	 * @return arrayList of component instances that correspond to this component type.
	 * @throws BIPEngineException 
	 * @throws InterruptedException 
	 */
	Iterable <BIPComponent> getBIPComponentInstances(String type) throws BIPEngineException;

	/**
	 * Returns the number of registered component instances in the system.
	 * 
	 * @return number of registered components
	 * 
	 */
	int getNoComponents();
	
	/**
	 * Returns the total number of ports of registered component 
	 * instances in the system.
	 * 
	 * @return number of ports of registered components
	 */
	int getNoPorts();

	/**
	 * Returns the total number of states of registered component 
	 * instances in the system.
	 * 
	 * @return number of states of registered components
	 */
	int getNoStates();
	
	/**
	 * Notifies all the components whether they need to perform a transition.
	 * If yes, the ArrayList contains the port that should be fires. Otherwise, it contains null.
	 * 
	 * @param allComponents, all components
	 * @throws BIPEngineException 
	 */
	void execute (Iterable<Map<BIPComponent, Iterable<Port>>> portsToFire) throws BIPEngineException;
	/**
	 * Set the interaction Execute instance either as DataCoordinator or as BIPCoordinator depending
	 * on whether there are data transfer between the components or not respectively.
	 */
	
	void setInteractionExecutor(InteractionExecutor interactionExecutor);
	
    void informSpecific(BDD disabledCombination);
	
    BehaviourEncoder getBehaviourEncoderInstance();
    BDDBIPEngine getBDDBIPEngineInstance();

	void specifyDataGlue(BDD specifyDataGlue);
	
}
