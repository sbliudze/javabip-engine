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
 * Receives information about the current state and the list of disabled ports of each registered component and computes the current state BDDs.
 * @author mavridou
 */

/** Computes the BDD of the Current State of all components */
public class CurrentStateEncoderImpl implements CurrentStateEncoder {

	private BehaviourEncoder behaviourEncoder; 
	private BDDBIPEngine engine;
	private BIPCoordinator wrapper;

	private Logger logger = LoggerFactory.getLogger(CurrentStateEncoderImpl.class);


	public BDD inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) throws BIPEngineException {
		ArrayList<String> componentStates = (ArrayList<String>) wrapper.getBehaviourByComponent(component).getStates();
		
		if (currentState == null || currentState.isEmpty()) {
	        try {
				logger.error("Component did not inform about its current state correctly");
				throw new BIPEngineException("Current State of component is null");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
	      }


		int StateID = 0;
		for (int i = 0; i < componentStates.size(); i++) {
			if (componentStates.get(i).equals(currentState)) {
				StateID = i;
				break;
			}
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
					throw new BIPEngineException("Disabled Port cannot be found.");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					logger.error(e.getMessage());	
				} 
			}		
		}

		return componentCurrentStateBDD(component, StateID, indexDisabledPorts);
	}

	private synchronized BDD componentCurrentStateBDD(BIPComponent component, int stateID, int[] disabledPorts) {

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
