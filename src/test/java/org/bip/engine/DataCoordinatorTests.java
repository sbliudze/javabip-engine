package org.bip.engine;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
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
		//guards.add(new Guard("f", this.getClass().getMethod("f"), new ArrayList<String>(Arrays.asList("Buenos Aires", "Córdoba", "La Plata"))));
		//guards.add(new Guard("g", this.getClass().getMethod("g"), new ArrayList<String>(Arrays.asList("delta", "alfa"))));
		//guards.add(new Guard("h", this.getClass().getMethod("h"), new ArrayList<String>(Arrays.asList("c", "b", "a"))));
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
		return null;
	}

	@Override
	public void execute(String portID, Map<String, ?> data) {
	}
	
	
	
	private ArrayList<ArrayList<String>> getDataValueTable(ArrayList<String> dataList) throws InterruptedException {
		ArrayList<ArrayList<String>> result = new ArrayList<ArrayList<String>>();
		
		if (dataList == null || dataList.isEmpty()) {
			// throw exception
			System.out.println("null");
			return null;
		}
		ArrayList<ArrayList<String>> sortedList = getListList(dataList);
		
		// for one bipData get iterator over its values
		ArrayList<String> entry = sortedList.get(0);
		System.out.println("first entry "+entry);
		Iterator<String> iterator = entry.iterator();

		// for each value of this first bipData
		while (iterator.hasNext()) {
			// create one map, where
			// all the different pairs name<->value will be stored
			// put there the current value of the first bipData
			ArrayList<String> dataRow = new ArrayList<String>();
			String copy = iterator.next();
			dataRow.add(copy);
			
			System.out.println("first dataRow "+dataRow);
			// remove the current data from the initial data table
			// so that it is not treated again further
			// treat the other bipData variables
			result.addAll(getNextTableRow(sortedList, dataRow));
			// restore the current data
			//dataEvaluation.put(keyCopy, valuesCopy);
		}
		return result;
}
	
	private ArrayList<ArrayList<String>> getNextTableRow(ArrayList<ArrayList<String>> sortedList, ArrayList<String> dataRow) {
		ArrayList<ArrayList<String>> result = new ArrayList<ArrayList<String>>();
		// if there is no more data left, it means we have constructed one map
		// of all the bipData variables
		if (sortedList == null || sortedList.isEmpty()) {
			result.add(dataRow);
			return result;
		}

		// for one bipData get iterator over its values
		ArrayList<String> entry = sortedList.iterator().next();
		Iterator<String> iterator = entry.iterator();

		// for each value of this bipData
		while (iterator.hasNext()) {
			// create a new map, where
			// all the different pairs name<->value will be stored
			// copy there all the previous values
			// (this must be done to escape
			// change of one variable that leads to change of all its copies
			ArrayList<String> thisRow = new ArrayList<String>();
			thisRow.addAll(dataRow);
			// put there the current value of the bipData
			thisRow.add(iterator.next());

			// remove the current data from the initial data table
			// so that it is not treated again further
			//String keyCopy = entry.getKey();
			//ArrayList<Object> valuesCopy = dataEvaluation.remove(keyCopy);
			// treat the other bipData variables
			result.addAll(getNextTableRow(sortedList, thisRow));
			// restore the current data
			//dataEvaluation.put(keyCopy, valuesCopy);
		}
		return result;
	}

	private ArrayList<ArrayList<String>> getListList(ArrayList<String> list) throws InterruptedException {
		ArrayList<ArrayList<String>> result = new ArrayList<ArrayList<String>>();

		while (!list.isEmpty()) {
			ArrayList<String> oneDataList = new ArrayList<String>();

			String data = list.get(0);
			oneDataList.add(data);
			list.remove(data);

			for (String d : list) {
				if (d.equals(data)) {
					oneDataList.add(d);
				}
			}
			list.removeAll(oneDataList);
			result.add(oneDataList);
		}
		return result;
	}

	@Test
	public void testListsConstruction() throws InterruptedException
	{
		ArrayList<String> dataList = new ArrayList<String>();
		dataList.add("a"); dataList.add("a"); dataList.add("b"); dataList.add("b"); dataList.add("c");		dataList.add("a");
		//ArrayList<ArrayList<String>> list =  getListList(dataList);
		//System.out.println(list);
		//assertEquals(3, list.size());
		ArrayList<ArrayList<String>> list = getDataValueTable(dataList);
		assertEquals(6, list.size());
	}
}
