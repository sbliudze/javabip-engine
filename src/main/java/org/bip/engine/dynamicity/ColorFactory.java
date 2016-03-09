package org.bip.engine.dynamicity;

class ColorFactory {
	private static int requirementColor = 0;
	private static int solutionColor = 1;

	static Color getRequirementColor() {
		return new RequirementColor(requirementColor++);
	}

	static Color getUnconditionalSolutionColor() {
		return new SolutionColor(0);
	}

	static Color getSolutionColor() {
		return new SolutionColor(solutionColor++);
	}
}

abstract class Color {
	private int color;

	Color(int color) {
		this.color = color;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + color;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Color other = (Color) obj;
		if (color != other.color)
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return color + "";
	}
}

class RequirementColor extends Color {

	RequirementColor(int color) {
		super(color);
	}
}

class SolutionColor extends Color {

	SolutionColor(int color) {
		super(color);
	}
}