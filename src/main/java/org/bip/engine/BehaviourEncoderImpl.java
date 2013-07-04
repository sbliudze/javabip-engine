package org.bip.engine;

import java.util.Hashtable;
import java.util.ArrayList;
import java.util.Map;

import net.sf.javabdd.BDD;

import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives information about the behaviour of each registered component and computes the total behaviour BDD.
 * @author mavridou
 */

/** Computes the BDD of the behaviour of all components */
public class BehaviourEncoderImpl implements BehaviourEncoder {

	private Logger logger = LoggerFactory.getLogger(BehaviourEncoderImpl.class);
	private volatile Hashtable<Integer, BDD[]> stateBDDs = new Hashtable<Integer, BDD[]>();
	private volatile Hashtable<Integer, BDD[]> portBDDs = new Hashtable<Integer, BDD[]>();
	private int auxSum;
	private BDDBIPEngine engine;
	private BIPCoordinator wrapper;

	private synchronized void createPortAndStateBDDs(int componentID, int sum, int noStates, int noPorts) {
		BDD[] singleNodeBDDsForStates = new BDD[noStates];
		for (int i = 0; i < noStates; i++) {
			/**
			 * create new variable in the BDD manager for the state of each
			 * component instance
			 */
			singleNodeBDDsForStates[i] = engine.getBDDManager().ithVar(i + sum);
		}
		stateBDDs.put(componentID, singleNodeBDDsForStates);

		BDD[] singleNodeBDDsForPorts = new BDD[noPorts];
		for (int j = 0; j < noPorts; j++) {
			/**
			 * create new variable in the BDD manager for the port of each
			 * component instance
			 */
			singleNodeBDDsForPorts[j] = engine.getBDDManager().ithVar(j + noStates + sum);
		}
		//TODO: pass the portBDDs, (stateBDDs ?) to the BDDEngine
		portBDDs.put(componentID, singleNodeBDDsForPorts);
		logger.debug("Component {} put to portBdds, size={}. ", componentID, portBDDs.size());
		logger.debug("portBDDs size: {} ", portBDDs.size());
	}

	/** All the components need to be registered before creating the nodes */
	public synchronized void createBDDNodes(int componentID, int noComponentPorts, int noComponentStates) {

		int initialNoNodes = noComponentPorts + noComponentStates + auxSum;

		logger.debug("Initial no of Nodes {}", initialNoNodes);
		logger.debug("Number of nodes in the BDD manager {}", engine.getBDDManager().varNum());
		if (engine.getBDDManager().varNum() < initialNoNodes){
			engine.getBDDManager().setVarNum(initialNoNodes);
		}

		logger.debug("componentID {}, auxSum {}", componentID, auxSum);
		logger.debug("noComponentPorts {}, noComponentStates {}", noComponentPorts, noComponentStates);
		createPortAndStateBDDs(componentID, auxSum, noComponentStates, noComponentPorts);
		auxSum = auxSum + noComponentPorts + noComponentStates;

	}

	/** Computes the Behavior BDD of a component */
	private synchronized BDD behaviourBDD(int componentID) {
		BDD componentBehaviour = engine.getBDDManager().zero(); // for OR-ing
		ArrayList<Port> componentPorts = (ArrayList<Port>) wrapper.getBIPComponentBehaviour(componentID).getEnforceablePorts();
		ArrayList<String> componentStates = (ArrayList<String>) wrapper.getBIPComponentBehaviour(componentID).getStates();
		int noStates = componentStates.size();
		int noPorts = ((ArrayList<Port>)wrapper.getBIPComponentBehaviour(componentID).getEnforceablePorts()).size();

		BDD[] portsBDDs = new BDD[noPorts];
		BDD[] statesBDDs = new BDD[noStates];

		for (int i = 0; i < noPorts; i++) {
			portsBDDs[i] = portBDDs.get(componentID)[i];
		}
		for (int j = 0; j < noStates; j++) {
			statesBDDs[j] = stateBDDs.get(componentID)[j];
		}

		Hashtable<String, ArrayList<Port>> statePorts = new Hashtable<String, ArrayList<Port>>();
		statePorts = (Hashtable<String, ArrayList<Port>>) wrapper.getBIPComponentBehaviour(componentID).getStateToPorts();
		int c_size = 0;
		for (Map.Entry<String, ArrayList<Port>> entry : statePorts.entrySet()) {
			c_size = c_size + entry.getValue().size();
			if (entry.getValue().size() == 0) {
				c_size++;
			}
		}
		BDD[] c = new BDD[c_size + 1];
		ArrayList<Port> portsValue = new ArrayList<Port>();
		String stateKey;

		ArrayList<Integer> availablePorts = new ArrayList<Integer>();

		for (Map.Entry<String, ArrayList<Port>> entry : statePorts.entrySet()) {
			portsValue = entry.getValue();
			stateKey = entry.getKey();
			for (int l = 0; l < portsValue.size(); l++) {
				int k = 0;
				while (portsValue.get(l) != componentPorts.get(k)) {
					if (k == componentPorts.size() - 1) {
						try {
							throw new BIPEngineException("Port not found.");
						} catch (BIPEngineException e) {
							e.printStackTrace();
							logger.error(e.getMessage());	
						} 
					}
					k++;
				}
				if (portsValue.get(l) == componentPorts.get(k))
					availablePorts.add(k + 1);
			}
			int i = 0;
			for (int m = 0; m < componentStates.size(); m++) {
				if (stateKey.equals(componentStates.get(m))) {
					// TODO: algorithmic complexity?
					i = m + 1;
					break;
				}
			}

			for (int l = 0; l < portsValue.size(); l++) {
				BDD aux1 = engine.getBDDManager().one();
				for (int j = 1; j <= noStates; j++) {
					if (i == j)
						c[i + l - 1] = aux1.and(statesBDDs[j - 1]);
					else
						c[i + l - 1] = aux1.and(statesBDDs[j - 1].not());
					if (j != noStates) {
						aux1.free();
						aux1 = c[i + l - 1];
					}
				}
				aux1.free();

				BDD aux2 = c[i + l - 1];
				for (int j = 1; j <= noPorts; j++) {
					if (availablePorts.get(l) == j)
						c[i + l - 1] = aux2.and(portsBDDs[j - 1]);
					else
						c[i + l - 1] = aux2.and(portsBDDs[j - 1].not());
					if (j != noPorts) {
						aux2.free();
						aux2 = c[i + l - 1];
					}
				}
				aux2.free();
				componentBehaviour.orWith(c[i + l - 1]);
			}

			if (portsValue.size() == 0) {

				BDD aux1 = engine.getBDDManager().one();
				for (int j = 1; j <= noStates; j++) {
					if (i == j)
						c[i - 1] = aux1.and(statesBDDs[j - 1]);
					else
						c[i - 1] = aux1.and(statesBDDs[j - 1].not());
					if (j != noStates) {
						aux1.free();
						aux1 = c[i - 1];
					}
				}
				aux1.free();

				BDD aux2 = c[i - 1];
				for (int j = 1; j <= noPorts; j++) {
					c[i - 1] = aux2.and(portsBDDs[j - 1].not());
					if (j != noPorts) {
						aux2.free();
						aux2 = c[i - 1];
					}
				}
				aux2.free();
				componentBehaviour.orWith(c[i - 1]);

			}

			availablePorts.clear();

		}

		BDD aux3 = engine.getBDDManager().one();
		for (int j = 1; j <= noPorts; j++) {
			c[c.length - 1] = aux3.and(portsBDDs[j - 1].not());
			if (j != noPorts) {
				aux3.free();
				aux3 = c[c.length - 1];
			}
		}
		aux3.free();

		componentBehaviour.orWith(c[c.length - 1]);

		return componentBehaviour;

	}

	public BDD totalBehaviour() {
		/** conjunction of all FSM BDDs (Λi Fi) */
		BDD totalBehaviour = engine.getBDDManager().one();
		for (int k = 0; k < wrapper.getNoComponents(); k++) {
			if (behaviourBDD(k) == null) {
		        try {
					throw new BIPEngineException("Behaviour of a component is null");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					logger.error("Component did not register correctly");
				}
		      }
			totalBehaviour.andWith(behaviourBDD(k));
		}
		if (totalBehaviour == null) {
	        try {
				throw new BIPEngineException("Total behaviour is null");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				logger.error("Total behaviour was not computed correctly or no components were registered.");
			}
	      }
		return totalBehaviour;
	}
	
	public void setEngine(BDDBIPEngine engine) { 
		this.engine = engine;
	}

	public void setBIPCoordinator(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}

	public synchronized Hashtable<Integer, BDD[]> getStateBDDs() {
		return stateBDDs;
	}

	public synchronized Hashtable<Integer, BDD[]> getPortBDDs() {
		return portBDDs;
	}


	
	

}
