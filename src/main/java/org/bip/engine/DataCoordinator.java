package org.bip.engine;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bip.api.BIPEngine;
import org.bip.api.Port;

/**
 * @author mavridou
 */

public interface DataCoordinator extends BIPEngine, BIPCoordinator, InteractionExecutor {
	DataEncoder getDataEncoder();

	/**
	 * @param dVariablesToPosition
	 *            the dVariablesToPosition to set
	 */
	void setdVariablesToPosition(Map<Integer, Entry<Port, Port>> dVariablesToPosition);

	/**
	 * @param positionsOfDVariables
	 *            the positionsOfDVariables to set
	 */
	void setPositionsOfDVariables(List<Integer> positionsOfDVariables);

	/**
	 * @return the dVariablesToPosition
	 */
	Map<Integer, Entry<Port, Port>> getdVarPositionsToWires();

	/**
	 * @return the positionsOfDVariables
	 */
	public List<Integer> getPositionsOfDVariables();
}
