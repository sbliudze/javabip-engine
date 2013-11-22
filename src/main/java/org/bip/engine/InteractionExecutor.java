package org.bip.engine;

import java.util.Map;

import org.bip.api.BIPComponent;
import org.bip.api.Port;
import org.bip.exceptions.BIPEngineException;

/**
 * 
 * @author mavridou
 */
public interface InteractionExecutor {
	
	public void executeInteractions(Iterable<Map<BIPComponent, Iterable<Port>>> portsToFire) throws BIPEngineException;

}
