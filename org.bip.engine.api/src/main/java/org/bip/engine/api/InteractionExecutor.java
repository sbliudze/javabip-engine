package org.bip.engine.api;

import java.util.List;
import org.bip.api.Port;
import org.bip.exceptions.BIPEngineException;

// TODO: Auto-generated Javadoc
/**
 * The Interface InteractionExecutor.
 *
 * @author mavridou
 */
public interface InteractionExecutor {
	
	/**
	 * Notifies all the components whether they need to perform a transition.
	 * If yes, the ArrayList contains the port that should be fires. Otherwise, it contains null.
	 * 
	 * TODO: change the annotations
	 *
	 * @param valuation the valuation
	 * @throws BIPEngineException the BIP engine exception
	 */
	void execute (byte[] valuation) throws BIPEngineException;

	/**
	 * Execute interactions.
	 *
	 * @param portsToFire the ports to fire
	 * @throws BIPEngineException the BIP engine exception
	 */
	public void executeInteractions(List<List<Port>> portsToFire) throws BIPEngineException;
}
