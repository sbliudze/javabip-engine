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
	public BDD inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) throws BIPEngineException {
		assert(component != null);
		assert (currentState != null && !currentState.isEmpty());
		assert (disabledPorts != null && !disabledPorts.isEmpty());
		
		ArrayList<String> componentStates = (ArrayList<String>) wrapper.getBehaviourByComponent(component).getStates();
		Hashtable<String, BDD> statesToBDDs = behaviourEncoder.getStateToBDDOfAComponent(component);
		Hashtable<String, BDD> portsToBDDs = behaviourEncoder.getPortToBDDOfAComponent(component);

		
//		if (currentState == null || currentState.isEmpty()) {
//	        try {
//				logger.error("Current state of component {} is null or empty "+component.getName());
//				throw new BIPEngineException("Current State of component is null");
//			} catch (BIPEngineException e) {
//				e.printStackTrace();
//				throw e;
//			}
//	      }
		
		BDD result = engine.getBDDManager().one().and(statesToBDDs.get(currentState));

		for (String componentState : componentStates){
			if (!componentState.equals(currentState)){
				result.andWith(statesToBDDs.get(componentState).not());
			}
		}
//		int stateIndex = 0;
//		while (stateIndex < componentStates.size() && !componentStates.get(stateIndex).equals(currentState)){
//			stateIndex++;
//		}
		
		for (Port disabledPort : disabledPorts){
			logger.debug("Conjunction of negated disabled ports.");
			result.andWith(portsToBDDs.get(disabledPort.id).not());
		}
		
		
//		int[] indexDisabledPorts = new int[disabledPorts.size()];
//		Hashtable<String, ArrayList<Port>> allStatePorts = (Hashtable<String, ArrayList<Port>>) wrapper.getBehaviourByComponent(component).getStateToPorts();
//		ArrayList<Port> currentStatePorts = allStatePorts.get(currentState);
//
//
//		for (int i = 0; i < disabledPorts.size(); i++) {
//			int j = 0;
//			int size = currentStatePorts.size();
//			boolean found = false;
//			while (!found && j < size) {
//				if (disabledPorts.get(i) == currentStatePorts.get(j)) {
//					indexDisabledPorts[i] = j;
//					found = true;
//				}
//				else {
//					j++;
//				}
//			}
//			if (!found) {
//				try {
//					logger.error("Disabled port {} of component {} cannot be found."+disabledPorts.get(i)+component.getName());	
//					throw new BIPEngineException("Disabled port cannot be found.");
//				} catch (BIPEngineException e) {
//					e.printStackTrace();
//
//				} 
//			}		
//		}
//		//return componentCurrentStateBDD(component, stateIndex, indexDisabledPorts);
//		
//		for (int i = 0; i < disabledPorts.length; i++) {
//			logger.debug("Conjunction of negated disabled ports.");
//			result.andWith(portsBDDs[disabledPorts[i]].not());
//		}
//		
		return result;
	}
	
//	/**
//	 * Computes the current State BDD. Takes as an argument the current state of the component
//	 * and computes the disjunction of the BDD corresponding to this state with the negation of 
//	 * the BDDs of all the other states of this component.
//	 * 
//	 * @param BIP Component that informs about its current state
//	 * @param index of the current state of the component to be used to find the corresponding BDD
//	 * @param Indexes of the disabled ports of this current state of the component to be used to find the corresponding BDDs
//	 * 
//	 * @return the current state BDD
//	 */
//	private BDD componentCurrentStateBDD(BIPComponent component, int stateID, int[] disabledPorts) {
//
//		int nbStates =  ((ArrayList<String>) wrapper.getBehaviourByComponent(component).getStates()).size();
//		BDD[] portsBDDs = behaviourEncoder.getPortBDDs().get(component);
//		BDD[] statesBDDs = behaviourEncoder.getStateBDDs().get(component);
//
//		BDD result = engine.getBDDManager().one().and(statesBDDs[stateID]);
//		for (int i = 0; i < nbStates; i++) {
//			if (i != stateID){
//				result.andWith(statesBDDs[i].not());
//			}
//		}
//		
//		for (int i = 0; i < disabledPorts.length; i++) {
//			logger.debug("Conjunction of negated disabled ports.");
//			result.andWith(portsBDDs[disabledPorts[i]].not());
//		}
//		
//		return result;
//	}
	
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
