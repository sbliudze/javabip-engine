package org.bip.engine;

import java.util.Map;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataEncoderImpl implements DataEncoder{

	private BehaviourEncoder behaviourEncoder; 
	private BDDBIPEngine engine;
	private BIPCoordinator wrapper;

	private Logger logger = LoggerFactory.getLogger(CurrentStateEncoderImpl.class);
	
	public BDD inform(Map<BIPComponent, Port> disabledCombinations) {
		// TODO Auto-generated method stub
		return null;
	}

	public void setBIPCoordinator(BIPCoordinator wrapper) {
		this.wrapper= wrapper;	
	}

	public void setBehaviourEncoder(BehaviourEncoder behaviourEncoder) {
		this.behaviourEncoder=behaviourEncoder;
	}

	public void setEngine(BDDBIPEngine engine) {
		this.engine=engine;
	}

}
