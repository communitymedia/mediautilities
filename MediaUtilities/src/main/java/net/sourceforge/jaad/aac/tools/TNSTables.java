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
package net.sourceforge.jaad.aac.tools;

/**
 * Tables of coefficients used for TNS. The suffix indicates the values of coefCompress and coefRes.
 * 
 * @author in-somnia
 */
interface TNSTables {

	float[] TNS_COEF_1_3 = {
		0.00000000f, -0.43388373f, 0.64278758f, 0.34202015f,};
	float[] TNS_COEF_0_3 = {
		0.00000000f, -0.43388373f, -0.78183150f, -0.97492790f,
		0.98480773f, 0.86602539f, 0.64278758f, 0.34202015f,};
	float[] TNS_COEF_1_4 = {
		0.00000000f, -0.20791170f, -0.40673664f, -0.58778524f,
		0.67369562f, 0.52643216f, 0.36124167f, 0.18374951f,};
	float[] TNS_COEF_0_4 = {
		0.00000000f, -0.20791170f, -0.40673664f, -0.58778524f,
		-0.74314481f, -0.86602539f, -0.95105654f, -0.99452192f,
		0.99573416f, 0.96182561f, 0.89516330f, 0.79801720f,
		0.67369562f, 0.52643216f, 0.36124167f, 0.18374951f,};
	float[][] TNS_TABLES = {
		TNS_COEF_0_3, TNS_COEF_0_4, TNS_COEF_1_3, TNS_COEF_1_4
	};
}
