package org.bip.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;
import org.bip.api.Behaviour;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives the current state, glue and behaviour BDDs.
 * Computes the possible maximal interactions and picks one non-deterministically.
 * Notifies the BIPCoordinator about the outcome.
 * @author mavridou
 */
public class BDDBIPEngineImpl implements BDDBIPEngine {
	
	private Logger logger = LoggerFactory.getLogger(BDDBIPEngineImpl.class);
	private Hashtable<BIPComponent, BDD> currentStateBDDs = new Hashtable<BIPComponent, BDD>();
	private ArrayList <BDD> disabledCombinationBDDs = new ArrayList<BDD>();
	private Hashtable<BIPComponent, BDD> behaviourBDDs = new Hashtable<BIPComponent, BDD>();
	/* BDD for ΛFi */
	private BDD totalBehaviour;
	/* BDD for Glue */
	private BDD totalGlue;
	
	private BDD totalBehaviourAndGlue;
	
	//
	private int noNodes=10000;
	private int cacheSize=1000;
	
	/* Use JavaBDD Bdd Manager */
	private BDDFactory bdd_mgr= BDDFactory.init("java", noNodes, cacheSize); 
	private ArrayList<Integer> positionsOfPorts = new ArrayList<Integer>();
	Hashtable<Port, Integer> portToPosition= new Hashtable<Port, Integer>();
	Hashtable<Integer, BiDirectionalPair> dVariablesToPosition = new Hashtable<Integer, BiDirectionalPair>();
	ArrayList<Integer> positionsOfDVariables = new ArrayList<Integer>();

	private BIPCoordinator wrapper;
	private BDD dataGlueBDD;

	/** 
	 * Counts the number of enabled ports in the Maximal cube chosen 
	 */
	private int countPortEnable(byte[] in_cube, ArrayList<Integer> allPorts){
		int out_count = 0;
		
		for (int i = 0; i < allPorts.size(); i++) {
			if (in_cube[allPorts.get(i)] == 1 || in_cube[allPorts.get(i)] == -1)
				out_count++;
		}
		return out_count;
	}

	/**
	 * @return 0 equal <br>
	 *         1 if cube2 in cube1, cube1 bigger <br>
	 *         2 not comparable 
	 *         3 if cube1 in cube2, cube2 bigger <br>
	 */
	private int compareCube(byte[] cube1, byte[] cube2, ArrayList<Integer> portBDDsPosition) {
		boolean cube1_big = false;
		boolean cube2_big = false;
		
		for (int i = 0; i < portBDDsPosition.size(); i++) {
			if ((cube1[portBDDsPosition.get(i)] != 0) && (cube2[portBDDsPosition.get(i)] == 0)) {
				cube1_big = true;
			} else if ((cube2[portBDDsPosition.get(i)] != 0) && (cube1[portBDDsPosition.get(i)] == 0)) {
				cube2_big = true;
			}
		}
		/* if cube1 is bigger than cube2 (cube1 contains cube2) */
		if (cube1_big && !cube2_big)
			return 1;
		/* if cubes are not comparable */
		else if (cube1_big && cube2_big)
			return 2;
		/* if cube1 is smaller than cube2 (cube2 contains cube1) */
		else if (!cube1_big && cube2_big)
			return 3;
		/* if cubes are equal */
		else
			return 0;
	}

	/** Copy maximal cube */
	private void addCube(ArrayList<byte[]> cubeMaximals, byte[] cube, int position) {
		for (int i = 0; i < cube.length; i++)
			if (cube[i] == -1)
				cube[i] = 1;
		cubeMaximals.add(position, cube);
	}

	private void findMaximals(ArrayList<byte[]> cubeMaximals, byte[] c_cube, ArrayList<Integer> portBDDsPosition) {
		int size = cubeMaximals.size();
		for (int i = 0; i < size; i++) {
			int comparison = compareCube(c_cube, cubeMaximals.get(i), portBDDsPosition);
			if (comparison == 1 || comparison == 0) {
				cubeMaximals.remove(i);
				addCube(cubeMaximals, c_cube, i);
				return;
			}
			if (comparison == 3)
				return;
		}
		
		addCube(cubeMaximals, c_cube, cubeMaximals.size());
	}
	

	public final void totalBehaviourBDD() throws BIPEngineException{
		
		BDD totalBehaviourBdd = bdd_mgr.one();
		BDD tmp;
		
		for (Enumeration<BIPComponent> componentsEnum = behaviourBDDs.keys(); componentsEnum.hasMoreElements(); ){
			tmp = totalBehaviourBdd.and(behaviourBDDs.get(componentsEnum.nextElement()));
			totalBehaviourBdd.free();
			totalBehaviourBdd = tmp;
		}
		this.totalBehaviour=totalBehaviourBdd;
//		synchronized (totalBehaviourAndGlue) {
			if (totalGlue!=null){
				totalBehaviourAndGlue=this.totalBehaviour.and(totalGlue);	
				if (totalBehaviourAndGlue == null) {
					try {
						logger.error("Total Behaviour and Glue is null");
						throw new BIPEngineException("Total Behaviour and Glue is null");
					} catch (BIPEngineException e) {
						e.printStackTrace();
						throw e;	
					}
				}	
				this.totalBehaviour.free();
				totalGlue.free();
			}
//		}
	}

	public final BDD totalCurrentStateBdd(Hashtable<BIPComponent, BDD> currentStateBDDs) {
		BDD totalCurrentStateBdd = bdd_mgr.one();
		BDD tmp;

		for (Enumeration<BIPComponent> componentsEnum = currentStateBDDs.keys(); componentsEnum.hasMoreElements(); ){
			BIPComponent component = componentsEnum.nextElement();
			if (currentStateBDDs.get(component)==null){
				logger.error("Current state BDD is null of component {}", component);
				//TODO: Add exception
			}
			tmp = totalCurrentStateBdd.and(currentStateBDDs.get(component));
			totalCurrentStateBdd.free();
			totalCurrentStateBdd = tmp;
		}
		return totalCurrentStateBdd;	
	}
	

	public final BDD totalDisabledCombinationsBdd(ArrayList<BDD> disabledCombinationBDDs) {
		BDD totalDisabledCombinationBdd = bdd_mgr.one();
		BDD tmp;

		for (BDD disabledCombinationBDD : disabledCombinationBDDs ){
			if (disabledCombinationBDD==null){
				logger.error("Disabled Combination BDD is null");
				//TODO: Add exception
			}
//			totalDisabledCombinationBdd.andWith(disabledCombinationBDD);
			tmp = totalDisabledCombinationBdd.and(disabledCombinationBDD);
			totalDisabledCombinationBdd.free();
			totalDisabledCombinationBdd = tmp;
		}
		return totalDisabledCombinationBdd;	
	}

	public final void runOneIteration() throws BIPEngineException {

		byte[] chosenInteraction;
		ArrayList<byte[]> cubeMaximals = new ArrayList<byte[]>();
		Hashtable<BIPComponent, ArrayList<Port>> chosenPorts = new Hashtable<BIPComponent, ArrayList<Port>>();
		ArrayList<BIPComponent> chosenComponents = new ArrayList<BIPComponent>();
		byte[] cubeMaximal = new byte[wrapper.getNoPorts() + wrapper.getNoStates()];

		cubeMaximals.add(0, cubeMaximal);

		BDD totalCurrentStateAndDisabledCombinations;

		if (!disabledCombinationBDDs.isEmpty() || disabledCombinationBDDs != null){
			BDD totalDisabledCombination = totalDisabledCombinationsBdd(disabledCombinationBDDs);
			if (totalDisabledCombination==null) {
				try {
					logger.error("Total Disabled Combination BDD is null, although there are disabled Combinations.");
					throw new BIPEngineException("Total Disabled Combination BDD is null, although there are disabled combinations.");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;	
				}
			}
			/* Λi Ci */
			totalCurrentStateAndDisabledCombinations = totalCurrentStateBdd(currentStateBDDs).and(totalDisabledCombination);
			if (totalCurrentStateAndDisabledCombinations==null) {
				try {
					logger.error("Total Current States BDD is null");
					throw new BIPEngineException("Total Current States BDD is null with disabled combinations");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;	
				}
			}
		}
		else{
			/* Λi Ci */
			totalCurrentStateAndDisabledCombinations = totalCurrentStateBdd(currentStateBDDs);

			if (totalCurrentStateAndDisabledCombinations==null) {
				try {
					logger.error("Total Current States BDD is null");
					throw new BIPEngineException("Total Current States BDD is null");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					throw e;	
				}
			}	
		}
		
		/* Compute global BDD: solns= Λi Fi Λ G Λ (Λi Ci) */
		BDD solns = totalBehaviourAndGlue.and(totalCurrentStateAndDisabledCombinations);

		if (solns==null ) {
			try {
				logger.error("Global BDD is null");
				throw new BIPEngineException("Global BDD is null");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;	
			}
		}	
		totalCurrentStateAndDisabledCombinations.free();
		ArrayList<byte[]> a = new ArrayList<byte[]>();
		

		/*
		 * Re-ordering function and statistics printouts
		 */
		bdd_mgr.reorder(BDDFactory.REORDER_SIFTITE);
		logger.info("Reorder stats: "+bdd_mgr.getReorderStats());

		a.addAll(solns.allsat()); // TODO, can we find random maximal
								  // interaction without getting all solutions
								  // at once?

		logger.info("******************************* Engine **********************************");
		logger.debug("Number of possible interactions is: {} ", a.size());
		Iterator<byte[]> it = a.iterator();

		/* for debugging */
		while (it.hasNext()) {
			byte[] value = it.next();

			StringBuilder sb = new StringBuilder();
			for (byte b : value) {
				sb.append(String.format("%02X ", b));
			}
			logger.debug(sb.toString());
		}

		for (int i = 0; i < a.size(); i++){
			// TODO: Sort this function out
			findMaximals(cubeMaximals, a.get(i), positionsOfPorts);
		}

		/* deadlock detection */
		int size = cubeMaximals.size();
		if (size == 0) {
			try {
				throw new BIPEngineException("Deadlock. No maximal interactions.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				logger.error(e.getMessage());	
				throw e;
			} 
		} else if (size == 1) { //TODO: Once findMaximals is fixed, we do not need to add a dummy cube at initialisation. Hence this check becomes irrelevant.
			if (countPortEnable(cubeMaximals.get(0), positionsOfPorts) == 0) {
				try {
					throw new BIPEngineException("Deadlock. No enabled ports.");
				} catch (BIPEngineException e) {
					e.printStackTrace();
					logger.error(e.getMessage());	
					throw e;
				} 
			}
		}

		logger.debug("Number of maximal interactions: " + cubeMaximals.size());
		Random rand = new Random();
		/*
		 * Pick a random maximal interaction
		 */
		int randomInt = rand.nextInt(cubeMaximals.size());
		/*
		 * Update chosen interaction
		 */
		chosenInteraction = cubeMaximals.get(randomInt); 
		
		cubeMaximals.clear();
		logger.info("ChosenInteraction: ");
		for (int k = 0; k < chosenInteraction.length; k++)
			logger.info("{}",chosenInteraction[k]);
		
		//TODO: Fix String to Port
		ArrayList<String> portsExecuted = new ArrayList<String>();
		Iterable<Map<BIPComponent, Iterable<Port>>> allInteractions = new ArrayList<Map<BIPComponent,Iterable<Port>>>() ;
//		ArrayList<Hashtable<BIPComponent, ArrayList<Port>>> allInteractions = new ArrayList<Hashtable<BIPComponent, ArrayList<Port>>>();
		
		for (Integer i: positionsOfDVariables){
			Hashtable<BIPComponent, ArrayList<Port>> oneInteraction = new Hashtable<BIPComponent, ArrayList<Port>>();
			if (chosenInteraction[i]==1){
				BiDirectionalPair pair = dVariablesToPosition.get(i);
				BiDirectionalPair firstPair = (BiDirectionalPair) pair.getFirst();
				BiDirectionalPair secondPair = (BiDirectionalPair) pair.getSecond();
				ArrayList<Port> componentPorts = new ArrayList<Port>();
				componentPorts.add((Port) firstPair.getSecond());
				Port port = (Port) firstPair.getSecond();
				portsExecuted.add(port.id);
				oneInteraction.put((BIPComponent) firstPair.getFirst(), (ArrayList<Port>) componentPorts);
				logger.info("Chosen Component: {}", firstPair.getFirst());
				logger.info("Chosen Port: {}", componentPorts.get(0).id);
				componentPorts.clear();
				componentPorts.add((Port) secondPair.getSecond());
				Port port2 = (Port) secondPair.getSecond();
				
				portsExecuted.add(port2.id);
				oneInteraction.put((BIPComponent) secondPair.getFirst(), (ArrayList<Port>) componentPorts);
				logger.info("Chosen Component: {}", secondPair.getFirst());
				logger.info("Chosen Port: {}", componentPorts.get(0).id);
			}
			logger.info("OneInteraction size: "+ oneInteraction.size());
			((List) allInteractions).add(oneInteraction);
//			oneInteraction.clear();
		}

		for (Enumeration<BIPComponent> componentsEnum = behaviourBDDs.keys(); componentsEnum.hasMoreElements(); ){
			BIPComponent component = componentsEnum.nextElement();
			logger.debug("Component: "+component.getName());
			
			Iterable <Port> componentPorts = wrapper.getBehaviourByComponent(component).getEnforceablePorts();
			if (componentPorts == null || !componentPorts.iterator().hasNext()){
				logger.warn("Component {} does not have any enforceable ports.", component.getName());		
			} 			
			ArrayList <Port> enabledPorts = new ArrayList<Port>();
			//TODO: Change! to executeInteractions
			for (Port componentPort : componentPorts){
				if(!portsExecuted.contains(componentPort.id) && chosenInteraction[portToPosition.get(componentPort)]==1){
					enabledPorts.add(componentPort);
				}
			}
			if (!enabledPorts.isEmpty()) {
				logger.info("Chosen Component: {}", component.getName());
				logger.info("Chosen Port: {}", enabledPorts.get(0).id);
			}
			chosenPorts.put(component, enabledPorts);
			chosenComponents.add(component);
		}
		
		logger.info("*************************************************************************");
		((List) allInteractions).add(chosenPorts);

		for (Map<BIPComponent, Iterable<Port>> inter: allInteractions)
		{
			for (Map.Entry <BIPComponent, Iterable<Port>> e:inter.entrySet())
			{
				System.out.println("ENGINE ENTRY"+ e.getKey().getName() + " - "+ e.getValue());
			}
		}
		
		wrapper.execute(allInteractions);

//		wrapper.executeInteractions(allInteractions);
//		portsExecuted.clear();
//		wrapper.executeComponents(chosenComponents, chosenPorts);


		solns.free();
//		currentStateBDDs.clear();
//		disabledCombinationBDDs.clear();

	}
	
	public synchronized void informCurrentState(BIPComponent component, BDD componentBDD) {
		currentStateBDDs.put(component, componentBDD);
	}
	
	public synchronized void informSpecific(BDD informSpecific) {
		disabledCombinationBDDs.add(informSpecific);
	}
	
	public void specifyDataGlue(BDD specifyDataGlue) {
		dataGlueBDD = specifyDataGlue;
		if (totalBehaviourAndGlue!= null){
			BDD tmp = totalBehaviourAndGlue.and(dataGlueBDD);
			totalBehaviourAndGlue.free();
			totalBehaviourAndGlue = tmp;
		}
		else if(this.totalGlue!=null){
			BDD tmp = totalGlue.and(dataGlueBDD);
			totalGlue.free();
			totalGlue = tmp;
		}
		else{
			return;
		}
		
	}
	
	public synchronized void informBehaviour(BIPComponent component, BDD componentBDD) {
		behaviourBDDs.put(component, componentBDD);
	}

	public void informGlue(BDD totalGlue) throws BIPEngineException {

		this.totalGlue = totalGlue;
		//TODO: Fix synchronized
//		synchronized (totalBehaviourAndGlue) {
			if (totalBehaviour!=null && this.dataGlueBDD!=null){
				totalBehaviourAndGlue=totalBehaviour.and(this.totalGlue).and(this.dataGlueBDD);	
//				bdd_mgr.reorder(BDDFactory.REORDER_SIFTITE);
//				logger.info("Reorder stats: "+bdd_mgr.getReorderStats());
				if (totalBehaviourAndGlue == null ) {
					try {
						logger.error("Total Behaviour and Glue is null");
						throw new BIPEngineException("Total Behaviour and Glue is null");
					} catch (BIPEngineException e) {
						e.printStackTrace();
						throw e;	
					}
				}	
				totalBehaviour.free();
				this.totalGlue.free();
			}
//		}
	}
	
	public ArrayList<Integer> getPositionsOfPorts() {
		return positionsOfPorts;
	}
	
	public Hashtable<Port, Integer> getPortToPosition() {
		return portToPosition;
	}
	
	/**
	 * @return the dVariablesToPosition
	 */
	public Hashtable<Integer, BiDirectionalPair> getdVariablesToPosition() {
		return dVariablesToPosition;
	}

	/**
	 * @param dVariablesToPosition the dVariablesToPosition to set
	 */
	public void setdVariablesToPosition(Hashtable<Integer, BiDirectionalPair> dVariablesToPosition) {
		this.dVariablesToPosition = dVariablesToPosition;
	}
	
	/**
	 * @return the positionsOfDVariables
	 */
	public ArrayList<Integer> getPositionsOfDVariables() {
		return positionsOfDVariables;
	}

	/**
	 * @param positionsOfDVariables the positionsOfDVariables to set
	 */
	public void setPositionsOfDVariables(ArrayList<Integer> positionsOfDVariables) {
		this.positionsOfDVariables = positionsOfDVariables;
	}

	public void setOSGiBIPEngine(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}
	
	public synchronized BDDFactory getBDDManager() {
		return bdd_mgr;
	}





}
