package org.bip.engine.api;

import java.util.List;
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.api.Behaviour;
import org.bip.exceptions.BIPEngineException;

// TODO: Auto-generated Javadoc
/**
 * Orchestrates the execution of the behaviour, glue and current state encoders.
 * At the initialization phase, it receives information about the behaviour of 
 * BIP components sends this to the behaviour encoder and orders it to compute 
 * the total behaviour BDD. At the initialization phase, it also receives information
 *  about the glue, sends this to the glue encoder and orders it to compute the glue BDD.
 *  During each execution cycle, it receives information about the current state of 
 *  the BIP components and their disabled ports, sends this to the current state encoder 
 * and orders it to compute the current state BDDs. When a new interaction is chosen
 *  by the engine, it notifies all the BIP components.
  * @author mavridou
 */

public interface BIPCoordinator extends Coordinator {
	
    /**
     * Gets the behaviour encoder instance.
     *
     * @return the behaviour encoder instance
     */
    BehaviourEncoder getBehaviourEncoderInstance();
    
    /**
     * Gets the BDD manager.
     *
     * @return the BDD manager
     */
    BDDFactory getBDDManager();

	
}
