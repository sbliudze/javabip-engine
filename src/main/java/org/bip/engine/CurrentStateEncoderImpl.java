package org.bip.engine;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.api.Port;
import org.bip.engine.api.BDDBIPEngine;
import org.bip.engine.api.BIPCoordinator;
import org.bip.engine.api.BehaviourEncoder;
import org.bip.engine.api.CurrentStateEncoder;
import org.bip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Auto-generated Javadoc
/**
 * Receives information about the current state and the list of disabled ports of each 
 * registered component and computes the current state BDDs.
 * @author mavridou
 */

public class CurrentStateEncoderImpl implements CurrentStateEncoder {

	/** The behaviour encoder. */
	private BehaviourEncoder behaviourEncoder; 
	
	/** The engine. */
	private BDDBIPEngine engine;
	
	/** The wrapper. */
	private BIPCoordinator wrapper;

	/** The logger. */
	private Logger logger = LoggerFactory.getLogger(CurrentStateEncoderImpl.class);
	
	/**
	 * Computes the current State BDD. Takes as an argument the current state of the component
	 * and computes the disjunction of the BDD corresponding to this state with the negation of 
	 * the BDDs of all the other states of this component.
	 *
	 * @param component the component
	 * @param currentState the current state
	 * @param disabledPorts the disabled ports
	 * @return the current state BDD
	 * @throws BIPEngineException the BIP engine exception
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
		
		ArrayList<String> componentStates = new ArrayList<String>( wrapper.getBehaviourByComponent(component).getStates());
		Map<String, BDD> statesToBDDs = behaviourEncoder.getStateToBDDOfAComponent(component);
		Map<String, BDD> portsToBDDs = behaviourEncoder.getPortToBDDOfAComponent(component);
	
		if (currentState == null || currentState.isEmpty()) {
	        try {
				logger.error("Current state of component {} is null or empty "+component.getId());
				throw new BIPEngineException("Current State of component "+component.getId() +" is null.");
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
			logger.trace("Conjunction of negated disabled ports: "+ disabledPort.getId()+ " of component "+ disabledPort.getSpecType());
			BDD tmp = result.and(portsToBDDs.get(disabledPort.getId()).not());
			result.free();
			result = tmp;		
		}
		
		return result;
	}
	
	/* (non-Javadoc)
	 * @see org.bip.engine.api.CurrentStateEncoder#setBIPCoordinator(org.bip.engine.api.BIPCoordinator)
	 */
	public void setBIPCoordinator(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}

	/* (non-Javadoc)
	 * @see org.bip.engine.api.CurrentStateEncoder#setBehaviourEncoder(org.bip.engine.api.BehaviourEncoder)
	 */
	public void setBehaviourEncoder(BehaviourEncoder behaviourEncoder) {
		this.behaviourEncoder = behaviourEncoder;
	}

	/* (non-Javadoc)
	 * @see org.bip.engine.api.CurrentStateEncoder#setEngine(org.bip.engine.api.BDDBIPEngine)
	 */
	public void setEngine(BDDBIPEngine engine) {
		this.engine = engine;
	}


}
