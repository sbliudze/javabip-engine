package org.bip.engine;

import java.util.ArrayList;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;

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
	 * @param totalGlue BDD corresponding to the total glue of the components of the system
	 */
	void informGlue(BDD totalGlue);
	
	/**
	 * @param totalBehaviour BDD corresponding to the total behaviour of the components of the system
	 */
	void informTotalBehaviour(BDD totalBehaviour);
	
	/**
	 * Computes possible maximal interactions and chooses one non-deterministically
	 */
	void runOneIteration();
	
	/**
	 * Setter for the OSGiBIPEngine
	 */
	void setOSGiBIPEngine(OSGiBIPEngine wrapper);
	
	/**
	 * Getter for the BDD Manager
	 */
	BDDFactory getBDDManager(); 
	
	/**
	 * @return the position of port BDDs in the BDD Manager
	 */
	ArrayList<Integer> getPositionsOfPorts();



}

