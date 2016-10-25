package org.bip.engine;

import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;
import org.bip.api.Port;
import org.bip.engine.api.ResourceCoordinator;
import org.bip.engine.api.ResourceEncoder;

public class ResourceEncoderImpl implements ResourceEncoder {

	@Override
	public BDD encodeDisabledCombinations(BIPComponent decidingComponent,
			Port decidingPort, Map<BIPComponent, Set<Port>> disabledCombinations) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setResourceCoordinator(ResourceCoordinator resourceCoordinator) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setBDDManager(BDDFactory bddManager) {
		// TODO Auto-generated method stub
		
	}

}
