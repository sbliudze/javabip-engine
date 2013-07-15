package org.bip.engine;

import java.util.ArrayList;
import java.util.Hashtable;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;
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

	//TODO: change commends
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
	public BDD inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) throws BIPEngineException {
		ArrayList<String> componentStates = (ArrayList<String>) wrapper.getBehaviourByComponent(component).getStates();
		
		if (currentState == null || currentState.isEmpty()) {
	        try {
				logger.error("Current state of component {} is null or empty "+component.getName());
				throw new BIPEngineException("Current State of component is null");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
	      }

		int stateIndex = 0;
		while (stateIndex < componentStates.size() && !componentStates.get(stateIndex).equals(currentState)){
			stateIndex++;
		}

		int[] indexDisabledPorts = new int[disabledPorts.size()];
		Hashtable<String, ArrayList<Port>> allStatePorts = (Hashtable<String, ArrayList<Port>>) wrapper.getBehaviourByComponent(component).getStateToPorts();
		ArrayList<Port> currentStatePorts = allStatePorts.get(currentState);


		for (int l = 0; l < disabledPorts.size(); l++) {
			int k = 0;
			int size = currentStatePorts.size();
			boolean found = false;
			while (!found && k < size) {
				if (disabledPorts.get(l) == currentStatePorts.get(k)) {
					indexDisabledPorts[l] = k;
					found = true;
				}
				else {
					k++;
				}
			}

			if (!found) {
				try {
					logger.error("Disabled port {} of component {} cannot be found."+disabledPorts.get(l)+component.getName());	
					throw new BIPEngineException("Disabled port cannot be found.");
				} catch (BIPEngineException e) {
					e.printStackTrace();

				} 
			}		
		}

		return componentCurrentStateBDD(component, stateIndex, indexDisabledPorts);
	}
	
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
	private BDD componentCurrentStateBDD(BIPComponent component, int stateID, int[] disabledPorts) {

		int nbStates =  ((ArrayList<String>) wrapper.getBehaviourByComponent(component).getStates()).size();
		BDD[] portsBDDs = behaviourEncoder.getPortBDDs().get(component);
		BDD[] statesBDDs = behaviourEncoder.getStateBDDs().get(component);

		BDD result = engine.getBDDManager().one().and(statesBDDs[stateID]);
		for (int i = 0; i < nbStates; i++) {
			if (i != stateID){
				result.andWith(statesBDDs[i].not());
			}
		}
		
		for (int i = 0; i < disabledPorts.length; i++) {
			result.andWith(portsBDDs[disabledPorts[i]].not());
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
