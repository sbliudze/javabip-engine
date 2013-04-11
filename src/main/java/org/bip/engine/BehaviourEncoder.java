package org.bip.engine;

import net.sf.javabdd.BDD;


public interface BehaviourEncoder {

	void createBDDNodes(int componentID, int noComponentPorts, int noComponentStates);
	
	BDD totalBehaviour();
	
}


