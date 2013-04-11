package org.bip.engine;

import java.util.ArrayList;
import java.util.Hashtable;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Computes the BDD of the Current State of all components */
public class CurrentStateEncoderImpl implements CurrentStateEncoder {

	private BehaviourEncoderImpl behaviourEncoder; //TODO, use the IFs instead
	private BDDBIPEngineImpl engine;
	private OSGiBIPEngine wrapper;

	private Logger logger = LoggerFactory.getLogger(CurrentStateEncoderImpl.class);

	public void setOSGiBIPEngine(OSGiBIPEngine wrapper) {
		this.wrapper = wrapper;

	}

	public void setBehaviourEncoder(BehaviourEncoderImpl behaviourEncoder) {
		this.behaviourEncoder = behaviourEncoder;
	}

	public void setEngine(BDDBIPEngineImpl engine) {
		this.engine = engine;
	}

	public BDD inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) {
		Integer CompID = wrapper.reversedIdentityMapping.get(component);
		ArrayList<String> componentStates = wrapper.behaviourMapping.get(CompID).getStates();

		int StateID = 0;
		for (int i = 1; i <= componentStates.size(); i++) {
			if (componentStates.get(i - 1).equals(currentState)) {
				StateID = i;
				break;
			}
		}

		int[] noDisabledPorts = new int[disabledPorts.size()];
		Hashtable<String, ArrayList<Port>> statePorts = new Hashtable<String, ArrayList<Port>>();
		statePorts = wrapper.behaviourMapping.get(CompID).getStateToPorts();
		ArrayList<Port> componentPorts = new ArrayList<Port>();
		componentPorts = statePorts.get(currentState);

		for (int l = 0; l < disabledPorts.size(); l++) {
			int k = 0;
			while (disabledPorts.get(l) != componentPorts.get(k)) {
				if (k == statePorts.get(StateID).size() - 1) {
					System.err.println("Disabled Port cannot be found.");
					break;
				}
				k++;
			}
			if (disabledPorts.get(l) == componentPorts.get(k))
				noDisabledPorts[noDisabledPorts.length - 1] = k;
		}

		return componentCurrentStateBDD(CompID, StateID, noDisabledPorts);
	}

	private synchronized BDD componentCurrentStateBDD(Integer ComponentID, int stateID, int[] disabledPorts) {

		int noStates = wrapper.behaviourMapping.get(ComponentID).getStates().size();
		int noPorts = wrapper.behaviourMapping.get(ComponentID).getEnforceablePorts().size();
		//System.out.println("BehaviourEncoder bddPort size: " + behaviourEncoder.getPortBDDs().size());
		BDD[] portsBDDs = new BDD[noPorts];
		BDD[] statesBDDs = new BDD[noStates];

		for (int i = 0; i < noPorts; i++) {
			Hashtable<Integer, BDD[]> tempPortBDDs = behaviourEncoder.getPortBDDs();
			//logger.error("For component {} Port bdds are {}", ComponentID, behaviourEncoder.getPortBDDs().get(ComponentID)[i]);
			portsBDDs[i] = behaviourEncoder.getPortBDDs().get(ComponentID)[i];
		}
		for (int j = 0; j < noStates; j++) {
			//logger.error("For component {} State bdds are {}", ComponentID, behaviourEncoder.getStateBDDs().get(ComponentID)[j]);
			statesBDDs[j] = behaviourEncoder.getStateBDDs().get(ComponentID)[j];
		}

		BDD partialBDD[] = new BDD[2];
		BDD aux1 = null;
		try {
			aux1 = engine.bdd_mgr.one();
			for (int k = 1; k <= noStates; k++) {
				if (stateID == k) {
					//logger.error("Bdd of state {} is {}", k, statesBDDs[k - 1]);
					partialBDD[0] = aux1.and(statesBDDs[k - 1]);
				} else {
					partialBDD[0] = aux1.and(statesBDDs[k - 1].not());
				}
				if (k != noStates) {
					aux1.free();
					aux1 = partialBDD[0];
				}
			}
		} finally {
			if (aux1 != null)
				aux1.free();
		}

		BDD aux2 = null;
		try {
			aux2 = engine.bdd_mgr.one();
			for (int k = 0; k < disabledPorts.length; k++) {
				partialBDD[1] = aux2.and(portsBDDs[disabledPorts[k]].not());
				if (k != disabledPorts.length - 1) {
					aux2.free();
					aux2 = partialBDD[1];
				}
			}
		} finally {
			if (aux2 != null)
				aux2.free();
		}

		BDD componentCurrentStateBDD;
		if (disabledPorts.length != 0) {
			componentCurrentStateBDD = partialBDD[0].and(partialBDD[1]);
			partialBDD[0].free();
			partialBDD[1].free();
		} else {
			componentCurrentStateBDD = partialBDD[0];
		}

		return componentCurrentStateBDD;
	}

}
