package org.bip.engine.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.api.Behaviour;
import org.bip.api.Port;
import org.bip.exceptions.BIPEngineException;

public interface Coordinator  extends BIPEngine, InteractionExecutor {

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
	
	BIPComponent getComponentFromObject(Object component);

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
	void specifyPermanentConstraints(Set<BDD> constraints);
	
	Map<Port, Integer> getPortsToPosition();
	

}
