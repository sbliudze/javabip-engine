package org.bip.engine;

import java.util.List;
import java.util.Map;

import org.bip.api.BIPEngine;

/**
 * @author mavridou
 */

public interface DataCoordinator extends BIPEngine, BIPCoordinator, InteractionExecutor {
	DataEncoder getDataEncoder();
	
	/**
	 * @param dVariablesToPosition the dVariablesToPosition to set
	 */
	void setdVariablesToPosition(Map<Integer, BiDirectionalPair> dVariablesToPosition);
	
	/**
	 * @param positionsOfDVariables the positionsOfDVariables to set
	 */
	void setPositionsOfDVariables(List<Integer> positionsOfDVariables);
	
	/**
	 * @return the dVariablesToPosition
	 */
	Map<Integer, BiDirectionalPair> getdVariablesToPosition() ;

	
	/**
	 * @return the positionsOfDVariables
	 */
	public List<Integer> getPositionsOfDVariables();
}
