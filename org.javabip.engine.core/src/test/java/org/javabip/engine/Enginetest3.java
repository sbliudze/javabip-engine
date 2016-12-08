package org.javabip.engine;
//package org.bip.engine;
//
//import static org.junit.Assert.*;
//
//import java.util.ArrayList;
//
//import net.sf.javabdd.BDD;
//
//import org.bip.engine.*;
//
//import org.junit.Test;
//
//public class Enginetest3 {
//
//	
//	@Test
//	public void test() throws Exception{
//		
//		Engine engine = new Engine();
//		BehaviourEncoder behenc = new BehaviourEncoder();
//		CurrentStateEncoder currstenc = new CurrentStateEncoder ();
//
//		
//		behenc.Ports=5;
//		behenc.States=3;
//		behenc.Components=3;
//		
//		behenc.CreateBDDNodes();
//
//		assertEquals("Component 1 wrong number of port BDDs", 2, behenc.portBDDs.get(1).length);
//		assertEquals("Component 1 wrong number of state BDDs", 1, behenc.stateBDDs.get(1).length);
//		assertEquals("Component 2 wrong number of port BDDs", 2, behenc.portBDDs.get(2).length);
//		assertEquals("Component 2 wrong number of state BDDs", 1, behenc.stateBDDs.get(2).length);
//		assertEquals("Component 3 wrong number of port BDDs", 1, behenc.portBDDs.get(3).length);
//		assertEquals("Component 3 wrong number of state BDDs", 1, behenc.stateBDDs.get(3).length);
//
//		behenc.TotalBehaviour();
//		
//		GlueBDD();
//		
//		currstenc.Components=3;
//		Currstateinform();
//		currstenc.TotalCurrentStateBDDs();
//		
//		engine.Ports=5;
//		engine.States=3;
//		engine.Components=3;
//		int [] Pos = new int [] {1,2,4,5,7}; 
//		//engine.PositionsOfPorts	= Pos;
//		
//		engine.run();
//		
//	}
//	
///** Auxiliary Methods */	
//	
//	void Currstateinform(){
//		CurrentStateEncoder currstenc1 = new CurrentStateEncoder ();
//		ArrayList <Integer> disabledPorts = new ArrayList<Integer>();
//		disabledPorts.add(0);
//		for (int i=1; i <=3; i++){
////			if (i==1) currstenc1.inform(i, 1, disabledPorts);
////			else if (i==2) currstenc1.inform(i, 2, disabledPorts);
////			else currstenc1.inform(i, 3, disabledPorts);
//	 }	
//	}
//	
//	void GlueBDD(){
//		Engine engine = new Engine();		 
//		 
//		BehaviourEncoder behenc = new BehaviourEncoder();
//		BDD tmp=Engine.bdd_mgr.zero();
//		BDD c=behenc.portBDDs.get(3)[0];
//		BDD tmp2=tmp.or(c);
//		
//		BDD a1=behenc.portBDDs.get(1)[0];
//		BDD ca1=a1.and(c);
//		BDD tmp3=tmp2.or(ca1);
//		
//		BDD a2=behenc.portBDDs.get(2)[0];
//		BDD ca2=a2.and(c);
//		BDD Glue=tmp3.or(ca2);
//		
//		BDD b1=behenc.portBDDs.get(1)[1];
//		BDD cb1=b1.and(c);
//		Glue.orWith(cb1);
//		BDD ca2b1=ca2.and(b1);
//		Glue.orWith(ca2b1);
//		
//		BDD b2=behenc.portBDDs.get(2)[1];
//		BDD cb2=b2.and(c);
//		Glue.orWith(cb2);
//		BDD ca1b2=ca1.and(b2);
//		Glue.orWith(ca1b2);
//		
//		engine.informGlue(Glue);
//				
//	}
//	
//	public int getPortsNumber(int compID) {
//		if (compID==1 || compID ==2) return 2;
//		else return 1;
//	}
//	public int getStatesNumber(int compID) {
//		return 1;
//	}
//	
//	public ArrayList<Integer> getStatetoPorts(int compID, int state){
//		ArrayList<Integer> stateToPorts = new ArrayList<Integer>();
//		    
//		if (compID==1) { 
//				stateToPorts.clear();
//				stateToPorts.add(1);
//				stateToPorts.add(2);
//				return(stateToPorts);
//			}
//		
//		else if (compID==2) { 
//			stateToPorts.clear();
//			stateToPorts.add(3);
//			stateToPorts.add(4);
//			return(stateToPorts);
//		}
//		
//		else{
//			stateToPorts.clear();
//			stateToPorts.add(5);
//			return(stateToPorts);
//		}
//	}
//	
//public ArrayList<Integer> getPorts(int compID){
//	ArrayList<Integer> enforceablePorts= new ArrayList<Integer>();
//	
//		if (compID==1) 
//		{	enforceablePorts.add(1);
//			enforceablePorts.add(2);
//			return enforceablePorts;
//		}
//		if (compID ==2) 
//		{	enforceablePorts.clear();
//			enforceablePorts.add(3);
//			enforceablePorts.add(4);
//			return enforceablePorts;
//		}
//		else
//		{
//			enforceablePorts.clear();
//			enforceablePorts.add(5);
//			return enforceablePorts;
//		}
//	}
//public ArrayList<Integer> getStates(int compID){
//	ArrayList<Integer> componentPorts = new ArrayList<Integer>();
//	
//	if (compID==1) 
//	{	componentPorts.add(1);
//		return componentPorts;
//	}
//	if (compID ==2) 
//	{	componentPorts.clear();
//		componentPorts.add(2);
//		return componentPorts;
//	}
//	else
//	{
//		componentPorts.clear();
//		componentPorts.add(3);
//		return componentPorts;
//	}
//}
//
//}