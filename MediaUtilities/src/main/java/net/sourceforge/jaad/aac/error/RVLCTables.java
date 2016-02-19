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
package net.sourceforge.jaad.aac.error;

interface RVLCTables {

	// index,length,codeword
	int[][] RVLC_BOOK = { { 0, 1, 0 }, /* 0 */
		{ -1, 3, 5 }, /* 101 */
		{ 1, 3, 7 }, /* 111 */
		{ -2, 4, 9 }, /* 1001 */
		{ -3, 5, 17 }, /* 10001 */
		{ 2, 5, 27 }, /* 11011 */
		{ -4, 6, 33 }, /* 100001 */
		{ 99, 6, 50 }, /* 110010 */
		{ 3, 6, 51 }, /* 110011 */
		{ 99, 6, 52 }, /* 110100 */
		{ -7, 7, 65 }, /* 1000001 */
		{ 99, 7, 96 }, /* 1100000 */
		{ 99, 7, 98 }, /* 1100010 */
		{ 7, 7, 99 }, /* 1100011 */
		{ 4, 7, 107 }, /* 1101011 */
		{ -5, 8, 129 }, /* 10000001 */
		{ 99, 8, 194 }, /* 11000010 */
		{ 5, 8, 195 }, /* 11000011 */
		{ 99, 8, 212 }, /* 11010100 */
		{ 99, 9, 256 }, /* 100000000 */
		{ -6, 9, 257 }, /* 100000001 */
		{ 99, 9, 426 }, /* 110101010 */
		{ 6, 9, 427 }, /* 110101011 */
		{ 99, 10, 0 }
	};
	int[][] ESCAPE_BOOK = {
		{1, 2, 0},
		{0, 2, 2},
		{3, 3, 2},
		{2, 3, 6},
		{4, 4, 14},
		{7, 5, 13},
		{6, 5, 15},
		{5, 5, 31},
		{11, 6, 24},
		{10, 6, 25},
		{9, 6, 29},
		{8, 6, 61},
		{13, 7, 56},
		{12, 7, 120},
		{15, 8, 114},
		{14, 8, 242},
		{17, 9, 230},
		{16, 9, 486},
		{19, 10, 463},
		{18, 10, 974},
		{22, 11, 925},
		{20, 11, 1950},
		{21, 11, 1951},
		{23, 12, 1848},
		{25, 13, 3698},
		{24, 14, 7399},
		{26, 15, 14797},
		{49, 19, 236736},
		{50, 19, 236737},
		{51, 19, 236738},
		{52, 19, 236739},
		{53, 19, 236740},
		{27, 20, 473482},
		{28, 20, 473483},
		{29, 20, 473484},
		{30, 20, 473485},
		{31, 20, 473486},
		{32, 20, 473487},
		{33, 20, 473488},
		{34, 20, 473489},
		{35, 20, 473490},
		{36, 20, 473491},
		{37, 20, 473492},
		{38, 20, 473493},
		{39, 20, 473494},
		{40, 20, 473495},
		{41, 20, 473496},
		{42, 20, 473497},
		{43, 20, 473498},
		{44, 20, 473499},
		{45, 20, 473500},
		{46, 20, 473501},
		{47, 20, 473502},
		{48, 20, 473503},
		{99, 21, 0}
	};
}
