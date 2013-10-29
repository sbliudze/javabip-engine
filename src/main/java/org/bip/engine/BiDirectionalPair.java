package org.bip.engine;

public final class BiDirectionalPair {

		  private final Object a;
		  private final Object b;

		public BiDirectionalPair(Object a, Object b) {
		    this.a = a; 
		    this.b = b;
		  }

		  @Override 
		  public boolean equals(Object o) {
		    if(o == null || !(o instanceof BiDirectionalPair)) 
		      return false;

		    BiDirectionalPair that = (BiDirectionalPair) o;
		    return this.a.equals(that.a) && this.b.equals(that.b) 
		      || this.a.equals(that.b) && this.b.equals(that.a);
		  }

		  @Override 
		  public int hashCode() {
		    return a.hashCode() ^ b.hashCode();
		  }	
		  
		  public Object getFirst(){
			return a;
		  }
		  
		  public Object getSecond(){
			return b;
		  }
	
}
