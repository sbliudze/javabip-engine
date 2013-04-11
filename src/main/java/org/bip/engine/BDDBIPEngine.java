package org.bip.engine;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Behaviour;


public interface BDDBIPEngine {

	//TODO: add nice comments, probably to use at later on documentation
	void informCurrentState(BIPComponent component, BDD componentBDD);
	
	void informGlue(BDD glue);
	
	void informTotalBehaviour(BDD totalBehaviour);
	
	void runOneIteration();

}

