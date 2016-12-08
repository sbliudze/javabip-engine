/*
 * Copyright 2012-2016 École polytechnique fédérale de Lausanne (EPFL), Switzerland
 * Copyright 2012-2016 Crossing-Tech SA, Switzerland
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.javabip.engine;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.javabip.api.BIPComponent;
import org.javabip.api.PortBase;
import org.javabip.engine.api.BDDBIPEngine;
import org.javabip.engine.api.BIPCoordinator;
import org.javabip.exceptions.BIPEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives the current state, glue and behaviour BDDs. Computes the possible maximal interactions and picks one
 * non-deterministically. Notifies the BIPCoordinator about the outcome.
 * 
 * @author Anastasia Mavridou
 */
public class BDDBIPEngineImpl implements BDDBIPEngine {

	private Logger logger = LoggerFactory.getLogger(BDDBIPEngineImpl.class);
	private Hashtable<BIPComponent, BDD> currentStateBDDs = new Hashtable<BIPComponent, BDD>();
	private ArrayList<BDD> temporaryConstraints = new ArrayList<BDD>();
	private Hashtable<BIPComponent, BDD> behaviourBDDs = new Hashtable<BIPComponent, BDD>();
	private Set<BDD> permanentDataBDDs = new HashSet<BDD>();

	private BDD totalConstraints;
	// TODO: Put these as arguments
	private int noNodes = 1500;
	private int cacheSize = 50000;

	/* Use JavaBDD Bdd Manager */
	private BDDFactory bdd_mgr = BDDFactory.init("java", noNodes, cacheSize);
	Map<Integer, Entry<PortBase, PortBase>> dVariablesToPosition = new Hashtable<Integer, Entry<PortBase, PortBase>>();
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
			if ((cube1[portBDDsPosition.get(i)] != 0) && (cube2[portBDDsPosition.get(i)] == 0)) {
				cube1_big = true;
			} else if ((cube2[portBDDsPosition.get(i)] != 0) && (cube1[portBDDsPosition.get(i)] == 0)) {
				cube2_big = true;
			}
			// TODO: change into while loop
			if (cube1_big == true && cube2_big == true)
				break;
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
	// TODO: Delete
	// private void addCube(ArrayList<byte[]> cubeMaximals, byte[] cube,
	// int position) {
	// logger.trace("Cube length: " + cube.length);
	// // for (int i = 0; i < cube.length; i++)
	// // if (cube[i] == -1)
	// // cube[i] = 1;
	// cubeMaximals.add(position, cube);
	// }
	private ArrayList<byte[]> findOneMaxMaximal(ArrayList<byte[]> possibleInteractions, List<Integer> portBDDsPosition) {

		int size = possibleInteractions.size();
		int nbOnes = 0;
		int nbOnestmp = 0;

		ArrayList<byte[]> maxMaximals = new ArrayList<byte[]>();

		for (int i = 0; i < size; i++) {
			byte[] oneInteraction = possibleInteractions.get(i);
			for (int j = 0; j < portBDDsPosition.size(); j++) {
				if ((oneInteraction[portBDDsPosition.get(j)]) != 0) {
					nbOnestmp++;
				}
			}
			if (nbOnes < nbOnestmp) {
				maxMaximals.clear();
				maxMaximals.add(oneInteraction);
				nbOnes = nbOnestmp;
				nbOnestmp = 0;
			}
			if (nbOnes == nbOnestmp) {
				maxMaximals.add(oneInteraction);
			}
		}
		return maxMaximals;
	}

	private void findMaximals(ArrayList<byte[]> cubeMaximals, byte[] c_cube, List<Integer> portBDDsPosition) {
		int size = cubeMaximals.size();
		logger.trace("findMaximals size: " + size);

		for (int i = 0; i < size; i++) {
			int comparison = compareCube(c_cube, cubeMaximals.get(i), portBDDsPosition);
			if (comparison == 1 || comparison == 0) {
				cubeMaximals.remove(i);
				cubeMaximals.add(i, c_cube);
				// addCube(cubeMaximals, c_cube, i);
				return;
			}
			if (comparison == 3)
				return;
		}
		cubeMaximals.add(cubeMaximals.size(), c_cube);
		// addCube(cubeMaximals, c_cube, cubeMaximals.size());
	}

	public final BDD totalCurrentStateBdd(Hashtable<BIPComponent, BDD> currentStateBDDs) throws BIPEngineException {
		BDD totalCurrentStateBdd = bdd_mgr.one();
		BDD tmp;

		logger.trace("Conjunction of current states about to start..");
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
		logger.trace("Conjunction of current states has finished");
		return totalCurrentStateBdd;
	}

	public final BDD totalExtraBdd(ArrayList<BDD> disabledCombinationBDDs) throws BIPEngineException {
		BDD totalDisabledCombinationBdd = bdd_mgr.one();

		logger.trace("Conjunction of disabled combinations about to start..");
		for (BDD disabledCombinationBDD : disabledCombinationBDDs) {
			if (disabledCombinationBDD == null) {
				logger.error("Disabled Combination BDD is null");
				throw new BIPEngineException("Disabled Combination BDD is null");
			}
			totalDisabledCombinationBdd.andWith(disabledCombinationBDD);
		}
		logger.trace("Conjunction of disabled combinations has finished");
		return totalDisabledCombinationBdd;
	}

	public synchronized final void runOneIteration() throws BIPEngineException {

		byte[] chosenInteraction;

		// For performance info
		// long time = System.currentTimeMillis();

		BDD totalCurrentStateAndDisabledCombinations = totalCurrentStateBdd(currentStateBDDs);
		BDD solns = totalConstraints.and(totalCurrentStateAndDisabledCombinations);

		logger.trace("INFORM SPECIFIC CALL: Disabled Combinations size " + temporaryConstraints.size());

		/*
		 * Temporary Constraints cannot be null.
		 */
		if (!temporaryConstraints.isEmpty()) {
			solns.andWith(totalExtraBdd(temporaryConstraints));
		}

		/* Compute global BDD: solns= Λi Fi Λ G Λ (Λi Ci) */
		totalCurrentStateAndDisabledCombinations.free();
		// For performance and memory info
		// System.out.println("Number of nodes " + this.bdd_mgr.getNodeTableSize());
		// System.out.println("Number of all nodes: " + this.bdd_mgr.getNodeNum());
		// System.out.println("Max memory: " + Runtime.getRuntime().maxMemory());
		// System.out.println("Total memory: " + Runtime.getRuntime().totalMemory());
		// System.out.println("free Memory: " + Runtime.getRuntime().freeMemory());

		// System.gc();
		// System.gc();
		// System.gc();
		// System.gc();
		// System.gc();
		// System.gc();
		// System.gc();

		// long mem0 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		// System.out.println("Cache size " + this.bdd_mgr.getCacheSize());
		//
		// System.out.println("solns nodes: " + solns.nodeCount());

		// System.out.println(bdd_mgr.getClass().toString());
		// solns.free();
		// bdd_mgr.done();
		// THIS IS WHAT I NEED FOR EVALUATION OF NODES
		// System.out.println("Number of nodes " + this.bdd_mgr.getNodeTableSize());
		// System.out.println("Number of all nodes: " + this.bdd_mgr.getNodeNum());
		// System.out.println("Cache size " + this.bdd_mgr.getCacheSize());
		// System.out.println("Number of nodes " + this.bdd_mgr.getNodeTableSize());
		// System.out.println("Number of all nodes: " + this.bdd_mgr.getNodeNum());
		// bdd_mgr = null;

		// System.gc();
		// System.gc();
		// System.gc();
		// System.gc();
		// System.gc();
		// System.gc();
		// System.gc();
		// System.out.println("Max memory: " + Runtime.getRuntime().maxMemory());
		// System.out.println("Total memory: " + Runtime.getRuntime().totalMemory());
		// System.out.println("free Memory: " + Runtime.getRuntime().freeMemory());

		// System.out.println("Total mem: "
		// + (mem0 - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())));

		ArrayList<byte[]> possibleInteraction = new ArrayList<byte[]>();

		possibleInteraction.addAll(solns.allsat());
		// BigInteger[] oneSolution = solns.scanAllVar();

		logger.debug("******************************* Engine **********************************");
		logger.debug("Number of possible interactions is: {} " + possibleInteraction.size());
		// Iterator<byte[]> it = possibleInteraction.iterator();

		/* for debugging */
		// while (it.hasNext()) {
		// byte[] value = it.next();
		//
		// StringBuilder sb = new StringBuilder();
		// for (byte b : value) {
		// sb.append(String.format("%02X ", b));
		// }
		// logger.trace(sb.toString());
		// System.out.println("Engine: " + sb.toString());
		// }

		ArrayList<byte[]> cubeMaximals = new ArrayList<byte[]>();
		List<Integer> positionOfPorts = wrapper.getBehaviourEncoderInstance().getPositionsOfPorts();
		for (int i = 0; i < possibleInteraction.size(); i++) {
			logger.trace("Positions of D Variables size:" + positionsOfDVariables.size());
			findMaximals(cubeMaximals, possibleInteraction.get(i), positionOfPorts);
		}

		/* deadlock detection */
		int size = cubeMaximals.size();
		if (size == 0) {
			logger.error("Deadlock. No maximal interactions.");
			throw new BIPEngineException("Deadlock. No maximal interactions.");
		} else if (size == 1) {
			if (countPortEnable(cubeMaximals.get(0), (ArrayList<Integer>) wrapper.getBehaviourEncoderInstance()
					.getPositionsOfPorts()) == 0) {
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

		/*
		 * Beginning of the part to move to the Data Coordinator
		 */
		// For performance info

		wrapper.execute(chosenInteraction);
		// System.out.println((System.currentTimeMillis() - time));

		/*
		 * End of the part to move to the Data Coordinator
		 */
		solns.free();
		temporaryConstraints.clear();
		// For performance info
		// bdd_mgr.done();
		// System.out.println("Number of nodes " + this.bdd_mgr.getNodeTableSize());
		// bdd_mgr = null;
		// time = System.currentTimeMillis() - time;
		// System.out.println("Engine cycle time: "+ time);

	}

	public synchronized void informCurrentState(BIPComponent component, BDD componentBDD) {
		currentStateBDDs.put(component, componentBDD);
	}

	public synchronized void specifyTemporaryExtraConstraints(final BDD extraConstraint) {
		temporaryConstraints.add(extraConstraint);
		logger.trace("INFORM SPECIFIC CALL: Disabled Combinations size " + temporaryConstraints.size());
	}

	private synchronized void dataConstraintsComputation(Set<BDD> extraConstraints) {
		synchronized (this) {
			if (totalConstraints == null) {
				totalConstraints = bdd_mgr.one();
				for (BDD eachD : extraConstraints) {
					totalConstraints.andWith(eachD);
				}
				logger.trace("Extra permanent constraints added to empty total BDD.");
				bdd_mgr.reorder(BDDFactory.REORDER_SIFTITE);
				// System.out.println("EData: Reorder stats: " + bdd_mgr.getReorderStats());
			} else {

				for (BDD eachD : extraConstraints) {
					totalConstraints.andWith(eachD);
				}
				logger.trace("Extra permanent constraints added to existing total BDD.");
				bdd_mgr.reorder(BDDFactory.REORDER_SIFTITE);
				// System.out.println("EData: Reorder stats: " + bdd_mgr.getReorderStats());
			}
		}
	}

	public synchronized void specifyPermanentExtraConstraints(Set<BDD> extraConstraints) {
		this.permanentDataBDDs.addAll(extraConstraints);
	}

	public synchronized void informBehaviour(BIPComponent component, BDD componentBDD) {
		behaviourBDDs.put(component, componentBDD);
	}

	public synchronized final void totalBehaviourBDD() {
		logger.trace("Conjunction of behaviours has finished");
		/*
		 * Re-ordering function and statistics printouts. If data are used this the gain is relatively small 2-5% If no
		 * data are used the gain is more than 50% After total behaviour BDD is computed
		 */

		synchronized (this) {
			if (totalConstraints == null) {
				totalConstraints = bdd_mgr.one();
				for (Enumeration<BIPComponent> componentsEnum = behaviourBDDs.keys(); componentsEnum.hasMoreElements();) {

					logger.trace("Conjunction of behaviours about to start..");
					totalConstraints.andWith(behaviourBDDs.get(componentsEnum.nextElement()));

				}
				bdd_mgr.reorder(BDDFactory.REORDER_SIFTITE);
				// System.out.println("E5: Reorder stats: " + bdd_mgr.getReorderStats());
				logger.trace("E5: Reorder stats: " + bdd_mgr.getReorderStats());
				logger.trace("Behaviour constraints added to empty total BDD.");
			} else {
				for (Enumeration<BIPComponent> componentsEnum = behaviourBDDs.keys(); componentsEnum.hasMoreElements();) {

					logger.trace("Conjunction of behaviours about to start..");
					totalConstraints.andWith(behaviourBDDs.get(componentsEnum.nextElement()));
				}
				bdd_mgr.reorder(BDDFactory.REORDER_SIFTITE);
				// System.out.println("E7: Reorder stats: " + bdd_mgr.getReorderStats());
				logger.trace("E7: Reorder stats: " + bdd_mgr.getReorderStats());
				logger.trace("Behaviour constraints added to existing total BDD.");

			}
		}
	}

	public synchronized void informGlue(List<BDD> totalGlue) {
		synchronized (this) {
			if (totalConstraints == null) {

				totalConstraints = bdd_mgr.one();
				for (BDD glueBDD : totalGlue) {
					/*
					 * Re-ordering function and statistics printouts
					 */
					logger.trace("And with effect Instance");
					totalConstraints.andWith(glueBDD);
					logger.trace("Finish andwith effect Instance");
				}
				logger.trace("Glue constraints added to empty total BDD.");

				if (this.permanentDataBDDs.size() != 0) {
					dataConstraintsComputation(this.permanentDataBDDs);
				}
			} else {

				for (BDD glueBDD : totalGlue) {
					logger.trace("And with effect Instance");
					totalConstraints.andWith(glueBDD);
					logger.trace("Finish andwith effect Instance");

				}
				logger.trace("E9: Reorder stats: " + bdd_mgr.getReorderStats());
				// System.out.println("E9: Reorder stats: " + bdd_mgr.getReorderStats());
				bdd_mgr.reorder(BDDFactory.REORDER_SIFTITE);
				logger.info("Glue constraints added to existing total BDD.");
				if (this.permanentDataBDDs.size() != 0) {
					dataConstraintsComputation(this.permanentDataBDDs);
				}
			}
		}
	}

	public void setBIPCoordinator(BIPCoordinator wrapper) {
		this.wrapper = wrapper;
	}

	public synchronized BDDFactory getBDDManager() {
		return bdd_mgr;
	}

}
