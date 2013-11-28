package org.bip.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;
import org.bip.api.Behaviour;
import org.bip.api.Port;
import org.bip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives the current state, glue and behaviour BDDs. Computes the possible
 * maximal interactions and picks one non-deterministically. Notifies the
 * BIPCoordinator about the outcome.
 * 
 * @author Anastasia Mavridou
 */
public class BDDBIPEngineImpl implements BDDBIPEngine {

	private Logger logger = LoggerFactory.getLogger(BDDBIPEngineImpl.class);
	private Hashtable<BIPComponent, BDD> currentStateBDDs = new Hashtable<BIPComponent, BDD>();
	private ArrayList<BDD> temporaryConstraints = new ArrayList<BDD>();
	private Hashtable<BIPComponent, BDD> behaviourBDDs = new Hashtable<BIPComponent, BDD>();

	private BDD totalConstraints;

	//
	private int noNodes = 10000;
	private int cacheSize = 1000;

	/* Use JavaBDD Bdd Manager */
	private BDDFactory bdd_mgr = BDDFactory.init("java", noNodes, cacheSize);
	Map<Integer, Entry<Port, Port>> dVariablesToPosition = new Hashtable<Integer, Entry<Port, Port>>();
	List<Integer> positionsOfDVariables = new ArrayList<Integer>();

	private BIPCoordinator wrapper;

	/**
	 * Counts the number of enabled ports in the Maximal cube chosen
	 */
	private int countPortEnable(byte[] in_cube, ArrayList<Integer> allPorts) {
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
	 *         2 not comparable 3 if cube1 in cube2, cube2 bigger <br>
	 */
	private int compareCube(byte[] cube1, byte[] cube2, List<Integer> portBDDsPosition) {
		boolean cube1_big = false;
		boolean cube2_big = false;

		for (int i = 0; i < portBDDsPosition.size(); i++) {
			logger.debug("portBDDsPosition: " + portBDDsPosition.get(i));
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
		logger.debug("Cube length: " + cube.length);
		for (int i = 0; i < cube.length; i++)
			if (cube[i] == -1)
				cube[i] = 1;
		cubeMaximals.add(position, cube);
	}

	private void findMaximals(ArrayList<byte[]> cubeMaximals, byte[] c_cube, List<Integer> portBDDsPosition) {
		int size = cubeMaximals.size();
		logger.debug("findMaximals size: " + size);
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

	public final BDD totalCurrentStateBdd(Hashtable<BIPComponent, BDD> currentStateBDDs) throws BIPEngineException {
		BDD totalCurrentStateBdd = bdd_mgr.one();
		BDD tmp;

		for (Enumeration<BIPComponent> componentsEnum = currentStateBDDs.keys(); componentsEnum.hasMoreElements();) {
			BIPComponent component = componentsEnum.nextElement();
			if (currentStateBDDs.get(component) == null) {
				logger.error("Current state BDD is null of component {}", component);
				throw new BIPEngineException("Current state BDD is null of component " + component);
			}
			tmp = totalCurrentStateBdd.and(currentStateBDDs.get(component));
			totalCurrentStateBdd.free();
			totalCurrentStateBdd = tmp;
		}
		return totalCurrentStateBdd;
	}

	public final BDD totalExtraBdd(ArrayList<BDD> disabledCombinationBDDs) throws BIPEngineException {
		BDD totalDisabledCombinationBdd = bdd_mgr.one();

		for (BDD disabledCombinationBDD : disabledCombinationBDDs) {
			if (disabledCombinationBDD == null) {
				logger.error("Disabled Combination BDD is null");
				throw new BIPEngineException("Disabled Combination BDD is null");
			}
			totalDisabledCombinationBdd.andWith(disabledCombinationBDD);
		}
		return totalDisabledCombinationBdd;
	}

	public synchronized final void runOneIteration() throws BIPEngineException {

		byte[] chosenInteraction;

		BDD totalCurrentStateAndDisabledCombinations = totalCurrentStateBdd(currentStateBDDs);
		// TODO: Check that the assert is correct
		assert (totalCurrentStateAndDisabledCombinations == null);

		if (temporaryConstraints != null || !temporaryConstraints.isEmpty()) {
			totalCurrentStateAndDisabledCombinations.andWith(totalExtraBdd(temporaryConstraints));
		}

		/* Compute global BDD: solns= Λi Fi Λ G Λ (Λi Ci) */
//		BDD solns = totalConstraints;
		BDD solns = totalConstraints.and(totalCurrentStateAndDisabledCombinations);
		totalCurrentStateAndDisabledCombinations.free();
//		solns.
//		andWith(totalCurrentStateAndDisabledCombinations);

		ArrayList<byte[]> possibleInteraction = new ArrayList<byte[]>();

		/*
		 * Re-ordering function and statistics printouts
		 */
		// bdd_mgr.reorder(BDDFactory.REORDER_SIFTITE);
		// logger.info("Reorder stats: "+bdd_mgr.getReorderStats());

		possibleInteraction.addAll(solns.allsat());

		logger.info("******************************* Engine **********************************");
		logger.info("Number of possible interactions is: {} ", possibleInteraction.size());
		Iterator<byte[]> it = possibleInteraction.iterator();

		/* for debugging */
		while (it.hasNext()) {
			byte[] value = it.next();

			StringBuilder sb = new StringBuilder();
			for (byte b : value) {
				sb.append(String.format("%02X ", b));
			}
			logger.debug(sb.toString());
		}

		logger.debug("Number of possible interactions: " + possibleInteraction.size());
		ArrayList<byte[]> cubeMaximals = new ArrayList<byte[]>();
		for (int i = 0; i < possibleInteraction.size(); i++) {

			logger.debug("Positions of D Variables size:" + positionsOfDVariables.size());
			findMaximals(cubeMaximals, possibleInteraction.get(i), wrapper.getBehaviourEncoderInstance().getPositionsOfPorts());
		}

		/* deadlock detection */
		int size = cubeMaximals.size();
		if (size == 0) {
			logger.error("Deadlock. No maximal interactions.");
			throw new BIPEngineException("Deadlock. No maximal interactions.");
		}
		 else if (size == 1) {
			if (countPortEnable(cubeMaximals.get(0), (ArrayList<Integer>) wrapper.getBehaviourEncoderInstance().getPositionsOfPorts()) == 0) {
				logger.error("Deadlock. No enabled ports.");
				throw new BIPEngineException("Deadlock. No enabled ports.");
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
		logger.debug("ChosenInteraction: ");
		for (int k = 0; k < chosenInteraction.length; k++)
			logger.debug("{}", chosenInteraction[k]);

		/*
		 * Beginning of the part to move to the Data Coordinator
		 */

		wrapper.execute(chosenInteraction);

		/*
		 * End of the part to move to the Data Coordinator
		 */

		solns.free();
		temporaryConstraints.clear();
		// for (BDD disabledCombination: disabledCombinationBDDs){
		// disabledCombination.free();
		// }

	}

	public synchronized void informCurrentState(BIPComponent component, BDD componentBDD) {
		currentStateBDDs.put(component, componentBDD);
	}

	public synchronized void specifyTemporaryExtraConstraints(final BDD informSpecific) {
		temporaryConstraints.add(informSpecific);
		logger.debug("INFORM SPECIFIC CALL: Disabled Combinations size " + temporaryConstraints.size());
	}

	public synchronized void specifyPermanentExtraConstraints(BDD extraConstraints) {
		synchronized (this) {
			if (totalConstraints == null) {
				totalConstraints = extraConstraints;
				logger.debug("Extra permanent constraints added to empty total BDD.");
			} else {
				totalConstraints.andWith(extraConstraints);
				logger.debug("Extra permanent constraints added to existing total BDD.");
			}
		}
	}

	public synchronized void informBehaviour(BIPComponent component, BDD componentBDD) {
		behaviourBDDs.put(component, componentBDD);
	}

	public synchronized final void totalBehaviourBDD() throws BIPEngineException {

		BDD totalBehaviourBdd = bdd_mgr.one();

		for (Enumeration<BIPComponent> componentsEnum = behaviourBDDs.keys(); componentsEnum.hasMoreElements();) {
			BDD tmp;
			tmp = totalBehaviourBdd.and(behaviourBDDs.get(componentsEnum.nextElement()));
			totalBehaviourBdd.free();
			totalBehaviourBdd = tmp;
		}

		synchronized (this) {
			if (totalConstraints == null) {
				totalConstraints = totalBehaviourBdd;
				logger.debug("Behaviour constraints added to empty total BDD.");
			} else {
				totalConstraints.andWith(totalBehaviourBdd);
				logger.debug("Behaviour constraints added to existing total BDD.");
			}
		}
	}


	public synchronized void informGlue(BDD totalGlue) throws BIPEngineException {
		synchronized (this) {
			if (totalConstraints == null) {
				totalConstraints = totalGlue;
				logger.debug("Glue constraints added to empty total BDD.");
			} else {
				totalConstraints.andWith(totalGlue);
				logger.debug("Glue constraints added to existing total BDD.");
			}
		}
	}

	public void setOSGiBIPEngine(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}

	public synchronized BDDFactory getBDDManager() {
		return bdd_mgr;
	}

}
