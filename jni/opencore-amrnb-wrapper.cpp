/*
 *  Copyright (C) 2012 Simon Robinson
 * 
 *  This file is part of Com-Me.
 * 
 *  Com-Me is free software; you can redistribute it and/or modify it 
 *  under the terms of the GNU Lesser General Public License as 
 *  published by the Free Software Foundation; either version 3 of the 
 *  License, or (at your option) any later version.
 *
 *  Com-Me is distributed in the hope that it will be useful, but WITHOUT 
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General 
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with Com-Me.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

#include <jni.h>
#include <interf_dec.h>

extern "C" {
JNIEXPORT jint JNICALL Java_ac_robinson_mov_AMRtoPCMConverter_AmrDecoderInit(JNIEnv* env, jobject obj);
JNIEXPORT void JNICALL Java_ac_robinson_mov_AMRtoPCMConverter_AmrDecoderDecode(JNIEnv* env, jobject obj,
		jint* nativePointer, jbyteArray in, jshortArray out, jint bfi);
JNIEXPORT void JNICALL Java_ac_robinson_mov_AMRtoPCMConverter_AmrDecoderExit(JNIEnv* env, jobject obj,
		jint* nativePointer);
}

JNIEXPORT jint JNICALL Java_ac_robinson_mov_AMRtoPCMConverter_AmrDecoderInit(JNIEnv* env, jobject obj) {
	return (jint) Decoder_Interface_init();
}

JNIEXPORT void JNICALL Java_ac_robinson_mov_AMRtoPCMConverter_AmrDecoderDecode(JNIEnv* env, jobject obj,
		jint* nativePointer, jbyteArray in, jshortArray out, jint bfi) {
	jsize inLen = env->GetArrayLength(in);
	jbyte inBuf[inLen];
	env->GetByteArrayRegion(in, 0, inLen, inBuf);

	jsize outLen = env->GetArrayLength(out);
	short outBuf[outLen];

	Decoder_Interface_Decode(nativePointer, (const unsigned char*) inBuf, (short*) outBuf, bfi);

	// env->ReleaseByteArrayElements(in, inBuf, JNI_ABORT); // no need - GetByteArrayRegion handles this
	env->SetShortArrayRegion(out, 0, outLen, outBuf);
}

JNIEXPORT void JNICALL Java_ac_robinson_mov_AMRtoPCMConverter_AmrDecoderExit(JNIEnv* env, jobject obj,
		jint* nativePointer) {
	Decoder_Interface_exit(nativePointer);
}
