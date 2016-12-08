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
//public class Enginetest5 {
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
//		behenc.Ports=3;
//		behenc.States=3;
//		behenc.Components=3;
//		
//		behenc.createBDDNodes();
//
//		assertEquals("Component 1 wrong number of port BDDs", 1, behenc.portBDDs.get(1).length);
//		assertEquals("Component 1 wrong number of state BDDs", 1, behenc.stateBDDs.get(1).length);
//		assertEquals("Component 2 wrong number of port BDDs", 1, behenc.portBDDs.get(2).length);
//		assertEquals("Component 2 wrong number of state BDDs", 1, behenc.stateBDDs.get(2).length);
//		assertEquals("Component 3 wrong number of port BDDs", 1, behenc.portBDDs.get(3).length);
//		assertEquals("Component 3 wrong number of state BDDs", 1, behenc.stateBDDs.get(3).length);
//
//		behenc.totalBehaviour();
//		
////		GlueBDD();
////		
////		currstenc.Components=3;
////		Currstateinform();
//		currstenc.informAllCurrentStateBDDs();
//		
//		engine.Ports=3;
//		engine.States=3;
//		engine.Components=3;
//		int [] Pos = new int [] {1,3,5}; 
//		//engine.PositionsOfPorts	= Pos;
//		
//		engine.run();
//		
//	}
//	
///** Auxiliary Methods */	
//	
////	void Currstateinform(){
////		CurrentStateEncoder currstenc1 = new CurrentStateEncoder ();
////		ArrayList <Integer> disabledPorts = new ArrayList<Integer>();
////		for (int i=1; i <=3; i++){
////			if (i==1) {
////				disabledPorts.clear();
////				//currstenc1.inform(i, 1, disabledPorts);
////				}
////			else if (i==2) {
////				disabledPorts.clear();
////				currstenc1.inform(i, 2, disabledPorts);
////			}
////			else {
////				disabledPorts.clear();
////				currstenc1.inform(i, 3, disabledPorts);
////			}
////				
////	 }	
////	}
////	
////	void GlueBDD(){
//////		Engine engine = new Engine();		 
//////		 
//////		BehaviourEncoder behenc = new BehaviourEncoder();
////		GlueEncoder glueenc = new GlueEncoder();
////		ArrayList<Integer> Reqcompo= new ArrayList<Integer> ();
////		ArrayList<Integer> Reqports = new ArrayList<Integer>();
////		//Component 1
////		Reqcompo.clear();
////		Reqcompo.add(3);
////		Reqports.clear();
////		Reqports.add(3);
////		glueenc.ComponentRequire(1, 1, Reqcompo, Reqports);
////		
////		//Component 2
////		Reqcompo.clear();
////		Reqcompo.add(3);
////		Reqports.clear();
////		Reqports.add(3);
////		glueenc.ComponentRequire(2, 2, Reqcompo, Reqports);
////		
////		//Component 1
////		Reqcompo.clear();
////		Reqcompo.add(3);
////		Reqports.clear();
////		Reqports.add(3);
////		glueenc.ComponentAccept(1, 1, Reqcompo, Reqports);
////		
////		//Component 2
////		Reqcompo.clear();
////		Reqcompo.add(3);
////		Reqports.clear();
////		Reqports.add(3);
////		glueenc.ComponentAccept(2, 2, Reqcompo, Reqports);
////		
////		glueenc.TotalGlue();
////		
//////		BDD Glue=(behenc.portBDDs.get(1)[0].not().and(behenc.portBDDs.get(2)[0].not())).or(behenc.portBDDs.get(1)[0].and(behenc.portBDDs.get(3)[0])).or(behenc.portBDDs.get(2)[0].and(behenc.portBDDs.get(3)[0]));
//////		engine.informGlue(Glue);
////				
////	}
////	
////	public int getPortsNumber(int compID) {
////		if (compID==1 || compID ==2) return 1;
////		else return 1;
////	}
////	public int getStatesNumber(int compID) {
////		return 1;
////	}
////	
////	public ArrayList<Integer> getStatetoPorts(int compID, int state){
////		ArrayList<Integer> stateToPorts = new ArrayList<Integer>();
////		    
////		if (compID==1) { 
////				stateToPorts.clear();
////				stateToPorts.add(1);
////				return(stateToPorts);
////			}
////		
////		else if (compID==2) { 
////			stateToPorts.clear();
////			stateToPorts.add(2);
////			return(stateToPorts);
////		}
////		
////		else{
////			stateToPorts.clear();
////			stateToPorts.add(3);
////			return(stateToPorts);
////		}
////	}
////	
////public ArrayList<Integer> getPorts(int compID){
////	ArrayList<Integer> enforceablePorts= new ArrayList<Integer>();
////	
////		if (compID==1) 
////		{	enforceablePorts.add(1);
////			return enforceablePorts;
////		}
////		if (compID ==2) 
////		{	enforceablePorts.clear();
////			enforceablePorts.add(2);
////			return enforceablePorts;
////		}
////		else
////		{
////			enforceablePorts.clear();
////			enforceablePorts.add(3);
////			return enforceablePorts;
////		}
////	}
////public ArrayList<Integer> getStates(int compID){
////	ArrayList<Integer> componentPorts = new ArrayList<Integer>();
////	
////	if (compID==1) 
////	{	componentPorts.add(1);
////		return componentPorts;
////	}
////	if (compID ==2) 
////	{	componentPorts.clear();
////		componentPorts.add(2);
////		return componentPorts;
////	}
////	else
////	{
////		componentPorts.clear();
////		componentPorts.add(3);
////		return componentPorts;
////	}
////}
////
//}