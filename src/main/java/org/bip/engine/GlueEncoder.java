package org.bip.engine;

import net.sf.javabdd.BDD;

import org.bip.exceptions.BIPEngineException;
import org.bip.glue.BIPGlue;

/**
 * Receives information about the glue and computes the glue BDD.
 * @author mavridou
 */

public interface GlueEncoder {

    /**
     * Receives the information about the glue of the system.
     * @param glue
     * @throws BIPEngineException 
     */
    void specifyGlue(BIPGlue glue);
	/**
	 * Setter for the BehaviourEncoder
	 */
    void setBehaviourEncoder(BehaviourEncoder behaviourEncoder);
	/**
	 * Setter for the BDDBIPEngine
	 */
	void setEngine(BDDBIPEngine engine);
	/**
	 * Setter for the OSGiBIPEngine
	 */
	void setBIPCoordinator(BIPCoordinator wrapper);
	
	/**
	 * @return the total Glue BDD
	 */
	BDD totalGlue();

}

