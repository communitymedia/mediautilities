/*
 *  Copyright (C) 2011 in-somnia
 * 
 *  This file is part of JAAD.
 * 
 *  JAAD is free software; you can redistribute it and/or modify it 
 *  under the terms of the GNU Lesser General Public License as 
 *  published by the Free Software Foundation; either version 3 of the 
 *  License, or (at your option) any later version.
 *
 *  JAAD is distributed in the hope that it will be useful, but WITHOUT 
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General 
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library.
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sourceforge.jaad.aac.sbr2;

class SynthesisFilterbank implements SBRConstants, FilterbankTables {

	private final float[][][] COEFS;
	private final float[][] V;
	private final float[] g, w;

	SynthesisFilterbank() {
		V = new float[2][1280]; // for both channels
		g = new float[640]; // tmp buffer
		w = new float[640];

		// complex coefficients:
		COEFS = new float[128][64][2];
		final double fac = 1.0f / 64.0f;
		double tmp;
		for (int n = 0; n < 128; n++) {
			for (int k = 0; k < 64; k++) {
				tmp = Math.PI / 128 * (k + 0.5) * (2 * n - 255);
				COEFS[n][k][0] = (float) (fac * Math.cos(tmp));
				COEFS[n][k][1] = (float) (fac * Math.sin(tmp));
			}
		}
	}

	// in: 64 x 32 complex, out: 2048 time samples
	public void process(float[][][] in, float[] out, int ch) {
		final float[] v = V[ch];
		int n, k, outOff = 0;

		// each loop creates 64 output samples
		for (int l = 0; l < TIME_SLOTS_RATE; l++) {
			// 1. shift buffer
			for (n = 1279; n >= 128; n--) {
				v[n] = v[n - 128];
			}

			// 2. multiple input by matrix and save in buffer
			for (n = 0; n < 128; n++) {
				v[n] = (in[0][l][0] * COEFS[n][0][0]) - (in[0][l][1] * COEFS[n][0][1]);
				for (k = 1; k < 64; k++) {
					v[n] += (in[k][l][0] * COEFS[n][k][0]) - (in[k][l][1] * COEFS[n][k][1]);
				}
			}

			// 3. extract samples
			for (n = 0; n < 5; n++) {
				for (k = 0; k < 64; k++) {
					g[128 * n + k] = v[256 * n + k];
					g[128 * n + 64 + k] = v[256 * n + 192 + k];
				}
			}

			// 4. window signal
			for (n = 0; n < 640; n++) {
				w[n] = (float) (g[n] * WINDOW[n]);
			}

			// 5. calculate output samples
			for (int i = 0; i < 64; i++) {
				out[outOff] = w[i];
				for (int j = 1; j < 10; j++) {
					out[outOff] = out[outOff] + w[64 * j + i];
				}
				outOff++;
			}
		}
	}
}
