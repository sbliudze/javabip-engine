package org.bip.engine.api;

import org.bip.api.BIPEngine;
import org.bip.api.ResourceProvider;

public interface ResourceCoordinator extends BIPEngine, InteractionExecutor {
	
	public void registerResourceProvider(ResourceProvider provider);

}
