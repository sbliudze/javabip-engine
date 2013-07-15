package org.bip.engine;

import java.util.Hashtable;
import java.util.ArrayList;
import java.util.Map;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.api.Behaviour;
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
	private volatile Hashtable<BIPComponent, BDD[]> stateBDDs = new Hashtable<BIPComponent, BDD[]>();
	private volatile Hashtable<BIPComponent, BDD[]> portBDDs = new Hashtable<BIPComponent, BDD[]>();
	private int auxSum;
	private BDDBIPEngine engine;
	private BIPCoordinator wrapper;

	private synchronized void createPortAndStateBDDs(BIPComponent component, int sum, int noStates, int noPorts) {
		BDD[] singleNodeBDDsForStates = new BDD[noStates];
		for (int i = 0; i < noStates; i++) {
			/**
			 * create new variable in the BDD manager for the state of each
			 * component instance
			 */
			singleNodeBDDsForStates[i] = engine.getBDDManager().ithVar(i + sum);
		}
		stateBDDs.put(component, singleNodeBDDsForStates);

		BDD[] singleNodeBDDsForPorts = new BDD[noPorts];
		for (int j = 0; j < noPorts; j++) {
			/**
			 * create new variable in the BDD manager for the port of each
			 * component instance
			 */
			singleNodeBDDsForPorts[j] = engine.getBDDManager().ithVar(j + noStates + sum);
		}
		//TODO: pass the portBDDs, (stateBDDs ?) to the BDDEngine
		portBDDs.put(component, singleNodeBDDsForPorts);
		logger.debug("Component {} put to portBdds, size={}. ", component.getName(), portBDDs.size());
		logger.debug("portBDDs size: {} ", portBDDs.size());
	}

	/** All the components need to be registered before creating the nodes */
	public synchronized void createBDDNodes(BIPComponent component, int noComponentPorts, int noComponentStates) {

		int initialNoNodes = noComponentPorts + noComponentStates + auxSum;

		logger.debug("Initial no of Nodes {}", initialNoNodes);
		logger.debug("Number of nodes in the BDD manager {}", engine.getBDDManager().varNum());
		if (engine.getBDDManager().varNum() < initialNoNodes){
			engine.getBDDManager().setVarNum(initialNoNodes);
		}

		logger.debug("noComponentPorts {}, noComponentStates {}", noComponentPorts, noComponentStates);
		createPortAndStateBDDs(component, auxSum, noComponentStates, noComponentPorts);
		auxSum = auxSum + noComponentPorts + noComponentStates;
	}

	/** Computes the Behavior BDD of a component */
	public synchronized BDD behaviourBDD(int componentID) {
		BDD componentBehaviour = engine.getBDDManager().zero(); // for OR-ing
		
		Behaviour behaviour = wrapper.getBehaviourById(componentID);
		
		ArrayList<Port> componentPorts = (ArrayList<Port>) behaviour.getEnforceablePorts();
		ArrayList<String> componentStates = (ArrayList<String>) behaviour.getStates();

		int noStates = componentStates.size();
		int noPorts = componentPorts.size();

		BIPComponent component = wrapper.getBIPComponent(componentID);

		BDD[] portsBDDs = new BDD[noPorts];
		BDD[] statesBDDs = new BDD[noStates];

		for (int i = 0; i < noPorts; i++) {
			portsBDDs[i] = portBDDs.get(component)[i];
		}
		for (int j = 0; j < noStates; j++) {
			statesBDDs[j] = stateBDDs.get(component)[j];
		}

		Hashtable<String, ArrayList<Port>> statePorts = new Hashtable<String, ArrayList<Port>>();
		statePorts = (Hashtable<String, ArrayList<Port>>) behaviour.getStateToPorts();
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
			for (int i = 0; i < portsValue.size(); i++) {
				int j = 0;
				while (portsValue.get(i) != componentPorts.get(j)) {
					if (j == componentPorts.size() - 1) {
						try {
							throw new BIPEngineException("Port not found.");
						} catch (BIPEngineException e) {
							e.printStackTrace();
							logger.error(e.getMessage());	
						} 
					}
					j++;
				}
				if (portsValue.get(i) == componentPorts.get(j))
					availablePorts.add(j + 1);
			}
			int aux = 0;
			for (int i = 0; i < componentStates.size(); i++) {
				if (stateKey.equals(componentStates.get(i))) {
					// TODO: algorithmic complexity?
					aux = i + 1;
					break;
				}
			}

			for (int i = 0; i < portsValue.size(); i++) {
				BDD aux1 = engine.getBDDManager().one();
				for (int j = 1; j <= noStates; j++) {
					if (aux == j)
						c[aux + i - 1] = aux1.and(statesBDDs[j - 1]);
					else
						c[aux + i - 1] = aux1.and(statesBDDs[j - 1].not());
					if (j != noStates) {
						aux1.free();
						aux1 = c[aux + i - 1];
					}
				}
				aux1.free();

				BDD aux2 = c[aux + i - 1];
				for (int j = 1; j <= noPorts; j++) {
					if (availablePorts.get(i) == j)
						c[aux + i - 1] = aux2.and(portsBDDs[j - 1]);
					else
						c[aux + i - 1] = aux2.and(portsBDDs[j - 1].not());
					if (j != noPorts) {
						aux2.free();
						aux2 = c[aux + i - 1];
					}
				}
				aux2.free();
				componentBehaviour.orWith(c[aux + i - 1]);
			}

			if (portsValue.size() == 0) {

				BDD aux1 = engine.getBDDManager().one();
				for (int i = 1; i <= noStates; i++) {
					if (aux == i)
						c[aux - 1] = aux1.and(statesBDDs[i - 1]);
					else
						c[aux - 1] = aux1.and(statesBDDs[i - 1].not());
					if (i != noStates) {
						aux1.free();
						aux1 = c[aux - 1];
					}
				}
				aux1.free();

				BDD aux2 = c[aux - 1];
				for (int i = 1; i <= noPorts; i++) {
					c[aux - 1] = aux2.and(portsBDDs[i - 1].not());
					if (i != noPorts) {
						aux2.free();
						aux2 = c[aux - 1];
					}
				}
				aux2.free();
				componentBehaviour.orWith(c[aux - 1]);

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


	public void setEngine(BDDBIPEngine engine) { 
		this.engine = engine;
	}

	public void setBIPCoordinator(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}

	public synchronized Hashtable<BIPComponent, BDD[]> getStateBDDs() {
		return stateBDDs;
	}

	public synchronized Hashtable<BIPComponent, BDD[]> getPortBDDs() {
		return portBDDs;
	}


	
	

}
