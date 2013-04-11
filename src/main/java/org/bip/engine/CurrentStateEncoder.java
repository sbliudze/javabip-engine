package org.bip.engine;
import java.util.ArrayList;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent; 
import org.bip.behaviour.Behaviour;
import org.bip.behaviour.Port;

public interface CurrentStateEncoder {

	//void register (BIPComponent component, Behaviour behaviour);
	
	BDD inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts);
	
}


