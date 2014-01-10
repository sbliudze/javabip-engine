package org.bip.engine;


import java.util.List;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;
import org.bip.exceptions.BIPEngineException;

/**
 * Receives the current state, glue and behaviour BDDs.
 * Computes the possible maximal interactions and picks one non-deterministically.
 * Notifies the OSGiBIPEngine about the outcome.
 * @author mavridou
 */
public interface BDDBIPEngine {

	/**
	 * @param component
	 * @param componentBDD BDD corresponding to the current state of the particular component
	 */
	void informCurrentState(BIPComponent component, BDD componentBDD);
	
	/**
	 * @param component
	 * @param componentBDD BDD corresponding to the behavior of the particular component
	 */
	void informBehaviour(BIPComponent component, BDD componentBDD);
	
	/**
	 * @param totalGlue BDD corresponding to the total glue of the components of the system
	 * @throws BIPEngineException 
	 */	
	void informGlue(List<BDD> totalGlue) throws BIPEngineException;
	
	void totalBehaviourBDD() throws BIPEngineException;
	
	/**
	 * Computes possible maximal interactions and chooses one non-deterministically
	 * @throws BIPEngineException 
	 */
	void runOneIteration() throws BIPEngineException;
	
	/**
	 * Setter for the OSGiBIPEngine
	 */
	void setOSGiBIPEngine(BIPCoordinator wrapper);
	
	/**
	 * Getter for the BDD Manager
	 */
	BDDFactory getBDDManager(); 
		
	void specifyTemporaryExtraConstraints(BDD informSpecific);

	void specifyPermanentExtraConstraints(BDD specifyDataGlue);



}


