package org.bip.engine;

import java.util.Hashtable;
import java.util.ArrayList;

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
	//TODO: pass the portBDDs, (stateBDDs ?) to the BDDEngine
	private volatile Hashtable<BIPComponent, BDD[]> stateBDDs = new Hashtable<BIPComponent, BDD[]>();
	private volatile Hashtable<BIPComponent, BDD[]> portBDDs = new Hashtable<BIPComponent, BDD[]>();
	//TODO: move the portToBDDs to the BDDEngine (?)
	private Hashtable <BIPComponent, Hashtable<String, BDD>> componentToPortToBDD = new Hashtable <BIPComponent, Hashtable<String,BDD>>();
	private Hashtable <BIPComponent, Hashtable<String, BDD>> componentToStateToBDD = new Hashtable <BIPComponent, Hashtable<String,BDD>>();
	private int auxSum;
	private BDDBIPEngine engine;
	private BIPCoordinator wrapper;
	
	/**
	 * Creates one-node BDDs for the states and the ports of all components.
	 * 
	 * @param The component that we want to create the BDDs nodes for
	 * @param The ports of the component 
	 * @param The states of the component
	 * 
	 * @throws BIPEngineExc	eption
	 */
	public synchronized void createBDDNodes(BIPComponent component, ArrayList<Port> componentPorts, ArrayList<String> componentStates) throws BIPEngineException {

		int nbComponentPorts = componentPorts.size();
		int nbComponentStates = componentStates.size();
		int initialNoNodes = nbComponentPorts + nbComponentStates + auxSum;
		
		if (engine.getBDDManager().varNum() < initialNoNodes){
			engine.getBDDManager().setVarNum(initialNoNodes);
		}
		
		BDD[] singleNodeBDDsForStates = new BDD[nbComponentStates];
		Hashtable <String, BDD> stateToBDD = new Hashtable<String, BDD>();
		for (int i = 0; i < nbComponentStates; i++) {
			/*Create new variable in the BDD manager for the state of each component instance.*/
			singleNodeBDDsForStates[i] = engine.getBDDManager().ithVar(i + auxSum);
			if (singleNodeBDDsForStates[i] == null){
				try {
					logger.error("Single node BDD for state {} is equal to null", componentStates.get(i));
					throw new BIPEngineException("Single node BDD for state is equal to null");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;
				}
			}
			stateToBDD.put(componentStates.get(i), singleNodeBDDsForStates[i]);
			if (stateToBDD.get(componentStates.get(i)) == null){
				try {
					logger.error("BDD node that corresponds to the state {} of component {} is not created.", componentStates.get(i), component.getName());
					throw new BIPEngineException("BDD node that corresponds to a state is not created.");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;
				}
			}	
		}
		componentToStateToBDD.put(component, stateToBDD);
		stateBDDs.put(component, singleNodeBDDsForStates);

		BDD[] singleNodeBDDsForPorts = new BDD[nbComponentPorts];
		Hashtable <String, BDD> portToBDD = new Hashtable<String, BDD>();
		for (int i = 0; i < nbComponentPorts; i++) {
			
			/*Create new variable in the BDD manager for the port of each component instance.*/
			singleNodeBDDsForPorts[i] = engine.getBDDManager().ithVar(i + nbComponentStates + auxSum);
			if (singleNodeBDDsForPorts[i] == null){
				try {
					logger.error("Single node BDD for port {} is equal to null", componentPorts.get(i));
					throw new BIPEngineException("Single node BDD for port is equal to null");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;
				}
			}	
			portToBDD.put(componentPorts.get(i).id, singleNodeBDDsForPorts[i]);
			if (portToBDD.get(componentPorts.get(i).id) == null){
				try {
					logger.error("BDD node that corresponds to the port {} of component {} is not created.", componentPorts.get(i).id, component.getName());
					throw new BIPEngineException("BDD node that corresponds to a port is not created.");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;
				}
			}	
		}
		componentToPortToBDD.put(component, portToBDD);
		portBDDs.put(component, singleNodeBDDsForPorts);
		auxSum = auxSum + nbComponentPorts + nbComponentStates;
	}

	/** 
	 * Computes the Behavior BDD of a component 
	 * @throws BIPEngineException 
	 */
	public synchronized BDD behaviourBDD(BIPComponent component) throws BIPEngineException {

		BDD componentBehaviourBDD = engine.getBDDManager().zero();
		Behaviour behaviour = wrapper.getBehaviourByComponent(component);
		if (behaviour == null){
			try {
				logger.error("Behaviour of component {} is null", component.getName());
				throw new BIPEngineException("Component behaviour is null");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}	
		ArrayList<Port> componentPorts = (ArrayList<Port>) behaviour.getEnforceablePorts();
		ArrayList<String> componentStates = (ArrayList<String>) behaviour.getStates();
//		Hashtable<String, ArrayList<Port>> stateToPorts = (Hashtable<String, ArrayList<Port>>) behaviour.getStateToPorts();
		Hashtable<String, BDD> portToBDD = componentToPortToBDD.get(component); 
		Hashtable<String, BDD> stateToBDD = componentToStateToBDD.get(component); 
//		int nbStates = componentStates.size();
//		int nbPorts = componentPorts.size();
		
		BDD tmp;
		for (String componentState: componentStates){
			logger.debug("Component State: "+componentState);
				BDD oneStateToPortsBDD = engine.getBDDManager().one(); 
				tmp = oneStateToPortsBDD.and(stateToBDD.get(componentState));
				oneStateToPortsBDD.free();
				oneStateToPortsBDD = tmp;
				for (String otherState : componentStates){
					if (!componentState.equals(otherState)){
						logger.debug("Negated State: "+otherState);
						tmp =oneStateToPortsBDD.and(stateToBDD.get(otherState).not());
						oneStateToPortsBDD.free();
						oneStateToPortsBDD=tmp;
					}
				}
				ArrayList<Port> statePorts= behaviour.getStateToPorts().get(componentState);
				if (!statePorts.isEmpty()){
					for (Port port: statePorts){
						logger.debug("Component state port: "+port);
						tmp = oneStateToPortsBDD.and(portToBDD.get(port.id));
						oneStateToPortsBDD.free();
						oneStateToPortsBDD = tmp;
						for (Port otherPort: componentPorts){
							if (!port.equals(otherPort)){
								logger.debug("Negated ports: "+otherPort);
								tmp = oneStateToPortsBDD.and(portToBDD.get(otherPort.id).not());
								oneStateToPortsBDD.free();
								oneStateToPortsBDD=tmp;
							}		
						}
						tmp=componentBehaviourBDD.or(oneStateToPortsBDD);
						componentBehaviourBDD.free();
						componentBehaviourBDD=tmp;
					}
				}
				else{	
					for (Port otherPort: componentPorts){
						logger.debug("All negated ports: "+otherPort);
						tmp = oneStateToPortsBDD.andWith(portToBDD.get(otherPort.id).not());
					}
					componentBehaviourBDD.orWith(oneStateToPortsBDD);
				}		
		}
		
		BDD allNegatedPortsBDD = engine.getBDDManager().one();
		for(Port port: componentPorts){
			tmp = allNegatedPortsBDD.and(portToBDD.get(port.id).not());
			allNegatedPortsBDD.free();
			allNegatedPortsBDD=tmp;
		}	
//		int nbStatePortsBDDs = 0;
//		for (Map.Entry<String, ArrayList<Port>> entry : stateToPorts.entrySet()) {
//			nbStatePortsBDDs = nbStatePortsBDDs + entry.getValue().size();
//			if (entry.getValue().size() == 0) {
//				nbStatePortsBDDs++;
//			}
//		}
//		
//		/* Plus one for the case that no transition happens. */
//		BDD[] c = new BDD[nbStatePortsBDDs + 1];
//		ArrayList<Port> portsValue = new ArrayList<Port>();
//		String stateKey;
//
//		ArrayList<Integer> availablePorts = new ArrayList<Integer>();
//
//		for (Map.Entry<String, ArrayList<Port>> entry : stateToPorts.entrySet()) {
//			portsValue = entry.getValue();
//			stateKey = entry.getKey();
////			for (Port port: entry.getValue()) {
//			for (int i = 0; i < portsValue.size(); i++) {
//				int j = 0;
//				while (portsValue.get(i) != componentPorts.get(j)) {
//					if (j == componentPorts.size() - 1) {
//						try {
//							throw new BIPEngineException("Port not found.");
//						} catch (BIPEngineException e) {
//							e.printStackTrace();
//							logger.error(e.getMessage());	
//						} 
//					}
//					j++;
//				}
//				if (portsValue.get(i) == componentPorts.get(j))
//					availablePorts.add(j);
//			}
//			int aux = 0;
//			for (int i = 0; i < componentStates.size(); i++) {
//				if (stateKey.equals(componentStates.get(i))) {
//					aux = i;
//					break;
//				}
//			}
//
//			for (int i = 0; i < portsValue.size(); i++) {
//				BDD aux1 = engine.getBDDManager().one();
//				for (int j = 0; j < nbStates; j++) {
//					if (aux == j)
//						c[aux + i] = aux1.and(stateBDDs.get(component)[j]);
//					else
//						c[aux + i] = aux1.and(stateBDDs.get(component)[j].not());
//					if (j != nbStates-1) {
//						aux1.free();
//						aux1 = c[aux + i];
//					}
//				}
//				aux1.free();
//
//				BDD aux2 = c[aux + i];
//				for (int j =0 ; j < nbPorts; j++) {
//					if (availablePorts.get(i) == j)
//						c[aux + i] = aux2.and(portBDDs.get(component)[j]);
//					else
//						c[aux + i] = aux2.and(portBDDs.get(component)[j].not());
//					if (j != nbPorts-1) {
//						aux2.free();
//						aux2 = c[aux + i];
//					}
//				}
//				aux2.free();
//				componentBehaviourBDD.orWith(c[aux + i ]);
//			}
//
//			if (portsValue.size() == 0) {
//
//				BDD aux1 = engine.getBDDManager().one();
//				for (int i = 0; i < nbStates; i++) {
//					if (aux == i)
//						c[aux] = aux1.and(stateBDDs.get(component)[i]);
//					else
//						c[aux] = aux1.and(stateBDDs.get(component)[i].not());
//					if (i != nbStates-1) {
//						aux1.free();
//						aux1 = c[aux];
//					}
//				}
//				aux1.free();
//
//				BDD aux2 = c[aux];
//				for (int i = 0; i < nbPorts; i++) {
//					c[aux] = aux2.and(portBDDs.get(component)[i].not());
//					if (i != nbPorts-1) {
//						aux2.free();
//						aux2 = c[aux];
//					}
//				}
//				aux2.free();
//				componentBehaviourBDD.orWith(c[aux]);
//
//			}
//			availablePorts.clear();
//		}
//
//		BDD aux3 = engine.getBDDManager().one();
//		for (int j = 0; j < nbPorts; j++) {
//			c[c.length - 1] = aux3.and(portBDDs.get(component)[j].not());
//			if (j != nbPorts-1) {
//				aux3.free();
//				aux3 = c[c.length - 1];
//			}
//		}
//		aux3.free();
//		return componentBehaviourBDD.orWith(c[c.length - 1]);
		return componentBehaviourBDD.orWith(allNegatedPortsBDD);

	}


	public void setEngine(BDDBIPEngine engine) { 
		this.engine = engine;
	}

	public void setBIPCoordinator(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}

//	public synchronized Hashtable<BIPComponent, BDD[]> getStateBDDs() {
//		return stateBDDs;
//	}

	public synchronized Hashtable<BIPComponent, BDD[]> getPortBDDs() {
		return portBDDs;
	}
	
	public synchronized BDD getBDDOfAPort(BIPComponent component, String portName) throws BIPEngineException {
		Hashtable<String, BDD> aux = componentToPortToBDD.get(component);
		if (aux.get(portName) == null){
			try {
				logger.error("BDD node of port {} is null", portName);
				throw new BIPEngineException("BDD node of a port is null");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		return aux.get(portName);
	}
	
//	public synchronized BDD getBDDOfAState(BIPComponent component, String stateName) throws BIPEngineException {
//		Hashtable<String, BDD> aux = componentToStateToBDD.get(component);
//		if (aux.get(stateName) == null){
//			try {
//				logger.error("BDD node of state {} is null", stateName);
//				throw new BIPEngineException("BDD node of a state is null");
//			} catch (BIPEngineException e) {
//				e.printStackTrace();
//				throw e;
//			}
//		}
//		return aux.get(stateName);
//	}
	
	public synchronized Hashtable<String, BDD> getStateToBDDOfAComponent (BIPComponent component){
		return componentToStateToBDD.get(component);
	}

	public synchronized Hashtable<String, BDD> getPortToBDDOfAComponent (BIPComponent component){
		return componentToPortToBDD.get(component);
	}

	
	

}
