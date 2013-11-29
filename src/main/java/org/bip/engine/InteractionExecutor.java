package org.bip.engine;

import java.util.List;
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

	public void executeInteractions(List<List<Port>> portsToFire) throws BIPEngineException;
}
