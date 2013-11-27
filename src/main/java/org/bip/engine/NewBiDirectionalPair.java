package org.bip.engine;

import org.bip.api.Port;

public final class NewBiDirectionalPair {

		  private final Port a;
		  private final Port b;

		public NewBiDirectionalPair(Port a, Port b) {
		    this.a = a; 
		    this.b = b;
		  }

		  @Override 
		  public boolean equals(Object o) {
		    if(o == null || !(o instanceof NewBiDirectionalPair)) 
		      return false;

		    NewBiDirectionalPair that = (NewBiDirectionalPair) o;
		    return this.a.equals(that.a) && this.b.equals(that.b) 
		      || this.a.equals(that.b) && this.b.equals(that.a);
		  }

		  @Override 
		  public int hashCode() {
		    return a.hashCode() ^ b.hashCode();
		  }	
		  
		  public Port getFirst(){
			return a;
		  }
		  
		  public Port getSecond(){
			return b;
		  }
	
}
