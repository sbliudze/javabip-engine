package org.bip.engine;

import org.bip.api.BIPEngine;

/**
 * As it is now there is no need for a DataCoordinator interface. If that continues to be the case then the
 * DataCoordinatorImpl can implement the BIPEngine and the InteractionExecutor directly and delete this interface.
 * 
 * @author mavridou
 */

public interface DataCoordinator extends BIPEngine, InteractionExecutor {

}
