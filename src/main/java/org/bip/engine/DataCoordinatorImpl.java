package org.bip.engine;

import java.util.ArrayList;

import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.api.Behaviour;
import org.bip.api.DataCoordinator;
import org.bip.behaviour.Port;
import org.bip.glue.BIPGlue;

// There is no need for DataCoordinator interface, just DataCoordinatorImpl will implement BIP engine interface.
// DataCoordinatorImpl needs BIP coordinator ( BIP Engine again ) that actual does all the computation of BIP
// engine. DataCoordinatorImpl takes care of only of data querying and passing to BIP executors.

// TODO
// DataCoordinator intercepts call register and inform from BIPExecutor. For each BIPComponent it
// creates a Proxy of BIPComponent (also BIPComponent class) that is registered with BIPCoordinator.
// This BIPComponent proxy is the identity provided to BIPCordinator. Now, DataCordinator implements
// just BIPEngine interface so it able also to intercept informs and translate it into proper informs.
// BIPComponent Proxy can intercept execute functions invoked by BIPCoordinator and enrich with data
// provided by DataCoordinatorImpl. Thus, Proxy BIPComponent knows about DataCoordinatorImpl, and
// original BIPcomponent, so BIPcomponent proxy can query DataCoordinatorIMpl for the data and call
// function execute of the original BIPComponent with proper data.


public class DataCoordinatorImpl implements BIPEngine{

	@Override
	public void specifyGlue(BIPGlue glue) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void register(BIPComponent component, Behaviour behaviour) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void execute() {
		// TODO Auto-generated method stub
		
	}

}
