package org.bip.engine;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent; 
import org.bip.behaviour.Behaviour;
import java.util.ArrayList;
import org.bip.behaviour.Port;
import org.bip.glue.BIPGlue;

public interface GlueEncoder {
	
	//void register (BIPComponent component, Behaviour behaviour);
	
    void specifyGlue(BIPGlue glue);
	
	BDD totalGlue();

}
