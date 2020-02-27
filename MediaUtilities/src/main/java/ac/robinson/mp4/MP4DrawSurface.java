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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.text.TextUtils;

import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Map;

import ac.robinson.mediautilities.FrameMediaContainer;
import ac.robinson.mediautilities.MediaUtilities;
import ac.robinson.util.BitmapUtilities;

// Based on examples at http://bigflake.com/mediacodec/CameraToMpegTest.java.txt and http://magroune.net/?p=63
class MP4DrawSurface {
	private Resources mResources;

	private final int mCanvasWidth;
	private final int mCanvasHeight;

	private final int mBackgroundColour;
	private final int mTextColourWithImage;
	private final int mTextColourNoImage;
	private final int mTextBackgroundColour;
	private final int mTextSpacing;
	private final int mTextCornerRadius;
	private final boolean mTextBackgroundSpanWidth;
	private final int mTextMaxFontSize;
	private final int mTextMaxCharsPerLine;
	private final int mAudioIconResourceId;

	private FrameMediaContainer mCurrentFrame;
	private Bitmap mCurrentFrameBitmap;
	private Canvas mCurrentFrameCanvas;
	private Paint mCurrentFramePaint;
	private SVG mAudioSVG;

	private final FloatBuffer mCubeTextureCoordinates;
	private int mTextureDataHandle;

	// @formatter:off
	private static final String VERTEX_SHADER_CODE =
			"attribute vec4 vPosition;" +
			"attribute vec2 a_TexCoordinate;" +
			"varying vec2 v_TexCoordinate;" +
			"void main() {" +
				"gl_Position = vPosition;" +
				"v_TexCoordinate = a_TexCoordinate;" +
			"}";

	private static final String FRAGMENT_SHADER_CODE =
			"precision mediump float;" +
			"uniform vec4 vColour;" +
			"uniform sampler2D u_Texture;" +
			"varying vec2 v_TexCoordinate;" +
			"void main() {" +
				"gl_FragColor = (vColour * texture2D(u_Texture, v_TexCoordinate));" +
			"}";
	// @formatter:on

	private final int shaderProgram;
	private final FloatBuffer vertexBuffer;
	private final ShortBuffer drawListBuffer;

	// number of coordinates per vertex in this array
	private static final int COORDS_PER_VERTEX = 2;
	private static float[] sSpriteCoords = {
			-0.15f, 0.15f,  // top left
			-0.15f, -0.15f, // bottom left
			0.15f, -0.15f,  // bottom right
			0.15f, 0.15f    //top right
	};

	private static short[] sDrawOrder = { 0, 1, 2, 0, 2, 3 }; // order to draw vertices


	public MP4DrawSurface(Resources resources, int canvasWidth, int canvasHeight, Map<Integer, Object> settings) {
		mResources = resources;
		mCanvasWidth = canvasWidth;
		mCanvasHeight = canvasHeight;

		// should really do proper checking on these
		mBackgroundColour = (Integer) settings.get(MediaUtilities.KEY_BACKGROUND_COLOUR);
		mTextColourWithImage = (Integer) settings.get(MediaUtilities.KEY_TEXT_COLOUR_WITH_IMAGE);
		mTextColourNoImage = (Integer) settings.get(MediaUtilities.KEY_TEXT_COLOUR_NO_IMAGE);
		mTextBackgroundColour = (Integer) settings.get(MediaUtilities.KEY_TEXT_BACKGROUND_COLOUR);
		mTextSpacing = (Integer) settings.get(MediaUtilities.KEY_TEXT_SPACING);
		mTextCornerRadius = (Integer) settings.get(MediaUtilities.KEY_TEXT_CORNER_RADIUS);
		mTextBackgroundSpanWidth = (Boolean) settings.get(MediaUtilities.KEY_TEXT_BACKGROUND_SPAN_WIDTH);
		mTextMaxFontSize = (Integer) settings.get(MediaUtilities.KEY_MAX_TEXT_FONT_SIZE);
		mTextMaxCharsPerLine = (Integer) settings.get(MediaUtilities.KEY_MAX_TEXT_CHARACTERS_PER_LINE);
		mAudioIconResourceId = (Integer) settings.get(MediaUtilities.KEY_AUDIO_RESOURCE_ID);

		mCurrentFrameBitmap = Bitmap.createBitmap(mCanvasWidth, mCanvasHeight, Bitmap.Config.ARGB_8888);
		mCurrentFrameCanvas = new Canvas(mCurrentFrameBitmap);
		mCurrentFramePaint = BitmapUtilities.getPaint(Color.WHITE, 1); // note: colour/size changed later when used

		// TODO: load appropriate settings here

		// initialize vertex buffer for shape coordinates (number of coordinate values * 4 bytes per float)
		ByteBuffer bb = ByteBuffer.allocateDirect(sSpriteCoords.length * 4);
		bb.order(ByteOrder.nativeOrder());
		vertexBuffer = bb.asFloatBuffer();
		vertexBuffer.put(sSpriteCoords);
		vertexBuffer.position(0); // set buffer to read first coordinate

		// configure texture coordinates
		final float[] cubeTextureCoordinateData = {
				0.0f, 0.0f, // top left
				0.0f, 1.0f, // top-right
				1.0f, 1.0f, // bottom-right
				1.0f, 0.0f  // bottom-left
		};
		mCubeTextureCoordinates = ByteBuffer.allocateDirect(cubeTextureCoordinateData.length * 4)
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer();
		mCubeTextureCoordinates.put(cubeTextureCoordinateData).position(0);

		// initialize byte buffer for the draw list
		ByteBuffer dlb = ByteBuffer.allocateDirect(sSpriteCoords.length * 2);
		dlb.order(ByteOrder.nativeOrder());
		drawListBuffer = dlb.asShortBuffer();
		drawListBuffer.put(sDrawOrder);
		drawListBuffer.position(0);

		// load vertex and fragent shaders
		int vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
		GLES20.glShaderSource(vertexShader, VERTEX_SHADER_CODE);
		GLES20.glCompileShader(vertexShader);
		//final int[] compileStatus = new int[1];
		//GLES20.glGetShaderiv(vertexShader, GLES20.GL_COMPILE_STATUS, compileStatus, 0); // TODO: check this error?

		int fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
		GLES20.glShaderSource(fragmentShader, FRAGMENT_SHADER_CODE);
		GLES20.glCompileShader(fragmentShader);
		//GLES20.glGetShaderiv(fragmentShader, GLES20.GL_COMPILE_STATUS, compileStatus, 0); // TODO: check this error?

		// attach shaders to program and bind to texture
		shaderProgram = GLES20.glCreateProgram();
		GLES20.glAttachShader(shaderProgram, vertexShader);
		GLES20.glAttachShader(shaderProgram, fragmentShader);
		GLES20.glBindAttribLocation(shaderProgram, 0, "a_TexCoordinate");
		GLES20.glLinkProgram(shaderProgram);

		final int[] textureHandle = new int[1];
		GLES20.glGenTextures(1, textureHandle, 0);

		// bind to the new texture in OpenGL
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

		// set filtering TODO: check best options here
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

		if (textureHandle[0] == 0) {
			throw new RuntimeException("Error loading texture.");
		}
		mTextureDataHandle = textureHandle[0];
	}

	void draw(float[] mvpMatrix) {
		// select our shader program
		GLES20.glUseProgram(shaderProgram);

		// configure position vertex
		int positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition");
		int vertexStride = COORDS_PER_VERTEX * 4;
		GLES20.glEnableVertexAttribArray(positionHandle);
		GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);

		// set colour
		int colourHandle = GLES20.glGetUniformLocation(shaderProgram, "vColour");
		float[] colour = { 1f, 1f, 1f, 1.0f };
		GLES20.glUniform4fv(colourHandle, 1, colour, 0);

		// set texture handles and bind texture
		int textureUniformHandle = GLES20.glGetAttribLocation(shaderProgram, "u_Texture");
		int textureCoordinateHandle = GLES20.glGetAttribLocation(shaderProgram, "a_TexCoordinate");

		// set the active texture unit to texture unit 0 and bind to it
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDataHandle);

		// tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
		GLES20.glUniform1i(textureUniformHandle, 0);

		// pass in texture coordinate information
		int textureCoordinateDataSize = 2;
		mCubeTextureCoordinates.position(0);
		GLES20.glVertexAttribPointer(textureCoordinateHandle, textureCoordinateDataSize, GLES20.GL_FLOAT, false, 0,
				mCubeTextureCoordinates);
		GLES20.glEnableVertexAttribArray(textureCoordinateHandle);

		// get handle to the model view projection matrix, and apply the transformation
		int matrixMVPHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix");
		GLES20.glUniformMatrix4fv(matrixMVPHandle, 1, false, mvpMatrix, 0);

		// configure textures
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

		// set transparency
		GLES20.glEnable(GLES20.GL_BLEND);
		GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

		// draw the output
		GLES20.glDrawElements(GLES20.GL_TRIANGLES, sDrawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

		// disable position vertex
		GLES20.glDisableVertexAttribArray(positionHandle);
	}

	void setCoords(float a1, float a2, float b1, float b2, float c1, float c2, float d1, float d2) {
		// TODO: could be used for zoom / Ken Burns effect, etc
		sSpriteCoords[0] = a1;
		sSpriteCoords[1] = a2;
		sSpriteCoords[2] = b1;
		sSpriteCoords[3] = b2;
		sSpriteCoords[4] = c1;
		sSpriteCoords[5] = c2;
		sSpriteCoords[6] = d1;
		sSpriteCoords[7] = d2;

		ByteBuffer bb = ByteBuffer.allocateDirect(sSpriteCoords.length * 4); // (# of coordinate values * 4 bytes per float)
		bb.order(ByteOrder.nativeOrder());
		vertexBuffer.put(sSpriteCoords);
		vertexBuffer.position(0);

		// initialize byte buffer for the draw list
		ByteBuffer dlb = ByteBuffer.allocateDirect(sDrawOrder.length * 2); // (# of coordinate values * 2 bytes per short)
		dlb.order(ByteOrder.nativeOrder());
		drawListBuffer.put(sDrawOrder);
		drawListBuffer.position(0);
	}

	void drawNarrativeFrame(FrameMediaContainer nextFrame) {

		// only redraw if this is a new frame
		if (!nextFrame.equals(mCurrentFrame)) {

			// reset after previous frame / texture
			boolean imageLoaded = false;
			BitmapUtilities.resetPaint(mCurrentFramePaint, Color.WHITE, 1);
			mCurrentFrameBitmap.eraseColor(nextFrame.mBackgroundColour < 0 ? nextFrame.mBackgroundColour : mBackgroundColour);

			if (nextFrame.mImagePath != null) {
				// scale image size to make sure it is small enough to fit in the container
				Bitmap imageBitmap = BitmapUtilities.loadAndCreateScaledBitmap(nextFrame.mImagePath, mCanvasWidth, mCanvasHeight
						, BitmapUtilities.ScalingLogic.FIT, true);

				if (imageBitmap != null) {
					int imageBitmapLeft = Math.round((mCanvasWidth - imageBitmap.getWidth()) / 2f);
					int imageBitmapTop = Math.round((mCanvasHeight - imageBitmap.getHeight()) / 2f);
					mCurrentFrameCanvas.drawBitmap(imageBitmap, imageBitmapLeft, imageBitmapTop, mCurrentFramePaint);

					imageLoaded = true;
				}
			}

			// TODO: we don't actually pass the value of @dimen/export_maximum_text_height_with_image
			if (!TextUtils.isEmpty(nextFrame.mTextContent)) {
				BitmapUtilities.drawScaledText(nextFrame.mTextContent, mCurrentFrameCanvas, mCurrentFramePaint,
						nextFrame.mForegroundColour <
								0 ? nextFrame.mForegroundColour : (imageLoaded ? mTextColourWithImage : mTextColourNoImage),
						(imageLoaded ? mTextBackgroundColour : 0), mTextSpacing, mTextCornerRadius, imageLoaded, 0,
						mTextBackgroundSpanWidth, mCanvasHeight, mTextMaxFontSize, mTextMaxCharsPerLine);

			} else if (!imageLoaded) {
				// quicker to do this than load the SVG for narratives that have no audio
				// TODO: this is the only reason we need the Resources object - should we just preload and pass this object?
				// TODO: use SVG.getBitmap()?
				if (mAudioIconResourceId < 0 && mAudioSVG == null) { // only load the icon if one is specified
					mAudioSVG = SVGParser.getSVGFromResource(mResources, mAudioIconResourceId);
				}
				if (mAudioSVG != null) {
					int audioBitmapSize = Math.min(mCanvasWidth, mCanvasHeight);
					int audioBitmapLeft = Math.round((mCanvasWidth - audioBitmapSize) / 2f);
					int audioBitmapTop = Math.round((mCanvasHeight - audioBitmapSize) / 2f);
					mCurrentFrameCanvas.drawPicture(mAudioSVG.getPicture(), new RectF(audioBitmapLeft, audioBitmapTop,
							audioBitmapLeft + audioBitmapSize, audioBitmapTop + audioBitmapSize));
				}
			}

			mCurrentFrame = nextFrame;

			// load the bitmap into the bound texture.
			GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mCurrentFrameBitmap, 0);
		}
	}
}
