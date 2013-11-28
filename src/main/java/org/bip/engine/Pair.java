package org.bip.engine;

import org.bip.api.Port;

/*
 * Only for Port Objects.
 * 
 * Non-ordered pair. In Java I can only find Set as non-ordered. 
 * To retrieve elements from a set, an iterator has to be created each time.
 */
//
//public final class Pair {
//
//		  private final Port a;
//		  private final Port b;
//
//		public Pair(Port a, Port b) {
//		    this.a = a; 
//		    this.b = b;
//		  }
//
//		  @Override 
//		  public boolean equals(Object o) {
//		    if(o == null || !(o instanceof Pair)) 
//		      return false;
//
//		    Pair that = (Pair) o;
//		    return this.a.equals(that.a) && this.b.equals(that.b)
//		      || this.a.equals(that.b) && this.b.equals(that.a);
//		  }
//
//		  @Override 
//		  public int hashCode() {
//		    return a.hashCode() ^ b.hashCode();
//		  }	
//	  
//		  public Port getFirst(){
//			return a;
//		  }
//		  
//		  public Port getSecond(){
//			return b;
//		  }
//	
//}
