package org.javabip.engine;
//package org.bip.engine;
//
//import static org.junit.Assert.*;
//
//
//import java.util.ArrayList;
//
//import net.sf.javabdd.BDD;
//
//import org.bip.engine.*;
//
//import org.junit.Test;
//
//public class Enginetest4 {
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
//		behenc.Ports=8;
//		behenc.States=5;
//		behenc.Components=3;
//		
//		behenc.createBDDNodes();
//
//		assertEquals("Component 1 wrong number of port BDDs", 3, behenc.portBDDs.get(1).length);
//		assertEquals("Component 1 wrong number of state BDDs", 2, behenc.stateBDDs.get(1).length);
//		assertEquals("Component 2 wrong number of port BDDs", 3, behenc.portBDDs.get(2).length);
//		assertEquals("Component 2 wrong number of state BDDs", 2, behenc.stateBDDs.get(2).length);
//		assertEquals("Component 3 wrong number of port BDDs", 2, behenc.portBDDs.get(3).length);
//		assertEquals("Component 3 wrong number of state BDDs", 1, behenc.stateBDDs.get(3).length);
//
//		behenc.totalBehaviour();
//		
//		GlueBDD();
//		
//		currstenc.Components=3;
//		Currstateinform();
//		currstenc.informAllCurrentStateBDDs();
//		
//		engine.Ports=8;
//		engine.States=5;
//		engine.Components=3;
//		int [] Pos = new int [] {2,3,4,7,8,9,11,12};
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
//		for (int i=1; i <=3; i++){
//			if (i==1) {
//				disabledPorts.clear();
////				currstenc1.inform(i, 2, disabledPorts);
//			}
//			else if (i==2) {
//				disabledPorts.clear();
//				//disabledPorts.add(5); 
////				currstenc1.inform(i, 4, disabledPorts);
////				currstenc1.inform(i, 4, disabledPorts);
//			}
//			else {
//				disabledPorts.clear(); 
////				currstenc1.inform(i, 5, disabledPorts);
//			}
//	 }	
//	}
//	
//	void GlueBDD(){
//		//Engine engine = new Engine();	
//		
//		GlueEncoder glueenc = new GlueEncoder();
//		ArrayList<Integer> Reqcompo= new ArrayList<Integer> ();
//		ArrayList<Integer> Reqports = new ArrayList<Integer>();
//		
//		//Components: 1,2,3
//		//Ports: 1,2,3  4,5,6  7,8
//		
//		//Require
//		//Component 1
//		Reqcompo.clear();
//		Reqcompo.add(3);
//		Reqports.clear();
//		Reqports.add(8);
////		glueenc.ComponentRequire(1, 1, Reqcompo, Reqports);
//		
//		//Component 1
//		Reqcompo.clear();
//		Reqcompo.add(3);
//		Reqports.clear();
//		Reqports.add(7);
//		//glueenc.ComponentRequire(1, 2, Reqcompo, Reqports);
//		
//		//Component 1
//		Reqcompo.clear();
//		Reqcompo.add(3);
//		Reqports.clear();
//		Reqports.add(7);
//		//glueenc.ComponentRequire(1, 3, Reqcompo, Reqports);
//		
//		//Component 2
//		Reqcompo.clear();
//		Reqcompo.add(3);
//		Reqports.clear();
//		Reqports.add(8);
//		//glueenc.ComponentRequire(2, 4, Reqcompo, Reqports);
//		
//		//Component 2
//		Reqcompo.clear();
//		Reqcompo.add(3);
//		Reqports.clear();
//		Reqports.add(7);
//		//glueenc.ComponentRequire(2, 5, Reqcompo, Reqports);
//		
//		//Component 2
//		Reqcompo.clear();
//		Reqcompo.add(3);
//		Reqports.clear();
//		Reqports.add(7);
//		//glueenc.ComponentRequire(2, 6, Reqcompo, Reqports);
//	
//		//Accept
//		//Component 1
//		Reqcompo.clear();
//		Reqcompo.add(3);
//		Reqports.clear();
//		Reqports.add(8);
//		//glueenc.ComponentAccept(1, 1, Reqcompo, Reqports);
//		
//		//Component 1
//		Reqcompo.clear();
//		Reqcompo.add(3);
//		Reqcompo.add(2);
//		Reqports.clear();
//		Reqports.add(7);
//		Reqports.add(6);
//		//glueenc.ComponentAccept(1, 2, Reqcompo, Reqports);
//		
//		//Component 1
//		Reqcompo.clear();
//		Reqcompo.add(3);
//		Reqcompo.add(2);
//		Reqports.clear();
//		Reqports.add(8);
//		Reqports.add(5);
//		//glueenc.ComponentAccept(1, 3, Reqcompo, Reqports);
//		
//		//Component 2
//		Reqcompo.clear();
//		Reqcompo.add(3);
//		Reqports.clear();
//		Reqports.add(8);
//		//glueenc.ComponentAccept(2, 4, Reqcompo, Reqports);
//		
//		//Component 2
//		Reqcompo.clear();
//		Reqcompo.add(3);
//		Reqcompo.add(2);
//		Reqports.clear();
//		Reqports.add(7);
//		Reqports.add(6);
//		//glueenc.ComponentAccept(2, 5, Reqcompo, Reqports);
//		
//		//Component 2
//		Reqcompo.clear();
//		Reqcompo.add(3);
//		Reqcompo.add(2);
//		Reqports.clear();
//		Reqports.add(7);
//		Reqports.add(5);
//		//glueenc.ComponentAccept(2, 6, Reqcompo, Reqports);
//
//		
//		glueenc.totalGlue();
//		 
////		BehaviourEncoder behenc = new BehaviourEncoder();
////		BDD c=behenc.portBDDs.get(3)[0];
////		BDD d1=behenc.portBDDs.get(1)[0];
////		BDD d2=behenc.portBDDs.get(2)[0];
////		BDD e=behenc.portBDDs.get(3)[1];		
////		BDD a1=behenc.portBDDs.get(1)[1];
////		BDD a2=behenc.portBDDs.get(2)[1];
////		BDD b1=behenc.portBDDs.get(1)[2];
////		BDD b2=behenc.portBDDs.get(2)[2];
////		
////		BDD Glue=(c.and(d1.not()).and(d2.not())).or(e.and(d1).and(d2.not())).or(e.and(d2).and(d1.not())).or(c.and(a1)).or(c.and(a2)).or(c.and(b1).and(b2.not())).or(c.and(b2).and(b1.not()));	 
////		
////		engine.informGlue(Glue);
//				
//	}
//	
//	public int getPortsNumber(int compID) {
//		if (compID==1 || compID ==2) return 3;
//		else return 2;
//	}
//	public int getStatesNumber(int compID) {
//		if (compID==1 || compID ==2) return 2;
//		else return 1;
//	}
//	
//	public ArrayList<Integer> getStatetoPorts(int compID, int state){
//		ArrayList<Integer> stateToPorts = new ArrayList<Integer>();
//		    
//		if (compID==1) { 
//			if (state==1) {
//				stateToPorts.clear();
//				stateToPorts.add(1);
//				return(stateToPorts);
//			}
//			else{
//				stateToPorts.clear();
//				stateToPorts.add(1);
//				stateToPorts.add(2);
//				stateToPorts.add(3);
//				return(stateToPorts);
//			}
//		}
//		
//		else if (compID ==2){
//			if (state==1){
//				stateToPorts.clear();
//				stateToPorts.add(4);
//				return (stateToPorts);
//			}
//			else {
//				stateToPorts.clear();
//				stateToPorts.add(4);
//				stateToPorts.add(5);
//				stateToPorts.add(6);
//				return (stateToPorts);
//			}
//		}
//		
//		else{
//			stateToPorts.clear();
//			stateToPorts.add(7);
//			stateToPorts.add(8);
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
//			enforceablePorts.add(3);
//			return enforceablePorts;
//		}
//		if (compID ==2) 
//		{	enforceablePorts.clear();
//			enforceablePorts.add(4);
//			enforceablePorts.add(5);
//			enforceablePorts.add(6);
//			return enforceablePorts;
//		}
//		else
//		{
//			enforceablePorts.clear();
//			enforceablePorts.add(7);
//			enforceablePorts.add(8);
//			return enforceablePorts;
//		}
//	}
//public ArrayList<Integer> getStates(int compID){
//	ArrayList<Integer> componentPorts = new ArrayList<Integer>();
//	
//	if (compID==1) 
//	{	componentPorts.add(1);
//		componentPorts.add(2);
//		return componentPorts;
//	}
//	if (compID ==2) 
//	{	componentPorts.clear();
//		componentPorts.add(3);
//		componentPorts.add(4);
//		return componentPorts;
//	}
//	else
//	{
//		componentPorts.clear();
//		componentPorts.add(5);
//		return componentPorts;
//	}
//}
//
//}
//
