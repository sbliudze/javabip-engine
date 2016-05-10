package org.bip.engine.api;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bip.api.BIPEngine;
import org.bip.api.Port;

// TODO: Auto-generated Javadoc
/**
 * The Interface DataCoordinator.
 *
 * @author mavridou
 */

public interface DataCoordinator extends BIPEngine, BIPCoordinator, InteractionExecutor, DataInformer {
	
	/**
	 * Gets the data encoder.
	 *
	 * @return the data encoder
	 */
	DataEncoder getDataEncoder();

	/**
	 * Setd variables to position.
	 *
	 * @param dVariablesToPosition            the dVariablesToPosition to set
	 */
	void setdVariablesToPosition(Map<Integer, Entry<Port, Port>> dVariablesToPosition);

	/**
	 * Sets the positions of d variables.
	 *
	 * @param positionsOfDVariables            the positionsOfDVariables to set
	 */
	void setPositionsOfDVariables(List<Integer> positionsOfDVariables);

	/**
	 * Gets the d var positions to wires.
	 *
	 * @return the dVariablesToPosition
	 */
	Map<Integer, Entry<Port, Port>> getdVarPositionsToWires();

	/**
	 * Gets the positions of d variables.
	 *
	 * @return the positionsOfDVariables
	 */
	public List<Integer> getPositionsOfDVariables();
}
