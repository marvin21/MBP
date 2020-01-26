package org.citopt.connde.service.receiver;

import org.springframework.stereotype.Component;

@Component
public class NoiseComponent {

	public double anonymiseLightValue(double value) {
		return value+10;
	}

	public double anonymiseDistanceValue(double value) {
		return ((Math.random() * ((10 - 5) + 1)) + 5) + value;
	}
}
