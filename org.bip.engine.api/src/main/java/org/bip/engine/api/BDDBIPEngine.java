package org.bip.engine.api;


import java.util.List;
import java.util.Set;

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
	 * Inform current state.
	 *
	 * @param component the component
	 * @param componentBDD BDD corresponding to the current state of the particular component
	 */
	void informCurrentState(BIPComponent component, BDD componentBDD);
	
	/**
	 * Inform behaviour.
	 *
	 * @param component the component
	 * @param componentBDD BDD corresponding to the behavior of the particular component
	 */
	void informBehaviour(BIPComponent component, BDD componentBDD);
	
	/**
	 * Inform glue.
	 *
	 * @param totalGlue BDD corresponding to the total glue of the components of the system
	 * @throws BIPEngineException the BIP engine exception
	 */	
	void informGlue(List<BDD> totalGlue) throws BIPEngineException;
	
	/**
	 * Total behaviour bdd.
	 *
	 * @throws BIPEngineException the BIP engine exception
	 */
	void totalBehaviourBDD() throws BIPEngineException;
	
	/**
	 * Computes possible maximal interactions and chooses one non-deterministically.
	 *
	 * @throws BIPEngineException the BIP engine exception
	 */
	void runOneIteration() throws BIPEngineException;
	
	/**
	 * Setter for the OSGiBIPEngine.
	 *
	 * @param wrapper the new OS gi bip engine
	 */
	void setOSGiBIPEngine(BIPCoordinator wrapper);
	
	/**
	 * Getter for the BDD Manager.
	 *
	 * @return the BDD manager
	 */
	BDDFactory getBDDManager(); 
		
	/**
	 * Specify temporary extra constraints.
	 *
	 * @param informSpecific the inform specific
	 */
	void specifyTemporaryExtraConstraints(BDD informSpecific);

	/**
	 * Specify permanent extra constraints.
	 *
	 * @param specifyDataGlue the specify data glue
	 */
	void specifyPermanentExtraConstraints(Set<BDD> specifyDataGlue);

	void resetBehaviourBDD();

}


