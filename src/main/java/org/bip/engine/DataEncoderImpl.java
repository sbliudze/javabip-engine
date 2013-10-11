package org.bip.engine;

import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deals with the DataGlue.
 * Encodes the informSpecific information.
 * @author mavridou
 */
public class DataEncoderImpl implements DataEncoder{

	private BDDBIPEngine engine;
	private BehaviourEncoder behaviourEncoder; 
	

	private Logger logger = LoggerFactory.getLogger(CurrentStateEncoderImpl.class);
	
	/*
	 * Possible implementation: Send each combination's BDD to the engine that takes the 
	 * conjunction of all of them on-the-fly. When all the registered components have informed
	 * at an execution cycle then take the conjunction of the above total BDD with the global BDD.
	 * 
	 * Actually we do not care about the number of components that have informed. We care whether the semaphore has been totally released.
	 * 
	 * Otherwise, the Data Encoder needs to compute and keep the total BDD. It needs to know when
	 * all the components will have informed the engine about their current state and only then
	 * send the total BDD to the core engine.
	 * 
	 * Three are the main factors that should contribute in the implementation decision. 
	 * 1. BDD complexity (especially in the conjunction with the global BDD)
	 * 2. Number of function calls
	 * 3. Transfering information regarding the number of components that have informed.
	 * 4. Here also is the questions whether the DataEncoder should save the BDDs or not at each execution cycle.
	 * @see org.bip.engine.DataEncoder#inform(java.util.Map)
	 */

	public BDD informSpecific(BIPComponent decidingComponent, Port decidingPort, Map<BIPComponent, Port> disabledCombinations) throws BIPEngineException {
		/*
		 * The disabledCombinations and disabledComponents are checked in the DataCoordinator,
		 * wherein exceptions are thrown. Here, we just use assertion.
		 */
		assert(disabledCombinations != null);
		Set<BIPComponent> disabledComponents= disabledCombinations.keySet();
		assert (disabledComponents != null);

		BDD result = engine.getBDDManager().zero();
		
		for (BIPComponent component : disabledComponents){
			Port port = disabledCombinations.get(component);
			if (port == null || port.id.isEmpty()){
		        try {
					logger.error("Disabled port {} is null or empty "+port.id);
					throw new BIPEngineException("Disabled port {} is null or empty "+port.id);
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;
				}
		      }
			//TODO: to be updated
//			result.andWith(behaviourEncoder.getBDDOfAPort(component, port.id).not());
		}
		return result;
	}


	public void setEngine(BDDBIPEngine engine) {
		this.engine=engine;
	}

	public void setBehaviourEncoder(BehaviourEncoder behaviourEncoder) {
		this.behaviourEncoder = behaviourEncoder;
	}




}
