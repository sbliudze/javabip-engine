package org.bip.engine;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import java.util.Hashtable;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

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
				throw new BIPEngineException("The glue parser has failed to compute the glue object.\n" +
						"\tPossible reasons: Corrupt or non-existant glue XML file.");
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
	 * 
	 * @return Hashtable with the causes ports as keys and the set of component instances that correspond to them as values
	 * 
	 * @throws BIPEngineException 
	 * @throws InterruptedException 
	 */
	Hashtable<Port, ArrayList<BDD>> findCausesComponents (Iterable<Port> requireCause) throws BIPEngineException{

		Hashtable<Port, ArrayList<BDD>> portToComponents = new Hashtable<Port, ArrayList<BDD>>();

		for (Port causePort : requireCause) {
			if (causePort.specType == null || causePort.specType.isEmpty()) {
				logger.warn("Spec type not specified or empty in a macro cause. Skipping the port.");
			} else  if (causePort.id == null || causePort.id.isEmpty()) {
				logger.warn("Port name not specified or empty in a macro cause. Skipping the port.");
			} else {
				ArrayList<BIPComponent> components = (ArrayList<BIPComponent>) wrapper.getBIPComponentInstances(causePort.specType);
				ArrayList<BDD> portBDDs = new ArrayList<BDD>();
				for (BIPComponent component: components){
					logger.debug("Component: {} ",component.getName());
					logger.debug("Causes ports: {} ", causePort);
					portBDDs.add(behenc.getBDDOfAPort(component, causePort.id));
				}
				logger.debug("Number of BDDs for port {} {}", causePort.id , portBDDs.size() );

				if (portBDDs.isEmpty() || portBDDs==null || portBDDs.get(0) == null) {
					try {
						logger.error("Port {} in causes was defined incorrectly. It does not match any registered port types", causePort.id);
						throw new BIPEngineException("Port "+causePort.id+" in causes was defined incorrectly. It does not match any registered port types");
					} catch (BIPEngineException e) {
						e.printStackTrace();
						throw e;	
					}
				}	
				portToComponents.put(causePort, portBDDs);
			}
		}
		//System.out.println("Port to Components size: "+portToComponents.size());
		return portToComponents;
	}
	
	/**
	 * Helper function that takes as an argument the port at the effect part of a constraint macro
	 * and returns the component instances that correspond to this port.
	 * 
	 * @param Effect port
	 * 
	 * @return ArrayList with the set of component instances that correspond to the effect port
	 * 
	 * @throws BIPEngineException 
	 * @throws InterruptedException 
	 */
	ArrayList<BIPComponent> findEffectComponents (Port effectPort) throws BIPEngineException{
		
		assert(effectPort.id != null && !effectPort.id.isEmpty());
		assert (effectPort.specType != null && !effectPort.specType.isEmpty());
		
		ArrayList<BIPComponent> requireEffectComponents = (ArrayList<BIPComponent>) wrapper.getBIPComponentInstances(effectPort.specType);
		if (requireEffectComponents.isEmpty()) {
			try {
				logger.error("Spec type in effect for component {} was defined incorrectly. It does not match any registered component types", effectPort.specType);
				throw new BIPEngineException("Spec type in effect for component "+ effectPort.specType +" was defined incorrectly. It does not match any registered component types");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}	
		return requireEffectComponents;
	}
	
	/**
	 * Finds the BDDs of the ports of the components that are needed for computing one require macro and 
	 * computes the BDD for this macro by calling the requireBDD method.
	 * 
	 * @param  require interaction constraints
	 * 
	 * @return the BDD that corresponds to a Require macro
	 * 
	 * @throws BIPEngineException 
	 * @throws InterruptedException 
	 */
	ArrayList<BDD> decomposeRequireGlue(Requires requires) throws BIPEngineException{
		ArrayList<BDD> result = new ArrayList<BDD>();
		
		if (requires.effect == null) {
			try {
				logger.error("Effect part of a Require constraint was not specified.");
				throw new BIPEngineException("Effect part of a Require constraint was not specified.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		if (requires.effect.id.isEmpty()) {
			try {
				logger.error("The port at the effect part of a Require constraint was not specified.");
				throw new BIPEngineException("The port at the effect part of a Require constraint was not specified.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		if (requires.effect.specType.isEmpty()) {
			try {
				logger.error("The component type of a port at the effect part of a Require constraint was not specified.");
				throw new BIPEngineException("The component type of a port at the effect part of a Require constraint was not specified.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		/* Find all effect component instances */
		ArrayList<BIPComponent> requireEffectComponents =findEffectComponents(requires.effect);
		
		if (requires.causes == null) {
			try {
				logger.error("Causes part of a Require constraint was not specified in the macro.");
				throw new BIPEngineException("Causes part of a Require constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		/* Find all causes component instances */
		List<List<Port>> requireCauses=requires.causes;
		List<Hashtable<Port, ArrayList<BDD>>> allPorts = new ArrayList<Hashtable<Port, ArrayList<BDD>>>();
		for (List<Port> requireCause : requireCauses){
			allPorts.add(findCausesComponents(requireCause));
		}
		
		//TODO: dont recompute the causes for each component instance
		for (BIPComponent effectInstance : requireEffectComponents) {
			logger.debug("Require Effect port type: "+ requires.effect.id+" of component "+requires.effect.specType);
			result.add(requireBDD(behenc.getBDDOfAPort(effectInstance, requires.effect.id), allPorts));
		}
		return result;
	}

	/**
	 * Finds the BDDs of the ports of the components that are needed for computing one accept macro and 
	 * computes the BDD for this macro by calling the acceptBDD method.
	 * 
	 * @param  accept interaction constraints
	 * 
	 * @return the BDD that corresponds to an Accept macro
	 * 
	 * @throws BIPEngineException 
	 * @throws InterruptedException 
	 */
	ArrayList<BDD> decomposeAcceptGlue(Accepts accept) throws BIPEngineException{
		ArrayList<BDD> result = new ArrayList<BDD>();

		if (accept.effect == null) {
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
		
		if (accept.causes == null) {
			try {
				logger.error("Causes part of an Accept constraint was not specified in the macro.");
				throw new BIPEngineException("Causes part of an Accept constraint was not specified");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		/* Find all causes component instances */
		Hashtable<Port, ArrayList<BDD>> portsToBDDs = findCausesComponents(accept.causes);

		for (BIPComponent effectInstance : acceptEffectComponents) {
			logger.debug("Accept Effect port type: {} ", accept.effect.id);
			logger.debug("PortToComponents size: {} ", portsToBDDs.size());
			result.add(acceptBDD(behenc.getBDDOfAPort(effectInstance, accept.effect.id), portsToBDDs));
		}
		return result;
	}

	/**
	 * Computes the BDD that corresponds to a Require macro.
	 * 
	 * @param BDD of the port of the component holder of the Require macro
	 * @param Hashtable of ports of the "causes" part of the Require macro 
	 * and the corresponding port BDDs of the component instances
	 * 
	 *  @return the BDD that corresponds to a Require macro.
	 */
	// TODO: Think of the cardinality issue (move to Accept)
	BDD requireBDD(BDD requirePortHolder, List<Hashtable<Port, ArrayList<BDD>>> requiredPorts) {
		
		BDD allDisjunctiveCauses = engine.getBDDManager().zero();

		for(Hashtable<Port, ArrayList<BDD>> requiredPort : requiredPorts){
			BDD allCausesBDD = engine.getBDDManager().one();
			logger.debug("Hashtable requiredPorts size: "+requiredPort.size());
			for (Enumeration<Port> portEnum = requiredPort.keys(); portEnum.hasMoreElements();) {
				Port port = portEnum.nextElement();
				ArrayList<BDD> auxPortBDDs = requiredPort.get(port);
				logger.debug("Required port BDDs size: " + auxPortBDDs.size());
				logger.debug("Required port: "+ port.id +" "+ port.specType);	
				int size = auxPortBDDs.size();
				BDD oneCauseBDD = engine.getBDDManager().zero();
				
				for (int i = 0; i < size; i++) {
					BDD monomial = engine.getBDDManager().one();				
					for (int j = 0; j < size; j++) {
						if (i == j) {
							/* Cannot use andWith here. Do not want to free the 
							 * BDDs assigned to the ports at the Behaviour Encoder.*/
							BDD tmp = monomial.and(auxPortBDDs.get(j));
							monomial.free();
							monomial = tmp;
						} else {
							/* Cannot use andWith here. Do not want to free the 
							 * BDDs assigned to the ports at the Behaviour Encoder.*/
							BDD tmp = monomial.and(auxPortBDDs.get(j).not());
							monomial.free();
							monomial = tmp;
						}
					}
					oneCauseBDD.orWith(monomial);
					logger.debug("oneCauseBDD: "+ oneCauseBDD);
				}
				allCausesBDD.andWith(oneCauseBDD);
				logger.debug("allCausesBDD: "+ allCausesBDD);
			}
			allDisjunctiveCauses.orWith(allCausesBDD);
			logger.debug("allDisjunctiveCausesBDD: "+ allDisjunctiveCauses);
		}
		allDisjunctiveCauses.orWith(requirePortHolder.not());
		


		return allDisjunctiveCauses;			
	}
	
	/**
	 * Computes the BDD that corresponds to an Accept macro.
	 * 
	 * @param BDD of the port of the component holder of the Accept macro
	 * @param Hashtable of ports of the "causes" part of the Accept macro 
	 * and the corresponding port BDDs of the component instances
	 * 
	 *  @return the BDD that corresponds to an Accept macro.
	 */
	//TODO: change the arguments to reflect the cardinality 
	BDD acceptBDD(BDD acceptPortHolder, Hashtable<Port, ArrayList<BDD>> acceptedPorts) { 
		BDD tmp;
		
		ArrayList<BDD> totalPortBDDs= new ArrayList<BDD>();
		
		/* Get all port BDDs registered in the Behaviour Encoder and 
		 * add them in the totalPortBDDs ArrayList. */
		Hashtable<BIPComponent, BDD[]> componentToBDDs = behenc.getPortBDDs();

		for (Enumeration<BIPComponent> componentsEnum = componentToBDDs.keys(); componentsEnum.hasMoreElements(); ){
			BIPComponent component = componentsEnum.nextElement();
			BDD [] portBDD = componentToBDDs.get(component);
			for (int p=0; p<portBDD.length;p++){
				totalPortBDDs.add(portBDD[p]);
			}
		}
		logger.debug("totalPortBDDs size: "+totalPortBDDs.size());
		BDD allCausesBDD = engine.getBDDManager().one();
		
		//TODO: Improve the  below
		if (acceptedPorts.size()>1){
			for (BDD portBDD : totalPortBDDs){
				boolean exist = false;
				
				for (Enumeration<Port> portEnum = acceptedPorts.keys(); portEnum.hasMoreElements();){
					Port port = portEnum.nextElement();
					ArrayList<BDD> currentPortInstanceBDDs=acceptedPorts.get(port);
					logger.debug("currentPortInstanceBDDs size"+currentPortInstanceBDDs.size());
					int indexPortBDD=0;
		
					if((portBDD).equals(acceptPortHolder)){
						exist=true;
					}
					while (!exist && indexPortBDD < currentPortInstanceBDDs.size()){
						if (currentPortInstanceBDDs.get(indexPortBDD).equals(portBDD)){
							exist =true;
							
						}
						else {
							indexPortBDD++;
						}
					}
				}
				if (!exist) {
					//allCausesBDD.andWith(portBDD.not());
					tmp = portBDD.not().and(allCausesBDD);
					allCausesBDD.free();
					allCausesBDD = tmp;
				}
			}
		}
		else{
			for (BDD portBDD : totalPortBDDs){
				boolean exist = false;
				
				for (Enumeration<Port> portEnum = acceptedPorts.keys(); portEnum.hasMoreElements();){
					Port port = portEnum.nextElement();
					ArrayList<BDD> currentPortInstanceBDDs=acceptedPorts.get(port);
					int indexPortBDD=0;
		
					if((portBDD).equals(acceptPortHolder)){
						exist=true;
					}
					while (!exist && indexPortBDD < currentPortInstanceBDDs.size()){
						if (currentPortInstanceBDDs.get(indexPortBDD).equals(portBDD)){
							exist =true;
						}
						else {
							indexPortBDD++;
						}
					}
					if (!exist) {
						tmp = portBDD.not().and(allCausesBDD);
						allCausesBDD.free();
						allCausesBDD = tmp;
					}
				}
			}
		}
		return allCausesBDD.orWith(acceptPortHolder.not());
	}


	/**
	 * This function computes the total Glue BDD that is the conjunction of the require and accept constraints.
	 * For each require/accept macro we find the different combinations of the component instances that correspond
	 * to each constraint and take the conjunction of all these.
	 * 
	 * @throws BIPEngineException 
	 * @throws InterruptedException 
	 */
	public BDD totalGlue() throws BIPEngineException{
		
		BDD result = engine.getBDDManager().one();

		logger.debug("Glue spec require Constraints size: {} ", glueSpec.requiresConstraints.size());
		if (!glueSpec.requiresConstraints.isEmpty() || !glueSpec.requiresConstraints.equals(null)) {
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
		if (!glueSpec.acceptConstraints.isEmpty() || !glueSpec.acceptConstraints.equals(null)) {
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

