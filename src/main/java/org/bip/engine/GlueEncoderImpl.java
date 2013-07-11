package org.bip.engine;

import java.util.ArrayList;
import java.util.Enumeration;

import java.util.Hashtable;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;
import org.bip.glue.Accepts;
import org.bip.glue.BIPGlue;
import org.bip.glue.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives information about the glue and computes the glue BDD.
 * @author mavridou
 */

/** Computes the BDD of the glue */
public class GlueEncoderImpl implements GlueEncoder {
	private Logger logger = LoggerFactory.getLogger(GlueEncoderImpl.class);

	// TODO: Dependencies to be simplified (see the BIPCoordinator implementation)
	private BehaviourEncoder behenc; 
	private BDDBIPEngine engine;
	private BIPCoordinator wrapper;
	private BIPGlue glueSpec;

	/**
	 * Function called by the BIPCoordinator when the Glue xml file is parsed
	 * and its contents are stored as BIPGlue object that is given to this function
	 * as a parameter and stored in a global field of the class.
	 * 
	 * If the glue field is null throw an exception. 
	 */
	public void specifyGlue(BIPGlue glue){

		if (glue == null) {
			try {
				logger.error("The glue parser has failed to compute the glue object.\n" +
						"\tPossible reasons: Corrupt or non-existant glue XML file.");
				throw new BIPEngineException("Glue parser outputs null");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}
		}

		this.glueSpec = glue;
	}
	
	/**
	 * Finds all the component instances that correspond to the effect and causes component types 
	 * and computes the BDDs for all the different combinations of component instances by calling
	 * the component require function.
	 * 
	 * @param  require interaction constraints
	 * @return Arraylist of BDDs for all the different combinations of component instances
	 */
	ArrayList<BDD> decomposeRequireGlue(Requires requires) {
		ArrayList<BDD> result = new ArrayList<BDD>();
		Hashtable<Port, ArrayList<BIPComponent>> portToComponents = new Hashtable<Port, ArrayList<BIPComponent>>();
		// TODO: Not having a spec types is a serious problem, so the execution should probably stop in such cases, 
		// meaning that the exceptions should be re-thrown 

		// TODO: Define helper functions to do the computations below that are shared with decomposeAcceptGlue()
		
		/** Find all causes component instances */
		for (Port causePort : requires.causes) {
			// TODO: This should be as much of a problem as a missing spec for the effect (see below)
			// Hence, the treatment should be the same (an error rather than a warning)
			if (causePort.specType == null || causePort.specType.isEmpty()) {
					logger.warn("Spec type not specified or empty in a Require macro cause");
			} else {
				portToComponents.put(causePort, wrapper.getBIPComponentInstances(causePort.specType));
			}

			if (causePort.id == null || causePort.id.isEmpty()) {
				logger.warn("Port name not specified or empty in a Require macro cause");
			} else {
				portToComponents.put(causePort, wrapper.getBIPComponentInstances(causePort.specType));
			}
		}


		/** Find all effect component instances */
		// TODO: Check that the port name is neither null, nor empty (as above).
		String requireComponentType = requires.effect.specType;
		if (requireComponentType == null || requireComponentType.isEmpty()) {
			try {
				logger.error("Spec type not specified or empty in a Require macro effect");
				throw new BIPEngineException("Spec type not specified or empty in a Require macro effect");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}	
		}
		
		ArrayList<BIPComponent> requireEffectComponents = wrapper.getBIPComponentInstances(requireComponentType);
		if (requireEffectComponents.isEmpty()) {
			try {
				logger.error("Spec type in require effect for component {} was defined incorrectly. It does not match any registered component types", requireComponentType);
				throw new BIPEngineException("Spec type in require effect was defined incorrectly");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}	
		} else {
			for (BIPComponent effectInstance : requireEffectComponents) {
				logger.debug("Require Effect port type: {} ", requires.effect.id);
				logger.debug("PortToComponents size: {} ", portToComponents.size());
				result.add(componentRequire(effectInstance, requires.effect, portToComponents));
			}
		}
		
		return result;
	}
	
	/**
	 * Finds all the component instances that correspond to the effect and causes component types 
	 * and calls the component accept function to compute the BDDs for all the different combinations 
	 * of component instances.
	 * 
	 * @param  accept interaction constraints
	 * @return Arraylist of BDDs for all the different combinations of component instances
	 */
	ArrayList<BDD> decomposeAcceptGlue(Accepts accept) {
		ArrayList<BDD> result = new ArrayList<BDD>();
		Hashtable<Port, ArrayList<BIPComponent>> portToComponents = new Hashtable<Port, ArrayList<BIPComponent>>();
		// TODO: Not having a spec types is a serious problem, so the execution should probably stop in such cases, 
		// meaning that the exceptions should be re-thrown 

		/** Find all causes component instances */
		for (Port causePort : accept.causes) {
			if (causePort.specType == null || causePort.specType.isEmpty()) {
					logger.warn("Spec type not specified or empty in a Accept macro cause");
			}
			else{
			portToComponents.put(causePort, wrapper.getBIPComponentInstances(causePort.specType));
			}
		}

		/** Find all effect component instances */
		String acceptComponentType = accept.effect.specType;
		if (acceptComponentType == null || acceptComponentType.isEmpty()) {
			try {
				logger.error("Spec type not specified or empty in a Accept macro effect");
				throw new BIPEngineException("Spec type not specified or empty in a Accept macro effect");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}	
		}
		
		ArrayList<BIPComponent> acceptEffectComponents = wrapper.getBIPComponentInstances(acceptComponentType);
		if (acceptEffectComponents.isEmpty()) {
			try {
				logger.error("Spec type in Accept effect for component {} was defined incorrectly. It does not match any registered component types", acceptComponentType);
				throw new BIPEngineException("Spec type in Accept effect was defined incorrectly");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}	
		} else {
			for (BIPComponent effectInstance : acceptEffectComponents) {
				logger.debug("Accept Effect port type: {} ", accept.effect.id);
				logger.debug("PortToComponents size: {} ", portToComponents.size());
				result.add(componentAccept(effectInstance, accept.effect, accept.causes, portToComponents));
			}
		}
		
		return result;
	}

	/**
	 * Computes the BDD that corresponds to a Require macro.
	 * 
	 * @param BDD of the port of the component holder of the Require macro
	 * @param Arraylist of ports of the "causes" part of the Require macro
	 * @param Hashtable of ports of the "causes" part of the Require macro 
	 * and the corresponding port BDDs of the component instances
	 * 
	 *  @return the BDD that corresponds to a Require macro.
	 */
	// TODO: Think of the cardinality issue (move to Accept)
	BDD requireBDD(BDD requirePortHolder, Hashtable<Port, ArrayList<BDD>> requiredPorts) {
		BDD allCausesBDD = engine.getBDDManager().one();

		logger.info("requiredPorts size: "+requiredPorts.size());
		for (Enumeration<Port> portEnum = requiredPorts.keys(); portEnum.hasMoreElements();) {
			Port port = portEnum.nextElement();
			logger.info("Port: "+ port.id);
			ArrayList<BDD> auxPortBDDs = requiredPorts.get(port);
			logger.info("auxPortBDDs size: " + auxPortBDDs.size());
			int size = auxPortBDDs.size();
			BDD oneCauseBDD = engine.getBDDManager().zero();
			
			logger.info("auxPortBDDs get 0 " + auxPortBDDs.get(0));
			for (int i = 0; i < size; i++) {
				BDD monomial = engine.getBDDManager().one();				
				for (int j = 0; j < size; j++) {
					if (i == j) {
						/*
						 * Cannot use andWith here. Do not want to free the 
						 * BDDs assigned to the ports at the Behaviour Encoder.
						 */
						BDD tmp = monomial.and(auxPortBDDs.get(j));
						monomial.free();
						monomial = tmp;
					} else {
						/*
						 * Cannot use andWith here. Do not want to free the 
						 * BDDs assigned to the ports at the Behaviour Encoder.
						 */
						BDD tmp = monomial.and(auxPortBDDs.get(j).not());
						monomial.free();
						monomial = tmp;
					}
				}
				oneCauseBDD.orWith(monomial);
			}
			allCausesBDD.andWith(oneCauseBDD);
			auxPortBDDs.clear();

		}
		allCausesBDD.orWith(requirePortHolder.not());
		return allCausesBDD;			
	}

	/**
	 * Computes the BDD that corresponds to an Accept macro.
	 * 
	 * @param BDD of the port of the component holder of the Accept macro
	 * @param Arraylist of ports of the "causes" part of the Accept macro
	 * @param Hashtable of ports of the "causes" part of the Accept macro 
	 * and the corresponding port BDDs of the component instances
	 * 
	 *  @return the BDD that corresponds to an Accept macro.
	 */
	BDD acceptBDD(BDD acceptPortHolder, ArrayList<Port> auxPort, Hashtable<Port, ArrayList<BDD>> acceptPorts) {
		BDD tmp;
		BDD accept_bdd = engine.getBDDManager().one();
		int portBDDsize = behenc.getPortBDDs().size();
		ArrayList<BDD> totalPortBDDs= new ArrayList<BDD>();

		for (int i = 0; i < portBDDsize; i++) {
			BDD [] portBDD=behenc.getPortBDDs().get(i);
			for (int p=0; p<portBDD.length;p++){
				totalPortBDDs.add(portBDD[p]);
			}
		}

		for(int i=0; i<totalPortBDDs.size();i++){
			boolean exist = false;
			for (int j = 0; j < acceptPorts.size(); j++) {
				ArrayList<BDD> acceptBDDs=acceptPorts.get(auxPort.get(j));
				for(int k=0; k< acceptBDDs.size(); k++){				
					if (acceptBDDs.get(k).equals(totalPortBDDs.get(i))) {
						exist = true;
						break;
					}
				}
				if((totalPortBDDs.get(i)).equals(acceptPortHolder)){
					exist=true;
				}
				if (!exist) {
					tmp = totalPortBDDs.get(i).not().and(accept_bdd);
					accept_bdd.free();
					accept_bdd = tmp;
				}
			}
		}
		BDD acc_bdd = acceptPortHolder.not().or(accept_bdd);
		accept_bdd.free();
		return acc_bdd;
	}

	/**
	 * Finds the BDDs of the ports of the components that are needed for computing one require macro and 
	 * computes the BDD for this macro by calling the requireBDD method.
	 * 
	 * @param the component that holds the interaction require constraint
	 * @param the port of the holder component
	 * @param the set of ports that are required by the macro
	 * @param the list of components that correspond to the previous set of ports
	 * @return the BDD that corresponds to a Require macro
	 */
	BDD componentRequire(BIPComponent holderComponent, Port holderPort, Hashtable<Port, ArrayList<BIPComponent>> causesPortToComponents) {
		assert(holderComponent != null & holderPort != null && causesPortToComponents != null);
		
		BDD effectPortBDD;
		Hashtable<Port, ArrayList<BDD>> requiredBDDs = new Hashtable<Port, ArrayList<BDD>>();
		ArrayList<BDD> portBDDs = new ArrayList<BDD>();
		
		/*
		 * Obtain the BDD for the port variable corresponding to the effect instance of the macro
		 */
		// TODO: Once the hash table is changed in the behaviour encoder, remove componentId here (will not be needed)
		Integer componentId = wrapper.getBIPComponentIdentity(holderComponent);
		// TODO: Is it possible to have a hash table mapping port names to Port ids or BDDs without paying too much for it?
		ArrayList<Port> componentPorts = (ArrayList<Port>) wrapper.getBehaviourByComponent(holderComponent).getEnforceablePorts();
		logger.info("holder component type: {}", holderComponent.getName());
		logger.info("holder port type: {}", holderPort.id);
		logger.info("holder componentPorts size: {}", componentPorts.size());
	
		int portId = 0;
		while (portId < componentPorts.size() && !componentPorts.get(portId).id.equals(holderPort.id)) {
			portId++;
		}
		logger.debug("PortId: {} ", portId);
		effectPortBDD = behenc.getPortBDDs().get(componentId)[portId];

		/*
		 * For each cause port, we obtain all the component instances that provide this port and store the BDDs corresponding
		 * to the associated port variables in a common list, which is then passed on to the BDD computing function (requireBDD).  
		 */
		
		for (Enumeration<Port> portEnum = causesPortToComponents.keys(); portEnum.hasMoreElements();) {
			Port requiredPort = portEnum.nextElement();
			
			ArrayList<BIPComponent> requiredComponents = causesPortToComponents.get(requiredPort);
			for (BIPComponent requiredComponent : requiredComponents) {
				// TODO: As above for the behaviour encoder hash table

				componentId = wrapper.getBIPComponentIdentity(requiredComponent);
				ArrayList<Port> compPorts = (ArrayList<Port>) wrapper.getBehaviourById(componentId).getEnforceablePorts();
				logger.debug("causes component type: {}", requiredComponent.getName());
				logger.debug("causes componentPorts size: {}", compPorts.size());
				portId = 0;
				// TODO: As above for the port names to Ports ids hash table
				while (portId < compPorts.size() && !compPorts.get(portId).id.equals(requiredPort.id)) {
					portId++;
				}
				portBDDs.add(behenc.getPortBDDs().get(componentId)[portId]);
				logger.info("port BDDs size: {}", portBDDs.size());
				
				
			}
			requiredBDDs.put(requiredPort, portBDDs);
			logger.info("Port at causes of a require: {}", requiredPort.id);
			logger.info("port BDDs size: {}", portBDDs.size());
			//portBDDs.clear();
		}
		logger.info("requiredBDDs size: "+ requiredBDDs.size());
		//logger.info("port BDDs size: {}", portBDDs.size());

		return requireBDD(effectPortBDD, requiredBDDs);
	}

	/**
	 * Finds the BDDs of the ports of the components that are needed for computing one accept macro and 
	 * computes the BDD for this macro by calling the acceptBDD method.
	 * 
	 * @param the component that holds the interaction accept constraint
	 * @param the port of the holder component
	 * @param the set of ports that are "accepted" by the macro
	 * @param the list of components that correspond to the previous set of ports
	 * @return the BDD that corresponds to an Accept macro
	 */
	BDD componentAccept(BIPComponent HolderComponent, Port HolderPort, ArrayList<Port> acceptedPorts, Hashtable<Port, ArrayList<BIPComponent>> EffectPorttoComponents) {

		BDD portBDD;
		Hashtable<Port, ArrayList<BDD>> acceptedBDDs = new Hashtable<Port, ArrayList<BDD>>();
		ArrayList<BDD> portBDDs = new ArrayList<BDD>();
		Integer componentID = wrapper.getBIPComponentIdentity(HolderComponent);
		ArrayList<Port> componentPorts = (ArrayList<Port>) wrapper.getBehaviourById(componentID).getEnforceablePorts();
		int portID = 0;
		for (int i = 0; i <= componentPorts.size(); i++) {
			if (componentPorts.get(i).id.equals(HolderPort.id)) {
				portID = i;
				break;
			}
		}
		portBDD = behenc.getPortBDDs().get(componentID)[portID];

		ArrayList<BIPComponent> acceptedComponents = new ArrayList<BIPComponent>();
		ArrayList<Port> AuxPorts = new ArrayList<Port>();
		int size = EffectPorttoComponents.size();
		for (int i = 0; i < size; i++) {
			Port port = acceptedPorts.get(i);
			acceptedComponents.addAll(EffectPorttoComponents.get(port));
			for (int j = 0; j < acceptedComponents.size(); j++) {
				Integer ComID = wrapper.getBIPComponentIdentity(acceptedComponents.get(j));
				ArrayList<Port> compPorts = (ArrayList<Port>) wrapper.getBehaviourById(ComID).getEnforceablePorts();
				int PID = 0;
				for (int k = 0; k < compPorts.size(); k++) {
					if (compPorts.get(k).id.equals(acceptedPorts.get(i).id)) {
						PID = k;
						break;
					}
				}
				portBDDs.add(behenc.getPortBDDs().get(ComID)[PID]);
			}
			acceptedBDDs.put(acceptedPorts.get(i), portBDDs);
			AuxPorts.add(acceptedPorts.get(i));
		}

		BDD accept = acceptBDD(portBDD, AuxPorts, acceptedBDDs);
		return accept;
	}
	
	/**
	 * This function computes the total Glue BDD that is the conjunction of the require and accept constraints.
	 * For each require/accept macro we find the different combinations of the component instances that correspond
	 * to each constraint and take the conjunction of all these.
	 */
	public BDD totalGlue() {
		
		BDD result = engine.getBDDManager().one();

		logger.debug("Glue spec require Constraints size: {} ", glueSpec.requiresConstraints.size());
		if (!glueSpec.requiresConstraints.isEmpty()) {
			for (Requires requires : glueSpec.requiresConstraints) {
				ArrayList<BDD> RequireBDDs = decomposeRequireGlue(requires);
				for (BDD effectInstance : RequireBDDs) {
					result.andWith(effectInstance);
				}
			}
		} else {
			logger.warn("No require constraints provided (usually there should be some).");
		}

		return result;
	}

	public void setBehaviourEncoder(BehaviourEncoder behaviourEncoder) {
		this.behenc = behaviourEncoder;
	}

	public void setEngine(BDDBIPEngine engine) {
		this.engine = engine;
	}

	public void setBIPCoordinator(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}


}

