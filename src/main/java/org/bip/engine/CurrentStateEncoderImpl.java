package org.bip.engine;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.api.Port;
import org.bip.exceptions.BIPEngineException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives information about the current state and the list of disabled ports of each 
 * registered component and computes the current state BDDs.
 * @author mavridou
 */

public class CurrentStateEncoderImpl implements CurrentStateEncoder {

	private BehaviourEncoder behaviourEncoder; 
	private BDDBIPEngine engine;
	private BIPCoordinator wrapper;

	private Logger logger = LoggerFactory.getLogger(CurrentStateEncoderImpl.class);
	
	/**
	 * Computes the current State BDD. Takes as an argument the current state of the component
	 * and computes the disjunction of the BDD corresponding to this state with the negation of 
	 * the BDDs of all the other states of this component.
	 * 
	 * @param BIP Component that informs about its current state
	 * @param index of the current state of the component to be used to find the corresponding BDD
	 * @param Indexes of the disabled ports of this current state of the component to be used to find the corresponding BDDs
	 * 
	 * @return the current state BDD
	 */
	/**
	 * BIP Component informs about its current State and its list of disabled Ports due to guards.
	 * We find the index of the current state of the component and the index of the disabledPorts
	 * to be used to find the corresponding BDDs by calling the componentCurrentStateBDD().
	 * 
	 * @param BIP Component that informs about its current state
	 * @param Name of the current state of the component
	 * @param ArrayList of the disabled ports of the components
	 * 
	 * @return the current state BDD
	 */
	public synchronized BDD inform(BIPComponent component, String currentState, Set<Port> disabledPorts) throws BIPEngineException {
		assert(component != null);
		assert (currentState != null && !currentState.isEmpty());
		
		ArrayList<String> componentStates = (ArrayList<String>) wrapper.getBehaviourByComponent(component).getStates();
		Map<String, BDD> statesToBDDs = behaviourEncoder.getStateToBDDOfAComponent(component);
		Map<String, BDD> portsToBDDs = behaviourEncoder.getPortToBDDOfAComponent(component);
	
		if (currentState == null || currentState.isEmpty()) {
	        try {
				logger.error("Current state of component {} is null or empty "+component.getName());
				throw new BIPEngineException("Current State of component "+component.getName() +" is null.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
	      }
		BDD result = engine.getBDDManager().one().and(statesToBDDs.get(currentState));
		for (String componentState : componentStates){

			if (!componentState.equals(currentState)){
				result.andWith(statesToBDDs.get(componentState).not());
			}
		}

		for (Port disabledPort : disabledPorts){
			logger.trace("Conjunction of negated disabled ports: "+ disabledPort.getId()+ " of component "+ disabledPort.specType);
			BDD tmp = result.and(portsToBDDs.get(disabledPort.getId()).not());
			result.free();
			result = tmp;		
		}
		
		return result;
	}
	
	public void setBIPCoordinator(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}

	public void setBehaviourEncoder(BehaviourEncoder behaviourEncoder) {
		this.behaviourEncoder = behaviourEncoder;
	}

	public void setEngine(BDDBIPEngine engine) {
		this.engine = engine;
	}


}
