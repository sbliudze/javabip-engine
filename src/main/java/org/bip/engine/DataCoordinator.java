package org.bip.engine;

import java.util.ArrayList;

import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.api.Behaviour;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;

/**
 * @author mavridou
 */
public interface DataCoordinator extends BIPEngine, InteractionExecutor {

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
	
	ArrayList<Port> getDataOutPorts(BIPComponent disabledComponent, Port decidingPort);
}
