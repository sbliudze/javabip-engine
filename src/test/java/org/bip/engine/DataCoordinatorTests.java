package org.bip.engine;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;

import org.bip.api.BIPComponent;
import org.bip.api.ExecutableBehaviour;
import org.bip.behaviour.BehaviourBuilder;
import org.bip.behaviour.ExecutorTransition;
import org.bip.behaviour.Guard;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPException;
import org.junit.Ignore;
import org.junit.Test;

public class DataCoordinatorTests implements BIPComponent {

	private Port firstPort = new Port("first", this.getClass());
	private Port secondPort = new Port("second", this.getClass());
	private Port thirdPort = new Port("third", this.getClass());

	private BehaviourBuilder createBehaviour() throws NoSuchMethodException, SecurityException {
		String type = "behaviourComponent";
		String currentState = "initial";
		ArrayList<ExecutorTransition> allTransitions = new ArrayList<ExecutorTransition>();
		ArrayList<Port> allPorts = new ArrayList<Port>();
		ArrayList<String> states = new ArrayList<String>();
		ArrayList<Guard> guards = new ArrayList<Guard>();
		guards.add(new Guard("f", this.getClass().getMethod("f"), new ArrayList<String>(Arrays.asList("Buenos Aires", "Córdoba", "La Plata"))));
		guards.add(new Guard("g", this.getClass().getMethod("g"), new ArrayList<String>(Arrays.asList("delta", "alfa"))));
		guards.add(new Guard("h", this.getClass().getMethod("h"), new ArrayList<String>(Arrays.asList("c", "b", "a"))));
		allTransitions.add(new ExecutorTransition("first", "initial", "1", "f&g&!h", null));
		allTransitions.add(new ExecutorTransition("second", "initial", "2", "(f|g)&!h", null));
		allTransitions.add(new ExecutorTransition("third", "initial", "3", "(f|g)&h", null));
		states.add("1");
		states.add("3");
		states.add("2");
		states.add("initial");
		allPorts.add(firstPort);
		allPorts.add(secondPort);
		allPorts.add(thirdPort);
		return new BehaviourBuilder(type, currentState, allTransitions, allPorts, states, guards, this);
	}

	@Test
    @Ignore
	public void testUndecidedPorts() throws BIPException, NoSuchMethodException, SecurityException {
		ExecutableBehaviour behaviour = createBehaviour().build();
		DataCoordinatorImpl coordinator = new DataCoordinatorImpl();
		// final OSGiExecutor executor1 = new OSGiExecutor(this, false);
		coordinator.register(this, behaviour);
		//assertEquals(1,coordinator.getUndecidedPorts(this, new ArrayList<Port>(Arrays.asList(firstPort, thirdPort))).size());
		//assertEquals(0,coordinator.getUndecidedPorts(this, new ArrayList<Port>(Arrays.asList(firstPort, secondPort, thirdPort))).size());
		//in order to run this test make the tested function public
	}
	
	@Test
	@Ignore
	public void testDataTransformation() {
		Hashtable<String, ArrayList<Object>> dataEvaluation = new Hashtable<String, ArrayList<Object>>();
		ArrayList<Object> array1 = new ArrayList<Object>(Arrays.asList("Buenos Aires", "Córdoba", "La Plata"));
		ArrayList<Object> array2 = new ArrayList<Object>(Arrays.asList(1, 2, 4, 2));
		ArrayList<Object> array3 = new ArrayList<Object>(Arrays.asList(true, false, true));
		dataEvaluation.put("town", array1);
		dataEvaluation.put("count", array2);
		dataEvaluation.put("isGood", array3);
		DataCoordinatorImpl coordinator = new DataCoordinatorImpl();
		ArrayList<Map<String, Object>> result = new ArrayList<Map<String,Object>>();
		//result = (ArrayList<Map<String, Object>>) coordinator.getDataValueTable(dataEvaluation);
		
		//assertEquals(36, result.size());
		//in order to run this test make the tested function public
		
		for (Map<String, Object> map : result) {
			for (Entry<String, Object> entry : map.entrySet()) {
				System.out.println(entry.getKey() + "-" + entry.getValue());
			}
			System.out.println();
		}
	}
	
	@Override
	public String getName() {
		return "DataTest";
	}

	@Override
	public void setName(String uniqueName) {
	}

	@Override
	public void execute(String portID) {
	}

	@Override
	public void inform(String portID) {
	}

	@Override
	public <T> T getData(String name, Class<T> clazz) {
		return null;
	}

	public boolean f() {
		return true;
	}

	public boolean g() {
		return false;
	}

	public boolean h() {
		return false;
	}

	@Override
	public Iterable<Boolean> checkEnabledness(Port port, Iterable<Map<String, Object>> data) {
		// TODO Auto-generated method stub
		return null;
	}
}
