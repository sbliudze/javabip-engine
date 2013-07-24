package org.bip.engine;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
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
	private Hashtable<BIPComponent, BDD> behaviourBDDs = new Hashtable<BIPComponent, BDD>();
	/* BDD for ΛFi */
	private BDD totalBehaviour;
	/* BDD for Glue */
	private BDD totalGlue;
	
	private BDD totalBehaviourAndGlue;
	
	//
	private int noNodes=10000;
	private int cacheSize=1000;
	
	/* JavaBDD Bdd Manager */
	private BDDFactory bdd_mgr= BDDFactory.init("java", noNodes, cacheSize); 
	private ArrayList<Integer> positionsOfPorts = new ArrayList<Integer>();
	Hashtable<Port, Integer> portToPosition= new Hashtable<Port, Integer>();

	private BIPCoordinator wrapper;


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
	

	public final void totalBehaviourBDD(){
		
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

	public final void runOneIteration() throws BIPEngineException {
		byte[] chosenInteraction;
		ArrayList<byte[]> cubeMaximals = new ArrayList<byte[]>();
		Hashtable<BIPComponent, ArrayList<Port>> chosenPorts = new Hashtable<BIPComponent, ArrayList<Port>>();
		ArrayList<BIPComponent> chosenComponents = new ArrayList<BIPComponent>();
		byte[] cubeMaximal = new byte[wrapper.getNoPorts() + wrapper.getNoStates()];

		cubeMaximals.add(0, cubeMaximal);

		/* Λi Ci */
		BDD totalCurrentState = totalCurrentStateBdd(currentStateBDDs);

		/* Compute global BDD: solns= Λi Fi Λ G Λ (Λi Ci) */
		BDD solns = totalBehaviourAndGlue.and(totalCurrentState);
		totalCurrentState.free();
		ArrayList<byte[]> a = new ArrayList<byte[]>();

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
			logger.debug("{}",chosenInteraction[k]);

		for (Enumeration<BIPComponent> componentsEnum = behaviourBDDs.keys(); componentsEnum.hasMoreElements(); ){
			BIPComponent component = componentsEnum.nextElement();
			
			logger.debug("Component: "+component.getName());
			
			Behaviour componentBehaviour = wrapper.getBehaviourByComponent(component);
			// TODO: Use Iterators!
			ArrayList<Port> componentPorts = (ArrayList<Port>) componentBehaviour.getEnforceablePorts();
			if (componentPorts == null || componentPorts.isEmpty()){
				logger.warn("Component {} does not have any enforceable ports.", component.getName());		
			} 
			
			ArrayList <Port> enabledPorts = new ArrayList<Port>();

			for (Port componentPort : componentPorts){
				if(chosenInteraction[portToPosition.get(componentPort)]==1){
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

		wrapper.executeComponents(chosenComponents, chosenPorts);

		solns.free();
	}
	
	public synchronized void informCurrentState(BIPComponent component, BDD componentBDD) {
		currentStateBDDs.put(component, componentBDD);
	}
	
	public synchronized void informBehaviour(BIPComponent component, BDD componentBDD) {
		behaviourBDDs.put(component, componentBDD);
	}

	public void informGlue(BDD totalGlue) {

		this.totalGlue = totalGlue;
		//TODO: Fix synchronized
//		synchronized (totalBehaviourAndGlue) {
			if (totalBehaviour!=null){
				totalBehaviourAndGlue=totalBehaviour.and(this.totalGlue);		
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
	
	public void setOSGiBIPEngine(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}
	
	public synchronized BDDFactory getBDDManager() {
		return bdd_mgr;
	}

}
