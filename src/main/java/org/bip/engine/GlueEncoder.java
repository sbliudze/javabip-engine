package org.bip.engine;

import net.sf.javabdd.BDD;

import org.bip.glue.BIPGlue;

public interface GlueEncoder {
	
	//void register (BIPComponent component, Behaviour behaviour);
	
    void specifyGlue(BIPGlue glue);
	
	BDD totalGlue();

}
