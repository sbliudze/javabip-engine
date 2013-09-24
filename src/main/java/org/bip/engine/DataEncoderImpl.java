package org.bip.engine;

import java.util.Map;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.api.DataCoordinator;
import org.bip.behaviour.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataEncoderImpl implements DataEncoder{

	private DataCoordinator dataCoordinator; 
	private BDDBIPEngine engine;
	private BIPCoordinator wrapper;

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
	 * @see org.bip.engine.DataEncoder#inform(java.util.Map)
	 */
	public BDD inform(Map<BIPComponent, Port> disabledCombinations) {
		// TODO Auto-generated method stub
		return null;
	}

	public void setBIPCoordinator(BIPCoordinator wrapper) {
		this.wrapper= wrapper;	
	}


	public void setEngine(BDDBIPEngine engine) {
		this.engine=engine;
	}

}
