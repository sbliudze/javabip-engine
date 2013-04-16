package org.bip.engine;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;
import org.bip.behaviour.Port;

//TODO: add Exceptions
//TODO: assert?
/** Coordinates the execution of ports */
public class BDDBIPEngineImpl implements BDDBIPEngine {

	// TODO: start IDs from 0, and change for loops to start from 0
	private Hashtable<Integer, BDD> currentStateBDDs = new Hashtable<Integer, BDD>();

	/** BDD for ΛFi */
	BDD totalBehaviour;
	/** BDD for Glue */
	BDD totalGlue;
	/** JavaBDD Bdd Manager */
	public BDDFactory bdd_mgr; // TODO, have a get function in the IF

	OSGiBIPEngine wrapper;

	public void setOSGiBIPEngine(OSGiBIPEngine wrapper) {
		this.wrapper = wrapper;
	}

	/** Current-State BDDs */
	public synchronized void informCurrentState(BIPComponent component, BDD componentBDD) {
		Integer id = wrapper.reversedIdentityMapping.get(component);
		currentStateBDDs.put(id, componentBDD);
	}

	public void informTotalBehaviour(BDD totalBehaviour) {
		this.totalBehaviour = totalBehaviour;// BDD for ΛFi
	}

	public void informGlue(BDD totalGlue) {
		this.totalGlue = totalGlue;
	}

	/** Counts the number of enabled ports in the Maximal cube chosen */
	private int countPortEnable(byte[] in_cube, ArrayList<Integer> pp) {
		int out_count = 0;
		int i = 0;
		while (i < pp.size()) {
			if (in_cube[pp.get(i)] == 1 || in_cube[pp.get(i)] == -1)
				out_count++;
			i++;
		}
		return out_count;
	}

	/**
	 * @return 0 equal <br>
	 *         1 if cube2 in cube1, cube1 bigger <br>
	 *         2 not comparable 3 if cube1 in cube2, cube2 bigger <br>
	 */
	private int compareCube(byte[] cube1, byte[] cube2, ArrayList<Integer> pp) {
		boolean cube1_big = false;
		boolean cube2_big = false;
		int i = 0;

		while (i < pp.size()) {
			if ((cube1[pp.get(i)] == 1 || cube1[pp.get(i)] == -1) && (cube2[pp.get(i)] == 0)) {
				cube1_big = true;
			} else if ((cube2[pp.get(i)] != 0 || cube2[pp.get(i)] == -1) && (cube1[pp.get(i)] == 0)) {
				cube2_big = true;
			}
			i++;
		}
		/** if cube1 is bigger than cube2 (cube1 contains cube2) */
		if (cube1_big && !cube2_big)
			return 1;
		/** if cubes are not comparable */
		else if (cube1_big && cube2_big)
			return 2;
		/** if cube1 is smaller than cube2 ( cube2 contains cube1) */
		else if (!cube1_big && cube2_big)
			return 3;
		/** if cubes are equal */
		else
			return 0;
	}

	/** Copy maximal cube */
	private void addCube(ArrayList<byte[]> cubeMaximals, byte[] cube, int position) {
		for (int p = 0; p < cube.length; p++)
			if (cube[p] == -1)
				cube[p] = 1;
		cubeMaximals.add(position, cube);
	}

	private void findMaximals(ArrayList<byte[]> cubeMaximals, byte[] c_cube, ArrayList<Integer> pp) {
		int size = cubeMaximals.size();
		for (int i = 0; i < size; i++) {
			int comparison = compareCube(c_cube, cubeMaximals.get(i), pp);
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

	public final BDD totalCurrentStateBdd(Hashtable<Integer, BDD> currentStateBDDs) {

		BDD behavBdd = bdd_mgr.one();
		BDD tmp;
		for (int k = 1; k <= wrapper.noComponents; k++) {
			tmp = behavBdd.and(currentStateBDDs.get(k));
			behavBdd.free();
			behavBdd = tmp;
		}
		return behavBdd;
	}

	public final void runOneIteration() {
		byte[] chosenInteraction;
		ArrayList<byte[]> cubeMaximals = new ArrayList<byte[]>();
		int noEnabledPorts = 0;
		Hashtable<BIPComponent, ArrayList<Port>> chosenPorts = new Hashtable<BIPComponent, ArrayList<Port>>();
		ArrayList<BIPComponent> chosenComponents = new ArrayList<BIPComponent>();
		byte[] cubeMaximal = new byte[wrapper.getNoPorts() + wrapper.getNoStates()];

		/** initialize maximal cube */
		// for (int z = 0; z < wrapper.getNoPorts() + wrapper.getNoStates();
		// z++)
		// cubeMaximal[z] = 0;
		cubeMaximals.add(0, cubeMaximal);

		/** Λi Ci */
		BDD totalCurrentState = totalCurrentStateBdd(currentStateBDDs);

		/** Compute global BDD: solns= Λi Fi Λ G Λ (Λi Ci) */
		BDD solns = totalBehaviour.and(totalGlue).and(totalCurrentState);
		// TODO: compute the conjunction of totalBehaviour and totalGlue and
		// store it in a global and then andwith totalCurrentState
		totalCurrentState.free();
		// chosenPorts.clear();
		// chosenComponents.clear();
		ArrayList<byte[]> a = new ArrayList<byte[]>();

		a.addAll(solns.allsat()); // BIG TODO, can we find random maximal
									// interaction without getting all solutions
									// at once
		// TODO: check for no solution

		System.out.println("********************************* Engine *************************************");
		System.out.println("Number of possible interactions is: " + a.size());
		Iterator<byte[]> it = a.iterator();

		// for debugging
		while (it.hasNext()) {
			byte[] value = it.next();

			StringBuilder sb = new StringBuilder();
			for (byte b : value) {
				sb.append(String.format("%02X ", b));
			}
			System.out.println(sb.toString());
		}

		for (int k = 0; k < a.size(); k++)
			findMaximals(cubeMaximals, a.get(k), wrapper.positionsOfPorts);

		/** deadlock detection */
		noEnabledPorts = countPortEnable(cubeMaximals.get(0), wrapper.positionsOfPorts);
		int size = cubeMaximals.size();
		if (size == 0) {
			System.out.println("scheduler : deadlock");
			System.exit(1); // TODO, do not exit the JVM just exit the thread
		} else if (size == 1) {
			if (noEnabledPorts == 0) {
				System.out.println("scheduler : deadlock");
				System.exit(0); // TODO, do not exit the JVM just exit the
								// thread
			}
		}

		System.out.println("Number of maximal interactions: " + cubeMaximals.size());
		Random rand = new Random();
		int randomInt = rand.nextInt(cubeMaximals.size()); // pick a random
															// maximal
															// interaction
		chosenInteraction = cubeMaximals.get(randomInt); // update chosen
															// interaction
		cubeMaximals.clear();
		System.out.println("ChosenInteraction: ");
		for (int k = 0; k < chosenInteraction.length; k++)
			System.out.print(+chosenInteraction[k]);
		System.out.println();

		int offset = 0;

		for (int i = 1; i <= wrapper.noComponents; i++) {
			int portsize = wrapper.behaviourMapping.get(i).getEnforceablePorts().size();
			BIPComponent component = wrapper.identityMapping.get(i);
			ArrayList<Port> enabledPorts = new ArrayList<Port>();
			for (int l = 0; l < portsize; l++) {
				if (chosenInteraction[wrapper.positionsOfPorts.get(l + offset)] == 1) {
					enabledPorts.add(wrapper.behaviourMapping.get(i).getEnforceablePorts().get(l));
				}
			}
			if (!enabledPorts.isEmpty()) {
				System.out.println("Chosen Component: " + wrapper.behaviourMapping.get(i).getComponentType() + i + " Chosen Port: " + enabledPorts.get(0).id);
			}
			chosenPorts.put(component, enabledPorts);
			chosenComponents.add(component);
			offset = offset + portsize;

		}

		System.out.println("*****************************************************************************");

		wrapper.execute(chosenComponents, chosenPorts);

		solns.free();
		// cubeMaximals.clear();

		// for(int j = 0; j < Components; j++)
		// {
		// //clear all local bdds
		// CurrentStateBDDs.clear();
		// }
	}

}