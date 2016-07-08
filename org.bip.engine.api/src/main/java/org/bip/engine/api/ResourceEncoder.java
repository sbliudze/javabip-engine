package org.bip.engine.api;

import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;
import org.bip.api.Port;

public interface ResourceEncoder {

	BDD encodeDisabledCombinations(BIPComponent decidingComponent, Port decidingPort,
			Map<BIPComponent, Set<Port>> disabledCombinations);

	void setResourceCoordinator(ResourceCoordinator resourceCoordinator);

	void setBDDManager(BDDFactory bddManager);

}
