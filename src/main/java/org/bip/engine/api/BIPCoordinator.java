package org.bip.engine.api;

import java.util.List;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.api.Behaviour;
import org.bip.exceptions.BIPEngineException;

// TODO: Auto-generated Javadoc
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
	 * @param component the component
	 * @return behaviour of the BIP component
	 */
	Behaviour getBehaviourByComponent(BIPComponent component);
	
	/**
	 * Returns the BIP component instances registered in the system that correspond
	 * to the component type provided as a parameter.
	 *
	 * @param type the type
	 * @return arrayList of component instances that correspond to this component type.
	 * @throws BIPEngineException the BIP engine exception
	 */
	List <BIPComponent> getBIPComponentInstances(String type) throws BIPEngineException;

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
	 * Set the interaction Execute instance either as DataCoordinator or as BIPCoordinator depending
	 * on whether there are data transfer between the components or not respectively.
	 *
	 * @param interactionExecutor the new interaction executor
	 */
	void setInteractionExecutor(InteractionExecutor interactionExecutor);
	
    /**
     * Gets the behaviour encoder instance.
     *
     * @return the behaviour encoder instance
     */
    BehaviourEncoder getBehaviourEncoderInstance();
    
    /**
     * Gets the BDD manager.
     *
     * @return the BDD manager
     */
    BDDFactory getBDDManager();

    /**
     * Specify temporary constraints.
     *
     * @param constraints the constraints
     */
    void specifyTemporaryConstraints(BDD constraints);
	
	/**
	 * Specify permanent constraints.
	 *
	 * @param constraints the constraints
	 */
	void specifyPermanentConstraints(BDD constraints);
	
}