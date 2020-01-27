package org.citopt.connde.service.receiver;

import org.springframework.stereotype.Component;

@Component
public class NoiseComponent {

	private final int min = 10;
	private final int max = 25;

	public double anonymiseLightValue(double value) {
		return value+10;
	}

	public double anonymiseDistanceValue(double value) {
		value = value + (Math.random() * min + max);
		return value;
	}
}
