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
	 * @throws BIPEngineException 
	 */
	public void specifyGlue(BIPGlue glue) throws BIPEngineException{

		if (glue == null) {
			try {
				logger.error("The glue parser has failed to compute the glue object.\n" +
						"\tPossible reasons: Corrupt or non-existant glue XML file.");
				throw new BIPEngineException("Glue parser outputs null");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		this.glueSpec = glue;
	}
	
	/**
	 * Helper function that takes as an argument the list of ports at the causes part of a constraint macro
	 * and returns the component instances that correspond to each cause port.
	 * 
	 * @param ArrayList of causes ports
	 * @return Hashtable with the causes ports as keys and the set of component instances that correspond to them as values
	 * @throws BIPEngineException 
	 */
	Hashtable<Port, ArrayList<BIPComponent>> findCausesComponents (ArrayList<Port> causesPorts) throws BIPEngineException{
		
		Hashtable<Port, ArrayList<BIPComponent>> portToComponents = new Hashtable<Port, ArrayList<BIPComponent>>();

		for (Port causePort : causesPorts) {
			if (causePort.specType == null || causePort.specType.isEmpty()) {
					logger.warn("Spec type not specified or empty in a Require macro cause");
			} else {
				portToComponents.put(causePort, wrapper.getBIPComponentInstances(causePort.specType));
			}

			if (causePort.id == null || causePort.id.isEmpty()) {
				logger.warn("Port name not specified or empty in a Require macro cause");
			} else {
				portToComponents.put(causePort, wrapper.getBIPComponentInstances(causePort.specType));
				if (portToComponents.get(causePort).isEmpty()) {
					try {
						logger.error("Spec type in causes for component {} was defined incorrectly. It does not match any registered component types", causePort.specType);
						throw new BIPEngineException("Spec type in causes was defined incorrectly");
					} catch (BIPEngineException e) {
						e.printStackTrace();
						throw e;
					}
				}	
			}
		}
			return portToComponents;
	}
	
	/**
	 * Helper function that takes as an argument the port at the effect part of a constraint macro
	 * and returns the component instances that correspond to this port.
	 * 
	 * @param Effect port
	 * @return ArrayList with the set of component instances that correspond to the effect port
	 * @throws BIPEngineException 
	 */
	ArrayList<BIPComponent> findEffectComponents (Port effectPort) throws BIPEngineException{
		
		if (effectPort.id == null || effectPort.id.isEmpty()) {
			try {
				logger.error("Port name not specified or empty in a macro cause.");
				throw new BIPEngineException("Port name not specified or empty in a macro cause.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}	
		}
		
		if (effectPort.specType == null || effectPort.specType.isEmpty()) {
			try {
				logger.error("Spec type not specified or empty in a macro effect");
				throw new BIPEngineException("Spec type not specified or empty in a macro effect");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}	
		}
		
		ArrayList<BIPComponent> requireEffectComponents = wrapper.getBIPComponentInstances(effectPort.specType);
		if (requireEffectComponents.isEmpty()) {
			try {
				logger.error("Spec type in effect for component {} was defined incorrectly. It does not match any registered component types", effectPort.specType);
				throw new BIPEngineException("Spec type in effect was defined incorrectly");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}	
		return requireEffectComponents;
	}
	
	/**
	 * Finds all the component instances that correspond to the effect and causes component types 
	 * and computes the BDDs for all the different combinations of component instances by calling
	 * the component require function.
	 * 
	 * @param  require interaction constraints
	 * @return Arraylist of BDDs for all the different combinations of component instances
	 * @throws BIPEngineException 
	 */
	ArrayList<BDD> decomposeRequireGlue(Requires requires) throws BIPEngineException {
		ArrayList<BDD> result = new ArrayList<BDD>();
		
		if (requires.effect.equals(null)) {
			try {
				logger.error("Effect part of a Require constraint was not specified in the macro.");
				throw new BIPEngineException("Effect part of a Require constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		if (requires.effect.id.isEmpty()) {
			try {
				logger.error("The port at the effect part of a Require constraint was not specified.");
				throw new BIPEngineException("The port at the effect part of a Require constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		if (requires.effect.specType.isEmpty()) {
			try {
				logger.error("The component type of a port at the effect part of a Require constraint was not specified");
				throw new BIPEngineException("The component type of a port at the effect part of a Require constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		/* Find all effect component instances */
		ArrayList<BIPComponent> requireEffectComponents =findEffectComponents(requires.effect);
		
		//TODO: consistency with Accept
		//if (requires.causes.equals(null) || requires.causes.isEmpty()) {
		if (requires.causes.equals(null)) {
			try {
				logger.error("Causes part of a Require constraint was not specified in the macro.");
				throw new BIPEngineException("Causes part of a Require constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		/* Find all causes component instances */
		Hashtable<Port, ArrayList<BIPComponent>> portToComponents = findCausesComponents(requires.causes);
		for (BIPComponent effectInstance : requireEffectComponents) {
			logger.debug("Require Effect port type: {} ", requires.effect.id);
			logger.debug("PortToComponents size: {} ", portToComponents.size());
			result.add(componentRequire(effectInstance, requires.effect, portToComponents));
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
	 * @throws BIPEngineException 
	 */
	ArrayList<BDD> decomposeAcceptGlue(Accepts accept) throws BIPEngineException {
		ArrayList<BDD> result = new ArrayList<BDD>();

		if (accept.effect.equals(null)) {
			try {
				logger.error("Effect part of an Accept constraint was not specified in the macro.");
				throw new BIPEngineException("Effect part of an Accept constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		if (accept.effect.id.isEmpty()) {
			try {
				logger.error("The port at the effect part of an Accept constraint was not specified.");
				throw new BIPEngineException("The port at the effect part of an Accept constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		if (accept.effect.specType.isEmpty()) {
			try {
				logger.error("The component type of a port at the effect part of an Accept constraint was not specified");
				throw new BIPEngineException("The component type of a port at the effect part of an Accept constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		/* Find all effect component instances */
		ArrayList<BIPComponent> acceptEffectComponents = findEffectComponents(accept.effect);
		
		if (accept.causes.equals(null)) {
			try {
				logger.error("Causes part of an Accept constraint was not specified in the macro.");
				throw new BIPEngineException("Causes part of an Accept constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		/* Find all causes component instances */
		Hashtable<Port, ArrayList<BIPComponent>> portToComponents = findCausesComponents(accept.causes);

		for (BIPComponent effectInstance : acceptEffectComponents) {
			logger.debug("Accept Effect port type: {} ", accept.effect.id);
			logger.debug("PortToComponents size: {} ", portToComponents.size());
			result.add(componentAccept(effectInstance, accept.effect, portToComponents));
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

		logger.debug("requiredPorts size: "+requiredPorts.size());
			for (Enumeration<Port> portEnum = requiredPorts.keys(); portEnum.hasMoreElements();) {
				Port port = portEnum.nextElement();
				ArrayList<BDD> auxPortBDDs = requiredPorts.get(port);
				
				logger.debug("Required port: "+ port.id);
				logger.debug("Required port BDDs size: " + auxPortBDDs.size());
				
				int size = auxPortBDDs.size();
				BDD oneCauseBDD = engine.getBDDManager().zero();
				
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
	BDD acceptBDD(BDD acceptPortHolder, Hashtable<Port, ArrayList<BDD>> acceptedPorts) { 
		BDD tmp;
		BDD allCausesBDD = engine.getBDDManager().one();
//		int portBDDsize = behenc.getPortBDDs().size();
		ArrayList<BDD> totalPortBDDs= new ArrayList<BDD>();
		
		/*
		 * Get all port BDDs registered in the Behaviour Encoder and 
		 * add them in the totalPortBDDs ArrayList. 
		 */
		Hashtable<BIPComponent, BDD[]> portToBDDs = behenc.getPortBDDs();

		for (Enumeration<BIPComponent> portIdEnum = portToBDDs.keys(); portIdEnum.hasMoreElements(); ){
			BIPComponent portID = portIdEnum.nextElement();
			BDD [] portBDD = portToBDDs.get(portID);
			for (int p=0; p<portBDD.length;p++){
				totalPortBDDs.add(portBDD[p]);
			}
		}
//		for (int i = 0; i < portBDDsize; i++) {
//			BDD [] portBDD=behenc.getPortBDDs().get(i);
//			for (int p=0; p<portBDD.length;p++){
//				totalPortBDDs.add(portBDD[p]);
//			}
//		}
		
		for (BDD totalPortBDD : totalPortBDDs){
			boolean exist = false;
//			for(int i=0; i<totalPortBDDs.size();i++){
//			boolean exist = false;
			
			for (Enumeration<Port> portEnum = acceptedPorts.keys(); portEnum.hasMoreElements();){
				Port port = portEnum.nextElement();
				ArrayList<BDD> auxPortBDDs=acceptedPorts.get(port);
				int nbPortBDD=0;
				
//				for(int k=0; k< auxPortBDDs.size(); k++){				
//				if (auxPortBDDs.get(k).equals(totalPortBDD)) {
//					exist = true;
//					break;
//				}
				while (nbPortBDD < auxPortBDDs.size() && !auxPortBDDs.get(nbPortBDD).equals(totalPortBDD)){
					nbPortBDD++;
				}
				if (nbPortBDD < auxPortBDDs.size() && auxPortBDDs.get(nbPortBDD).equals(totalPortBDD)){
					exist =true;
				}
//			for (int j = 0; j < acceptPorts.size(); j++) {
//				ArrayList<BDD> acceptBDDs=acceptPorts.get(auxPort.get(j));
//				for(int k=0; k< acceptBDDs.size(); k++){				
//					if (acceptBDDs.get(k).equals(totalPortBDD)) {
//						exist = true;
//						break;
//					}
//				}
				
				if((totalPortBDD).equals(acceptPortHolder)){
					exist=true;
				}
				if (!exist) {
					tmp = totalPortBDD.not().and(allCausesBDD);
					allCausesBDD.free();
					allCausesBDD = tmp;
				}
			}
		}
//				if((totalPortBDDs.get(i)).equals(acceptPortHolder)){
//					exist=true;
//				}
//				if (!exist) {
//					tmp = totalPortBDDs.get(i).not().and(allCausesBDD);
//					allCausesBDD.free();
//					allCausesBDD = tmp;
//				}
//			}
//		}
		
		allCausesBDD.orWith(acceptPortHolder.not());
		return allCausesBDD;
//		BDD acc_bdd = acceptPortHolder.not().or(allCausesBDD);
//		allCausesBDD.free();
//		return acc_bdd;
	}

	/**
	 * Finds the BDDs of the ports of the components that are needed for computing one require macro and 
	 * computes the BDD for this macro by calling the requireBDD method.
	 * 
	 * @param the component that holds the interaction require constraint
	 * @param the port of the holder component
	 * @param the list of components that correspond to the previous set of ports
	 * 
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
		// TODO: Is it possible to have a hash table mapping port names to Port ids or BDDs without paying too much for it?
		ArrayList<Port> componentPorts = (ArrayList<Port>) wrapper.getBehaviourByComponent(holderComponent).getEnforceablePorts();
		logger.debug("holder component type: {}", holderComponent.getName());
		logger.debug("holder port type: {}", holderPort.id);
		logger.debug("holder componentPorts size: {}", componentPorts.size());
	
		int portId = 0;
		while (portId < componentPorts.size() && !componentPorts.get(portId).id.equals(holderPort.id)) {
			portId++;
		}
		effectPortBDD = behenc.getPortBDDs().get(holderComponent)[portId];

		/*
		 * For each cause port, we obtain all the component instances that provide this port and store the BDDs corresponding
		 * to the associated port variables in a common list, which is then passed on to the BDD computing function (requireBDD).  
		 */
		for (Enumeration<Port> portEnum = causesPortToComponents.keys(); portEnum.hasMoreElements();) {
			Port requiredPort = portEnum.nextElement();
			
			ArrayList<BIPComponent> requiredComponents = causesPortToComponents.get(requiredPort);
			for (BIPComponent requiredComponent : requiredComponents) {
				ArrayList<Port> compPorts = (ArrayList<Port>) wrapper.getBehaviourByComponent(requiredComponent).getEnforceablePorts();
				logger.debug("causes component type: {}", requiredComponent.getName());
				logger.debug("causes componentPorts size: {}", compPorts.size());
				portId = 0;
				// TODO: As above for the port names to Ports ids hash table
				while (portId < compPorts.size() && !compPorts.get(portId).id.equals(requiredPort.id)) {
					portId++;
				}
				portBDDs.add(behenc.getPortBDDs().get(requiredComponent)[portId]);
				logger.debug("port BDDs size: {}", portBDDs.size());
				
				
			}
			requiredBDDs.put(requiredPort, portBDDs);
			logger.debug("Port at causes of a require: {}", requiredPort.id);
			logger.debug("port BDDs size: {}", portBDDs.size());
			/*
			 * Should not clear portBDDs. 
			 */
		}
		logger.debug("requiredBDDs size: "+ requiredBDDs.size());

		return requireBDD(effectPortBDD, requiredBDDs);
	}

	/**
	 * Finds the BDDs of the ports of the components that are needed for computing one accept macro and 
	 * computes the BDD for this macro by calling the acceptBDD method.
	 * 
	 * @param the component that holds the interaction accept constraint
	 * @param the port of the holder component
	 * @param the list of components that correspond to the previous set of ports
	 * 
	 * @return the BDD that corresponds to an Accept macro
	 */
	BDD componentAccept(BIPComponent holderComponent, Port holderPort, Hashtable<Port, ArrayList<BIPComponent>> causesPortToComponents) {
		assert(holderComponent != null & holderPort != null && causesPortToComponents != null);	
		
		BDD effectPortBDD;
		Hashtable<Port, ArrayList<BDD>> acceptedBDDs = new Hashtable<Port, ArrayList<BDD>>();
		ArrayList<BDD> portBDDs = new ArrayList<BDD>();
		
		/*
		 * Obtain the BDD for the port variable corresponding to the effect instance of the macro
		 */		
		// TODO: Is it possible to have a hash table mapping port names to Port ids or BDDs without paying too much for it?
		ArrayList<Port> componentPorts = (ArrayList<Port>) wrapper.getBehaviourByComponent(holderComponent).getEnforceablePorts();
		logger.debug("holder component type: {}", holderComponent.getName());
		logger.debug("holder port type: {}", holderPort.id);
		logger.debug("holder componentPorts size: {}", componentPorts.size());
	
		int portId = 0;
		while (portId < componentPorts.size() && !componentPorts.get(portId).id.equals(holderPort.id)) {
			portId++;
		}
		logger.debug("PortId: {} ", portId);
		effectPortBDD = behenc.getPortBDDs().get(holderComponent)[portId];
		
		/*
		 * For each cause port, we obtain all the component instances that provide this port and store the BDDs corresponding
		 * to the associated port variables in a common list, which is then passed on to the BDD computing function (requireBDD).  
		 */		
		for (Enumeration<Port> portEnum = causesPortToComponents.keys(); portEnum.hasMoreElements();) {
		Port requiredPort = portEnum.nextElement();
		
		ArrayList<BIPComponent> acceptedComponents = causesPortToComponents.get(requiredPort);
		for (BIPComponent acceptedComponent : acceptedComponents) {

			ArrayList<Port> compPorts = (ArrayList<Port>) wrapper.getBehaviourByComponent(acceptedComponent).getEnforceablePorts();
			logger.debug("causes component type: {}", acceptedComponent.getName());
			logger.debug("causes componentPorts size: {}", compPorts.size());
			portId = 0;
			// TODO: As above for the port names to Ports ids hash table
			while (portId < compPorts.size() && !compPorts.get(portId).id.equals(requiredPort.id)) {
				portId++;
			}
			portBDDs.add(behenc.getPortBDDs().get(acceptedComponent)[portId]);
			logger.debug("port BDDs size: {}", portBDDs.size());
			
			
		}
		acceptedBDDs.put(requiredPort, portBDDs);
		logger.debug("Port at causes of a require: {}", requiredPort.id);
		logger.debug("port BDDs size: {}", portBDDs.size());
		/*
		 * Should not clear portBDDs. 
		 */
	}
	
		return acceptBDD(effectPortBDD, acceptedBDDs);
	}
	
	/**
	 * This function computes the total Glue BDD that is the conjunction of the require and accept constraints.
	 * For each require/accept macro we find the different combinations of the component instances that correspond
	 * to each constraint and take the conjunction of all these.
	 * @throws BIPEngineException 
	 */
	public BDD totalGlue() throws BIPEngineException {
		
		BDD result = engine.getBDDManager().one();

		logger.debug("Glue spec require Constraints size: {} ", glueSpec.requiresConstraints.size());
		if (!glueSpec.requiresConstraints.isEmpty() || glueSpec.requiresConstraints.equals(null)) {
			for (Requires requires : glueSpec.requiresConstraints) {
				ArrayList<BDD> RequireBDDs = decomposeRequireGlue(requires);
				for (BDD effectInstance : RequireBDDs) {
					result.andWith(effectInstance);
				}
			}
		} else {
			logger.warn("No require constraints provided (usually there should be some).");
		}
		
		logger.debug("Glue spec accept Constraints size: {} ", glueSpec.acceptConstraints.size());
		if (!glueSpec.acceptConstraints.isEmpty() || glueSpec.acceptConstraints.equals(null)) {
			for (Accepts accepts : glueSpec.acceptConstraints) {
				ArrayList<BDD> AcceptBDDs = decomposeAcceptGlue(accepts);
				for (BDD effectInstance : AcceptBDDs) {
					result.andWith(effectInstance);
				}
			}
		} else {
			logger.warn("No accept constraints were provided (usually there should be some).");
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

