package org.bip.engine;

import java.util.ArrayList;

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
			}
			else{
				portToComponents.put(causePort, wrapper.getBIPComponentInstances(causePort.specType));
			}
		}


		/** Find all effect component instances */
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
				result.add(componentRequire(effectInstance, requires.effect, requires.causes, portToComponents));
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

	/** BDD for the Require Constraint */
	BDD requireBDD(BDD RequirePortHolder, ArrayList<Port> AuxPort, Hashtable<Port, ArrayList<BDD>> RequirePorts) {
		BDD tmp, tmp2, tmp3;
		BDD aux = engine.getBDDManager().zero();
		BDD aux2 = engine.getBDDManager().one();

		ArrayList<BDD> auxPortBDDs = new ArrayList<BDD>();
		int size = RequirePorts.size();
		for (int j = 0; j < size; j++) {
			auxPortBDDs.addAll(RequirePorts.get(AuxPort.get(j)));
			int size2 = auxPortBDDs.size();
			for (int i = 0; i < size2; i++) {
				BDD aux3 = engine.getBDDManager().one();
				for (int k = 0; k < size2; k++) {
					if (i == k) {
						tmp3 = auxPortBDDs.get(k).and(aux3);
						aux3.free();
						aux3 = tmp3;
					} else {
						tmp3 = auxPortBDDs.get(k).not().and(aux3);
						aux3.free();
						aux3 = tmp3;
					}
				}
				tmp = aux3.or(aux);
				aux.free();
				aux = tmp;
			}
			tmp2 = aux.and(aux2);
			aux2.free();
			aux2 = tmp2;
			auxPortBDDs.clear();

		}
		BDD require_bdd = RequirePortHolder.not().or(aux2);
		aux2.free();
		return require_bdd;


	}

	/** BDD for the Accept Constraint */
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
	 * 
	 * @param
	 * @return
	 */
	BDD componentRequire(BIPComponent holderComponent, Port holderPort, ArrayList<Port> requiredPorts, Hashtable<Port, ArrayList<BIPComponent>> effectPortToComponents) {

		BDD portBDD;
		Hashtable<Port, ArrayList<BDD>> requiredBDDs = new Hashtable<Port, ArrayList<BDD>>();
		ArrayList<BDD> PortBDDs = new ArrayList<BDD>();
		Integer CompID = wrapper.getBIPComponentIdentity(holderComponent);
		ArrayList<Port> componentPorts = (ArrayList<Port>) wrapper.getBIPComponentBehaviour(CompID).getEnforceablePorts();
		int PortID = 0;
		for (int i = 1; i <= componentPorts.size(); i++) {
			if (componentPorts.get(i - 1).id.equals(holderPort.id)) {
				PortID = i;
				break;
			}
		}
		portBDD = behenc.getPortBDDs().get(CompID)[PortID - 1];

		ArrayList<BIPComponent> RequiredComponents = new ArrayList<BIPComponent>();
		ArrayList<Port> AuxPorts = new ArrayList<Port>();
		int size = effectPortToComponents.size();
		for (int p = 0; p < size; p++) {
			Port port = requiredPorts.get(p);
			RequiredComponents.addAll(effectPortToComponents.get(port));
			for (int i = 0; i < RequiredComponents.size(); i++) {
				Integer ComID = wrapper.getBIPComponentIdentity(RequiredComponents.get(i));
				ArrayList<Port> compPorts = (ArrayList<Port>) wrapper.getBIPComponentBehaviour(ComID).getEnforceablePorts();
				int PID = 0;
				for (int j = 1; j <= compPorts.size(); j++) {
					if (compPorts.get(j - 1).id.equals(requiredPorts.get(p).id)) {
						PID = j;
						break;
					}
				}
				PortBDDs.add(behenc.getPortBDDs().get(ComID)[PID - 1]);
			}
			requiredBDDs.put(requiredPorts.get(p), PortBDDs);
			AuxPorts.add(requiredPorts.get(p));
		}

		BDD Require = requireBDD(portBDD, AuxPorts, requiredBDDs);
		return Require;
	}

	/** Accept BDD */
	BDD componentAccept(BIPComponent HolderComponent, Port HolderPort, ArrayList<Port> acceptedPorts, Hashtable<Port, ArrayList<BIPComponent>> EffectPorttoComponents) {

		BDD portBDD;
		Hashtable<Port, ArrayList<BDD>> acceptedBDDs = new Hashtable<Port, ArrayList<BDD>>();
		ArrayList<BDD> portBDDs = new ArrayList<BDD>();
		Integer compID = wrapper.getBIPComponentIdentity(HolderComponent);
		ArrayList<Port> componentPorts = (ArrayList<Port>) wrapper.getBIPComponentBehaviour(compID).getEnforceablePorts();
		int portID = 0;
		for (int i = 1; i <= componentPorts.size(); i++) {
			if (componentPorts.get(i - 1).id.equals(HolderPort.id)) {
				portID = i;
				break;
			}
		}
		portBDD = behenc.getPortBDDs().get(compID)[portID - 1];

		ArrayList<BIPComponent> acceptedComponents = new ArrayList<BIPComponent>();
		ArrayList<Port> AuxPorts = new ArrayList<Port>();
		int size = EffectPorttoComponents.size();
		for (int p = 0; p < size; p++) {
			Port port = acceptedPorts.get(p);
			acceptedComponents.addAll(EffectPorttoComponents.get(port));
			for (int i = 0; i < acceptedComponents.size(); i++) {
				Integer ComID = wrapper.getBIPComponentIdentity(acceptedComponents.get(i));
				ArrayList<Port> compPorts = (ArrayList<Port>) wrapper.getBIPComponentBehaviour(ComID).getEnforceablePorts();
				int PID = 0;
				for (int j = 1; j <= compPorts.size(); j++) {
					if (compPorts.get(j - 1).id.equals(acceptedPorts.get(p).id)) {
						PID = j;
						break;
					}
				}
				portBDDs.add(behenc.getPortBDDs().get(ComID)[PID - 1]);
			}
			acceptedBDDs.put(acceptedPorts.get(p), portBDDs);
			AuxPorts.add(acceptedPorts.get(p));
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

		logger.debug("Glue spec accept Constraints size: {} ", glueSpec.acceptConstraints.size());
		if (!glueSpec.acceptConstraints.isEmpty()) {
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

