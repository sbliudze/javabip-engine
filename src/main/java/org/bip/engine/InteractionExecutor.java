package org.bip.engine;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;

public interface InteractionExecutor {
	
	public void executeInteraction(Iterable<Map<BIPComponent, Iterable<Port>>> portsToFire) throws BIPEngineException;

}
