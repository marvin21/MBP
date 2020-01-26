package org.citopt.connde.service.receiver;

import org.springframework.stereotype.Component;

public class NoiseComponent {

	public double anonymiseLightValue(double value) {
		return value+10;
	}

	private void anonymiseGpsValues(int latitude, int longitude) {

	}
}
