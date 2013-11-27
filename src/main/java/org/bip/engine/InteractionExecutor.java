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
	/**
	 * Notifies all the components whether they need to perform a transition.
	 * If yes, the ArrayList contains the port that should be fires. Otherwise, it contains null.
	 * 
	 * TODO: change the annotations
	 * @param allComponents, all components
	 * @throws BIPEngineException 
	 */
	void execute (byte[] valuation) throws BIPEngineException;

	public void executeInteractions(Iterable<Map<BIPComponent, Iterable<Port>>> portsToFire) throws BIPEngineException;
//	public void executeInteractions(byte[] valuation) throws BIPEngineException;

}
