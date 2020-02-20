/*
 *  Copyright (C) 2020 Simon Robinson
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

package ac.robinson.mp4;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.os.Build;
import android.view.Surface;

import androidx.annotation.RequiresApi;

/**
 * Holds state associated with a Surface used for MediaCodec encoder input.
 * <p>
 * The constructor takes a Surface obtained from MediaCodec.createInputSurface(), and uses that to create an EGL window surface.
 * Calls to eglSwapBuffers() cause a frame of data to be sent to the video encoder.
 * <p>
 * This object owns the Surface -- releasing this will release the Surface too.
 * <p>
 * Source: https://www.bigflake.com/mediacodec/
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MP4CodecInputSurface {
	private static final int EGL_RECORDABLE_ANDROID = 0x3142;

	private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
	private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
	private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
	private Surface mSurface;

	/**
	 * Creates a MP4CodecInputSurface from a Surface.
	 */
	MP4CodecInputSurface(Surface surface) {
		if (surface == null) {
			throw new NullPointerException();
		}
		mSurface = surface;

		eglSetup();
	}

	/**
	 * Prepares EGL. We want a GLES 2.0 context and a surface that supports recording.
	 */
	private void eglSetup() {
		mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
		if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
			throw new RuntimeException("Unable to get EGL14 display");
		}
		int[] version = new int[2];
		if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
			throw new RuntimeException("Unable to initialize EGL14");
		}

		// configure EGL for recording and OpenGL ES 2.0
		// @formatter:off
		int[] attribList = {
				EGL14.EGL_RED_SIZE, 8,
				EGL14.EGL_GREEN_SIZE, 8,
				EGL14.EGL_BLUE_SIZE, 8,
				EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
				EGL_RECORDABLE_ANDROID, 1,
				EGL14.EGL_NONE
		};
		// @formatter:on

		EGLConfig[] configs = new EGLConfig[1];
		int[] numConfigs = new int[1];
		EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0);
		checkEglError("eglCreateContext RGB888+recordable ES2");

		// configure context for OpenGL ES 2.0
		// @formatter:off
		int[] attrib_list = {
				EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
				EGL14.EGL_NONE
		};
		// @formatter:on

		mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.eglGetCurrentContext(), attrib_list, 0);
		checkEglError("eglCreateContext");

		// create a window surface, and attach it to the Surface we received
		mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], mSurface, new int[]{ EGL14.EGL_NONE }, 0);
		checkEglError("eglCreateWindowSurface");
	}

	/**
	 * Discards all resources held by this class, notably the EGL context. Also releases the Surface that was passed to our
	 * constructor.
	 */
	public void release() {
		if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
			EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
			EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
			EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
			EGL14.eglReleaseThread();
			EGL14.eglTerminate(mEGLDisplay);
		}
		mSurface.release();

		mEGLDisplay = EGL14.EGL_NO_DISPLAY;
		mEGLContext = EGL14.EGL_NO_CONTEXT;
		mEGLSurface = EGL14.EGL_NO_SURFACE;

		mSurface = null;
	}

	/**
	 * Makes our EGL context and surface current.
	 */
	boolean makeCurrent() {
		boolean result = EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
		checkEglError("eglMakeCurrent");
		return result;
	}

	/**
	 * Calls eglSwapBuffers. Use this to "publish" the current frame.
	 */
	boolean swapBuffers() {
		boolean result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
		checkEglError("eglSwapBuffers");
		return result;
	}

	/**
	 * Sends the presentation time stamp to EGL. Time is expressed in nanoseconds.
	 */
	boolean setPresentationTime(long nanoseconds) {
		boolean result = EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nanoseconds);
		checkEglError("eglPresentationTimeANDROID");
		return result;
	}

	/**
	 * Checks for EGL errors. Throws a RuntimeException if one is found.
	 */
	private void checkEglError(String msg) {
		int error;
		if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
			throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
		}
	}
}
