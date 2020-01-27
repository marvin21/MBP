package org.citopt.connde.service.receiver;

public class NoiseComponent {

	private final int min = 10;
	private final int max = 25;
	private double value;

	public NoiseComponent(double value) {
		this.value = value;
	}

	public double anonymiseLightValue() {
		return value+10;
	}

	public double anonymiseDistanceValue() {
		value = value + (Math.random() * min + max);
		return value;
	}
}
