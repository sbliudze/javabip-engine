package org.bip.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.api.Behaviour;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;
import org.bip.glue.DataWire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deals with the DataGlue.
 * Encodes the informSpecific information.
 * @author mavridou
 */
public class DataEncoderImpl implements DataEncoder{

	private BDDBIPEngine engine;
	private BehaviourEncoder behaviourEncoder; 
	private DataCoordinator dataCoordinator;
	
	Iterator<DataWire> dataGlueSpec;

	private Logger logger = LoggerFactory.getLogger(CurrentStateEncoderImpl.class);
	
	/*
	 * Possible implementation: Send each combination's BDD to the engine that takes the 
	 * conjunction of all of them on-the-fly. When all the registered components have informed
	 * at an execution cycle then take the conjunction of the above total BDD with the global BDD.
	 * 
	 * Actually we do not care about the number of components that have informed. We care whether the semaphore has been totally released.
	 * 
	 * Otherwise, the Data Encoder needs to compute and keep the total BDD. It needs to know when
	 * all the components will have informed the engine about their current state and only then
	 * send the total BDD to the core engine.
	 * 
	 * Three are the main factors that should contribute in the implementation decision. 
	 * 1. BDD complexity (especially in the conjunction with the global BDD)
	 * 2. Number of function calls
	 * 3. Transfering information regarding the number of components that have informed.
	 * 4. Here also is the questions whether the DataEncoder should save the BDDs or not at each execution cycle.
	 * @see org.bip.engine.DataEncoder#inform(java.util.Map)
	 */

	public BDD informSpecific(BIPComponent decidingComponent, Port decidingPort, Map<BIPComponent, Port> disabledCombinations) throws BIPEngineException {
		/*
		 * The disabledCombinations and disabledComponents are checked in the DataCoordinator,
		 * wherein exceptions are thrown. Here, we just use assertion.
		 */
		assert(disabledCombinations != null);
		Set<BIPComponent> disabledComponents= disabledCombinations.keySet();
		assert (disabledComponents != null);

		BDD result = engine.getBDDManager().zero();
		
		for (BIPComponent component : disabledComponents){
			Port port = disabledCombinations.get(component);
			if (port == null || port.id.isEmpty()){
		        try {
					logger.error("Disabled port {} is null or empty "+port.id);
					throw new BIPEngineException("Disabled port {} is null or empty "+port.id);
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;
				}
		      }
			//TODO: to be updated
//			result.andWith(behaviourEncoder.getBDDOfAPort(component, port.id).not());
		}
		return result;
	}
	
	public void specifyDataGlue(Iterable<DataWire> dataGlue) throws BIPEngineException {
		if (dataGlue == null || !dataGlue.iterator().hasNext()) {
			try {
				logger.error("The glue parser has failed to compute the data glue.\n" +
						"\tPossible reasons: No data transfer or corrupt/non-existant glue XML file.");
				throw new BIPEngineException("The glue parser has failed to compute the data glue.\n" +
						"\tPossible reasons: No data transfer or corrupt/non-existant glue XML file.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		this.dataGlueSpec = dataGlue.iterator();
	}
	
	public synchronized void createDataBDDNodes() throws BIPEngineException {
		/*
		 * Store in the Arraylists below all the possible in and out ports.
		 * Later to take their cross product.
		 */
		ArrayList<Port> allOutPorts = new ArrayList<Port>();
		ArrayList<Port> allInPorts = new ArrayList<Port>();
		while (dataGlueSpec.hasNext()){
			DataWire dataWire = dataGlueSpec.next();
			/*
			 * These are not ports actually. In the specType the type of the component is stored.
			 * In the id the name of the data variable is stored.
			 * 
			 * Input data are always assigned to transitions. Therefore, I need the list of ports of the component
			 * that will re receiving the data.
			 */
			Port inData = dataWire.from;
			String inComponentType = inData.specType;
			Iterable<BIPComponent> inComponentInstances = dataCoordinator.getBIPComponentInstances(inComponentType);
			for (BIPComponent component: inComponentInstances){
				allInPorts.addAll((Collection<? extends Port>) dataCoordinator.getBehaviourByComponent(component).portsNeedingData(inData.id));
			}
			 /* 
			 * Output data are not associated to transitions. Here, will take the conjunction of all possible
			 * transitions of a component.
			 * 
			 * TODO: Should try to find a way to limit down the possible transitions here
			 */
			Port outData = dataWire.to;
			String outComponentType = outData.specType;
			Iterable<BIPComponent> outComponentInstances = dataCoordinator.getBIPComponentInstances(outComponentType);
			for (BIPComponent component: outComponentInstances){
				allOutPorts.addAll((Collection<? extends Port>) dataCoordinator.getBehaviourByComponent(component).getEnforceablePorts());
			}
		}
		/*
		 * Take the cross product of all the ports to create the d-variables 
		 */
		for (Port inPort: allInPorts){
			for (Port outPort :allOutPorts){
				//TODO: Create a d-variable
			}
		}

	}

	public void setEngine(BDDBIPEngine engine) {
		this.engine=engine;
	}

	public void setBehaviourEncoder(BehaviourEncoder behaviourEncoder) {
		this.behaviourEncoder = behaviourEncoder;
	}
	
	public void setDataCoordinator(DataCoordinator dataCoordinator) {
		this.dataCoordinator = dataCoordinator;
	}

}
