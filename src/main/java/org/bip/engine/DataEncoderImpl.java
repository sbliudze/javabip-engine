package org.bip.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;
import org.bip.glue.DataWire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deals with the DataGlue.
 * Encodes the informSpecific information.
 * @author Anastasia Mavridou
 */
public class DataEncoderImpl implements DataEncoder{

	private BDDBIPEngine engine;
	private DataCoordinator dataCoordinator;
	private BehaviourEncoder behaviourEncoder;
	
	Iterator<DataWire> dataGlueSpec;
	Map <BiDirectionalPair, BDD> portsToDVarBDDMapping = new Hashtable<BiDirectionalPair, BDD>();
	private Logger logger = LoggerFactory.getLogger(CurrentStateEncoderImpl.class);
	ArrayList<BDD> componentOutBDDs = new ArrayList<BDD>();
	ArrayList<BDD> componentInBDDs = new ArrayList<BDD>();
//	private ArrayList<BDD> dBddVariable;

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

		//for Or-ing
		BDD result = engine.getBDDManager().one();
		
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
			
			BiDirectionalPair portsPair = new BiDirectionalPair(decidingPort, port);
			result.orWith(portsToDVarBDDMapping.get(portsPair).not());
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
		createDataBDDNodes();
	}
	
	private Iterable<Port> inPorts(Port inData) throws BIPEngineException {
		/*
		 * Store in the Arraylist below all the possible out ports.
		 * Later to take their cross product.
		 */
		ArrayList<Port> componentInPorts = new ArrayList<Port>();
		/*
		 * IMPORTANT
		 * These are not ports actually. In the specType the type of the component is stored.
		 * In the id the name of the data variable is stored.
		 * 
		 * Input data are always assigned to transitions. Therefore, I need the list of ports of the component
		 * that will re receiving the data.
		 */
		String inComponentType = inData.specType;

		Iterable<BIPComponent> inComponentInstances = dataCoordinator.getBIPComponentInstances(inComponentType);
		for (BIPComponent component: inComponentInstances){
			Iterable<Port> dataInPorts = dataCoordinator.getBehaviourByComponent(component).portsNeedingData(inData.id);
			componentInPorts.addAll((Collection<? extends Port>) dataInPorts);
			for (Port port : dataInPorts){
				componentInBDDs.add(behaviourEncoder.getBDDOfAPort(component, port.id));
			}
		}
		return componentInPorts;
	}
	
	private Iterable<Port> outPorts (Port outData) throws BIPEngineException {
		/*
		 * Store in the Arraylist below all the possible in ports.
		 * Later to take their cross product.
		 */
		ArrayList<Port> componentOutPorts = new ArrayList<Port>();	

		 /* 
		 * Output data are not associated to transitions. Here, will take the conjunction of all possible
		 * transitions of a component.
		 */
		String outComponentType = outData.specType;
		Iterable<BIPComponent> outComponentInstances = dataCoordinator.getBIPComponentInstances(outComponentType);
		for (BIPComponent component: outComponentInstances){
			/*
			 * Limit down the possible combinations by using the getDataOutPorts function of the DataCoordinator
			 */
			//allOutPorts.addAll((Collection<? extends Port>) dataCoordinator.getBehaviourByComponent(component).getEnforceablePorts());
			ArrayList<Port> dataOutPorts = dataCoordinator.getDataOutPorts(component, outData.id);
			componentOutPorts.addAll(dataOutPorts);
			for (Port port : dataOutPorts){
				componentOutBDDs.add(behaviourEncoder.getBDDOfAPort(component, port.id));
			}
		}
		return componentOutPorts;
	}

	private void createDataBDDNodes() throws BIPEngineException {
		/*
		 * Get the number of BDD-nodes of the System. We base this on the assumption that all the components
		 * have registered before. Therefore, we know the size of the BDD nodes created for states and ports,
		 * which is the current System BDD size.
		 */
		int initialSystemBDDSize = dataCoordinator.getNoPorts() + dataCoordinator.getNoStates();
		int currentSystemBddSize = initialSystemBDDSize;
		while (dataGlueSpec.hasNext()){
			DataWire dataWire = dataGlueSpec.next();
			Iterable<Port> componentInPorts = inPorts(dataWire.to);
			Iterable<Port> componentOutPorts = outPorts(dataWire.from);
			/*
			 * Here take the cross product of in and out variables to create the d-variables for one data-wire
			 * Store this in a Map with the ports as the key and the d-variable as a value.
			 * 
			 * Before creating the d-variable check for dublicates in the Map. If this does not exist then create it.
			 * 
			 * Clear the componentInPorts and componentOutPorts for the next dataWire components.
			 */
			for (Port inPort: componentInPorts){
				for (Port outPort :componentOutPorts){
					//TODO: Ports do not have component holder information, Change below 
					BiDirectionalPair inOutPortsPair = new BiDirectionalPair(inPort, outPort);
					if (!portsToDVarBDDMapping.containsKey(inOutPortsPair)){
						/*Create new variable in the BDD manager for the d-variables.
						 * Does it start from 0 or 1 ? 
						 * if from 0 increase later
						 */
						currentSystemBddSize++;
						BDD temp = engine.getBDDManager().ithVar(currentSystemBddSize);
						//TODO: Fix that
						temp = componentInBDDs.get(currentSystemBddSize-initialSystemBDDSize).and(componentOutBDDs.get(currentSystemBddSize));
						portsToDVarBDDMapping.put(inOutPortsPair, temp);
		//					dBddVariable.add(engine.getBDDManager().ithVar(currentSystemBddSize));
						/*
						 * Store the position of the d-variables in the BDD manager
						 */
						engine.getdVariablesToPosition().put(inOutPortsPair, currentSystemBddSize);
						if (portsToDVarBDDMapping.get(inOutPortsPair)== null || portsToDVarBDDMapping.get(inOutPortsPair).isZero()){
							try {
								logger.error("Single node BDD for d variable for ports "+ inPort.id+" and "+ outPort.id+ " is equal to null");
								throw new BIPEngineException("Single node BDD for d variable for ports "+ inPort.id+" and "+ outPort.id+ " is equal to null");
							} catch (BIPEngineException e) {
								e.printStackTrace();
								throw e;
							}
						}
//					portsToDVarBDDMapping.put(inOutPortsPair, dBddVariable.get(currentSystemBddSize-initialSystemBDDSize));
					}
				}
			}
		}
	}

	public void setEngine(BDDBIPEngine engine) {
		this.engine=engine;
	}
	
	public void setBehaviourEncoder(BehaviourEncoder behaviourEncoder) {
		this.behaviourEncoder=behaviourEncoder;
	}

	public void setDataCoordinator(DataCoordinator dataCoordinator) {
		this.dataCoordinator = dataCoordinator;
	}
	
}
