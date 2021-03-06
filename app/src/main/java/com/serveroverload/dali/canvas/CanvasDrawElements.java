/**
 * CanvasView.java
 *
 * Copyright (c) 2014 Tomohiro IKEDA (Korilakkuma)
 * Released under the MIT license
 */

package com.serveroverload.dali.canvas;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.serveroverload.dali.R;
import com.serveroverload.dali.colorbox.ColorGenerator;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.CornerPathEffect;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathDashPathEffect;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.view.MotionEvent;
// import android.util.Log;
// import android.widget.Toast;
import android.view.View;
import android.widget.Toast;

/**
 * This class defines fields and methods for drawing.
 */
public class CanvasDrawElements extends View {

	// private ScaleGestureDetector mScaleDetector;
	// private float mScaleFactor = 1.f;

	ColorGenerator colorGenerator = ColorGenerator.MATERIAL;

	private Bitmap mBitmapBrush;
	private Vector2 mBitmapBrushDimensions;

	private List<Vector2> mPositions = new ArrayList<Vector2>(100);

	private static final class Vector2 {
		public Vector2(float x, float y) {
			this.x = x;
			this.y = y;
		}

		public final float x;
		public final float y;
	}

	// Enumeration for Mode
	public enum Mode {
		DRAW, TEXT, ERASER, LOGO;
	}

	// Enumeration for Drawer
	public enum Drawer {
		PEN, LINE, RECTANGLE, CIRCLE, ELLIPSE, QUADRATIC_BEZIER, QUBIC_BEZIER;
	}

	private Context context = null;
	private Canvas canvas = null;
	private Bitmap bitmap = null;

	private List<Path> pathLists = new ArrayList<Path>();
	private List<Paint> paintLists = new ArrayList<Paint>();

	// for Eraser
	private int baseColor = Color.WHITE;

	// for Undo, Redo
	private int historyPointer = 0;

	// Flags
	private Mode mode = Mode.DRAW;
	private Drawer drawer = Drawer.PEN;
	private boolean isDown = false;

	// for Paint
	private Paint.Style paintStyle = Paint.Style.STROKE;
	private int paintStrokeColor = colorGenerator.getRandomColor();// Color.BLACK;
	private int paintFillColor = colorGenerator.getRandomColor(); // Color.BLACK;
	private float paintStrokeWidth = 3F;
	private int opacity = 255;
	private float blur = 0F;
	private Paint.Cap lineCap = Paint.Cap.ROUND;

	// for Text
	private String text = "Aweome Canvas";
	private Typeface fontFamily = Typeface.DEFAULT;
	private float fontSize = 32F;
	private Paint.Align textAlign = Paint.Align.RIGHT; // fixed
	private Paint textPaint = new Paint();
	private float textX = 0F;
	private float textY = 0F;

	// for Drawer
	private float startX = 0F;
	private float startY = 0F;
	private float controlX = 0F;
	private float controlY = 0F;
	private float phase = 3;

	// private GradientBox paintBox;

	/**
	 * Copy Constructor
	 * 
	 * @param context
	 * @param attrs
	 * @param defStyle
	 */
	public CanvasDrawElements(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.setup(context);

	}

	/**
	 * Copy Constructor
	 * 
	 * @param context
	 * @param attrs
	 */
	public CanvasDrawElements(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.setup(context);
	}

	/**
	 * Copy Constructor
	 * 
	 * @param context
	 */
	public CanvasDrawElements(Context context) {
		super(context);
		this.setup(context);
	}

	/**
	 * Common initialization.
	 * 
	 * @param context
	 */
	private void setup(Context context) {
		this.context = context;

		// paintBox = new GradientBox();

		this.pathLists.add(new Path());
		this.paintLists.add(this.createPaint());
		this.historyPointer++;

		this.textPaint.setARGB(0, 255, 255, 255);

		mBitmapBrush = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher);

		mBitmapBrushDimensions = new Vector2(mBitmapBrush.getWidth(), mBitmapBrush.getHeight());

		// mScaleDetector = new ScaleGestureDetector(context, new
		// ScaleListener());
	}

	/** The screen half width. */
	private int SCREEN_HALF_WIDTH;

	/** The screen half height. */
	private int SCREEN_HALF_HEIGHT;

	/** The screen width. */
	private int screenWidth;

	/** The screen height. */
	private int screenHeight;

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		screenWidth = MeasureSpec.getSize(widthMeasureSpec);
		screenHeight = MeasureSpec.getSize(heightMeasureSpec);

		SCREEN_HALF_WIDTH = screenWidth / 2;

		SCREEN_HALF_HEIGHT = screenHeight / 2;

		this.setMeasuredDimension(screenWidth, screenHeight);

		// paintBox.initPaintBox(screenWidth, screenHeight, SCREEN_HALF_WIDTH,
		// SCREEN_HALF_HEIGHT);

		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	/**
	 * This method creates the instance of Paint. In addition, this method sets
	 * styles for Paint.
	 * 
	 * @return paint This is returned as the instance of Paint
	 */
	private Paint createPaint() {
		Paint paint = new Paint();

		paint.setAntiAlias(true);
		paint.setStyle(this.paintStyle);
		paint.setStrokeWidth(this.paintStrokeWidth);
		paint.setStrokeCap(this.lineCap);
		paint.setStrokeJoin(Paint.Join.MITER); // fixed

		// for Text
		if (this.mode == Mode.TEXT) {
			paint.setTypeface(this.fontFamily);
			paint.setTextSize(this.fontSize);
			paint.setTextAlign(this.textAlign);
			paint.setStrokeWidth(0F);
		}

		if (this.mode == Mode.ERASER) {
			// Eraser

			paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
			paint.setARGB(0, 0, 0, 0);

			// paint.setColor(this.baseColor);
			// paint.setShadowLayer(this.blur, 0F, 0F, this.baseColor);
		} else {
			// Otherwise
			paint.setColor(this.paintStrokeColor);
			paint.setShadowLayer(this.blur, 0F, 0F, this.paintStrokeColor);
			paint.setAlpha(this.opacity);
		}

		return paint;
	}

	private static Path makePathDash() {
		Path p = new Path();
		p.moveTo(-6, 4);
		p.lineTo(6, 4);
		p.lineTo(6, 3);
		p.lineTo(-6, 3);
		p.close();
		p.moveTo(-6, -4);
		p.lineTo(6, -4);
		p.lineTo(6, -3);
		p.lineTo(-6, -3);
		return p;
	}

	void setPathEffect(Paint paint) {

		paint.setPathEffect(new DashPathEffect(new float[] { 10, 20 }, 0));

		paint.setPathEffect(new CornerPathEffect(10));

		paint.setPathEffect(new DashPathEffect(new float[] { 10, 5, 5, 5 }, phase));

		paint.setPathEffect(new PathDashPathEffect(makePathDash(), 12, phase, PathDashPathEffect.Style.MORPH));

		// paint.setPathEffect(new ComposePathEffect(e[2], e[1]));

	}

	/**
	 * This method initialize Path. Namely, this method creates the instance of
	 * Path, and moves current position.
	 * 
	 * @param event
	 *            This is argument of onTouchEvent method
	 * @return path This is returned as the instance of Path
	 */
	private Path createPath(MotionEvent event) {
		Path path = new Path();

		// Save for ACTION_MOVE
		this.startX = event.getX();
		this.startY = event.getY();

		path.moveTo(this.startX, this.startY);

		return path;
	}

	/**
	 * This method updates the lists for the instance of Path and Paint. "Undo"
	 * and "Redo" are enabled by this method.
	 * 
	 * @param path
	 *            the instance of Path
	 * @param paint
	 *            the instance of Paint
	 */
	private void updateHistory(Path path) {
		if (this.historyPointer == this.pathLists.size()) {
			this.pathLists.add(path);
			this.paintLists.add(this.createPaint());
			this.historyPointer++;
		} else {
			// On the way of Undo or Redo
			this.pathLists.set(this.historyPointer, path);
			this.paintLists.set(this.historyPointer, this.createPaint());
			this.historyPointer++;

			for (int i = this.historyPointer, size = this.paintLists.size(); i < size; i++) {
				this.pathLists.remove(this.historyPointer);
				this.paintLists.remove(this.historyPointer);
			}
		}
	}

	/**
	 * This method gets the instance of Path that pointer indicates.
	 * 
	 * @return the instance of Path
	 */
	private Path getCurrentPath() {
		return this.pathLists.get(this.historyPointer - 1);
	}

	/**
	 * This method draws text.
	 * 
	 * @param canvas
	 *            the instance of Canvas
	 */
	private void drawText(Canvas canvas) {
		if (this.text.length() <= 0) {
			return;
		}

		if (this.mode == Mode.TEXT) {
			this.textX = this.startX;
			this.textY = this.startY;

			this.textPaint = this.createPaint();
		}

		float textX = this.textX;
		float textY = this.textY;

		Paint paintForMeasureText = new Paint();

		// Line break automatically
		float textLength = paintForMeasureText.measureText(this.text);
		float lengthOfChar = textLength / (float) this.text.length();
		float restWidth = this.canvas.getWidth() - textX; // text-align : right
		int numChars = (lengthOfChar <= 0) ? 1 : (int) Math.floor((double) (restWidth / lengthOfChar)); // The
																										// number
																										// line
		int modNumChars = (numChars < 1) ? 1 : numChars;
		float y = textY;

		for (int i = 0, len = this.text.length(); i < len; i += modNumChars) {
			String substring = "";

			if ((i + modNumChars) < len) {
				substring = this.text.substring(i, (i + modNumChars));
			} else {
				substring = this.text.substring(i, len);
			}

			y += this.fontSize;

			canvas.drawText(substring, textX, y, this.textPaint);
		}
	}

	/**
	 * This method defines processes on MotionEvent.ACTION_DOWN
	 * 
	 * @param event
	 *            This is argument of onTouchEvent method
	 */
	private void onActionDown(MotionEvent event) {
		switch (this.mode) {
		case DRAW:
		case ERASER:
			if ((this.drawer != Drawer.QUADRATIC_BEZIER) && (this.drawer != Drawer.QUBIC_BEZIER)) {
				// Oherwise
				this.updateHistory(this.createPath(event));
				this.isDown = true;
			} else {
				// Bezier
				if ((this.startX == 0F) && (this.startY == 0F)) {
					// The 1st tap
					this.updateHistory(this.createPath(event));
				} else {
					// The 2nd tap
					this.controlX = event.getX();
					this.controlY = event.getY();

					this.isDown = true;
				}
			}

			break;
		case TEXT:
			this.startX = event.getX();
			this.startY = event.getY();

			break;
		default:
			break;
		}
	}

	/**
	 * This method defines processes on MotionEvent.ACTION_MOVE
	 * 
	 * @param event
	 *            This is argument of onTouchEvent method
	 */
	private void onActionMove(MotionEvent event) {
		float x = event.getX();
		float y = event.getY();

		switch (this.mode) {
		case DRAW:
		case ERASER:

			if ((this.drawer != Drawer.QUADRATIC_BEZIER) && (this.drawer != Drawer.QUBIC_BEZIER)) {
				if (!isDown) {
					return;
				}

				Path path = this.getCurrentPath();

				switch (this.drawer) {
				case PEN:
					path.lineTo(x, y);
					break;
				case LINE:
					path.reset();
					path.moveTo(this.startX, this.startY);
					path.lineTo(x, y);
					break;
				case RECTANGLE:
					path.reset();
					path.addRect(this.startX, this.startY, x, y, Path.Direction.CCW);
					break;
				case CIRCLE:
					double distanceX = Math.abs((double) (this.startX - x));
					double distanceY = Math.abs((double) (this.startX - y));
					double radius = Math.sqrt(Math.pow(distanceX, 2.0) + Math.pow(distanceY, 2.0));

					path.reset();
					path.addCircle(this.startX, this.startY, (float) radius, Path.Direction.CCW);
					break;
				case ELLIPSE:
					RectF rect = new RectF(this.startX, this.startY, x, y);

					path.reset();
					path.addOval(rect, Path.Direction.CCW);
					break;

				default:
					break;
				}
			} else {
				if (!isDown) {
					return;
				}

				Path path = this.getCurrentPath();

				path.reset();
				path.moveTo(this.startX, this.startY);
				path.quadTo(this.controlX, this.controlY, x, y);
			}

			break;
		case TEXT:
			this.startX = x;
			this.startY = y;

			break;

		case LOGO:

			final float posX = event.getX();
			final float posY = event.getY();

			if (!mPositions.isEmpty()) {
				mPositions.clear();
			}
			mPositions.add(new Vector2(posX - mBitmapBrushDimensions.x / 2, posY - mBitmapBrushDimensions.y / 2));
			invalidate();
			break;
		default:
			break;
		}
	}

	/**
	 * This method defines processes on MotionEvent.ACTION_DOWN
	 * 
	 * @param event
	 *            This is argument of onTouchEvent method
	 */
	private void onActionUp(MotionEvent event) {
		if (isDown) {
			this.startX = 0F;
			this.startY = 0F;
			this.isDown = false;
		}
	}

	/**
	 * This method updates the instance of Canvas (View)
	 * 
	 * @param canvas
	 *            the new instance of Canvas
	 */
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		// canvas.save();
		// canvas.scale(mScaleFactor, mScaleFactor);

		// Before "drawPath"
		canvas.drawColor(this.baseColor);

		for (int i = 0; i < this.historyPointer; i++) {
			Path path = this.pathLists.get(i);

			Paint paint = this.paintLists.get(i);

			// paint.setColor(colorGenerator.getRandomColor());

			canvas.drawPath(path, paint);
		}

		this.canvas = canvas;

		this.drawText(canvas);

		for (Vector2 pos : mPositions) {
			canvas.drawBitmap(mBitmapBrush, pos.x, pos.y, null);
		}

		// canvas.restore();
	}

	/**
	 * This method set event listener for drawing.
	 * 
	 * @param event
	 *            the instance of MotionEvent
	 * @return
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {

		// Let the ScaleGestureDetector inspect all events.

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			this.onActionDown(event);
			break;
		case MotionEvent.ACTION_MOVE:
			this.onActionMove(event);
			break;
		case MotionEvent.ACTION_UP:
			this.onActionUp(event);
			break;
		default:
			break;
		}

		// mScaleDetector.onTouchEvent(event);

		// Re draw
		this.invalidate();

		return true;
	}

	/**
	 * This method is getter for mode.
	 * 
	 * @return
	 */
	public Mode getMode() {
		return this.mode;
	}

	/**
	 * This method is setter for mode.
	 * 
	 * @param mode
	 */
	public void setMode(Mode mode) {
		this.mode = mode;
	}

	/**
	 * This method is getter for drawer.
	 * 
	 * @return
	 */
	public Drawer getDrawer() {
		return this.drawer;
	}

	/**
	 * This method is setter for drawer.
	 * 
	 * @param drawer
	 */
	public void setDrawer(Drawer drawer) {
		this.drawer = drawer;
	}

	/**
	 * This method draws canvas again for Undo.
	 * 
	 * @return If Undo is enabled, this is returned as true. Otherwise, this is
	 *         returned as false.
	 */
	public boolean undo() {
		if (this.historyPointer > 1) {
			this.historyPointer--;
			this.invalidate();

			return true;
		} else {
			return false;
		}
	}

	/**
	 * This method draws canvas again for Redo.
	 * 
	 * @return If Redo is enabled, this is returned as true. Otherwise, this is
	 *         returned as false.
	 */
	public boolean redo() {
		if (this.historyPointer < this.pathLists.size()) {
			this.historyPointer++;
			this.invalidate();

			return true;
		} else {
			return false;
		}
	}

	/**
	 * This method initializes canvas.
	 * 
	 * @return
	 */
	public void clear() {
		Path path = new Path();
		path.moveTo(0F, 0F);
		path.addRect(0F, 0F, 10000F, 10000F, Path.Direction.CCW);
		path.close();

		Paint paint = new Paint();
		paint.setColor(Color.WHITE);
		paint.setStyle(Paint.Style.FILL);

		if (this.historyPointer == this.pathLists.size()) {
			this.pathLists.add(path);
			this.paintLists.add(paint);
			this.historyPointer++;
		} else {
			// On the way of Undo or Redo
			this.pathLists.set(this.historyPointer, path);
			this.paintLists.set(this.historyPointer, paint);
			this.historyPointer++;

			for (int i = this.historyPointer, size = this.paintLists.size(); i < size; i++) {
				this.pathLists.remove(this.historyPointer);
				this.paintLists.remove(this.historyPointer);
			}
		}

		this.text = "";

		// Clear
		this.invalidate();
	}

	/**
	 * This method is getter for canvas background color
	 * 
	 * @return
	 */
	public int getBaseColor() {
		return this.baseColor;
	}

	/**
	 * This method is setter for canvas background color
	 * 
	 * @param color
	 */
	public void setBaseColor(int color) {
		this.baseColor = color;
	}

	/**
	 * This method is getter for drawn text.
	 * 
	 * @return
	 */
	public String getText() {
		return this.text;
	}

	/**
	 * This method is setter for drawn text.
	 * 
	 * @param text
	 */
	public void setText(String text) {
		this.text = text;
	}

	/**
	 * This method is getter for stroke or fill.
	 * 
	 * @return
	 */
	public Paint.Style getPaintStyle() {
		return this.paintStyle;
	}

	/**
	 * This method is setter for stroke or fill.
	 * 
	 * @param style
	 */
	public void setPaintStyle(Paint.Style style) {
		this.paintStyle = style;
	}

	/**
	 * This method is getter for stroke color.
	 * 
	 * @return
	 */
	public int getPaintStrokeColor() {
		return this.paintStrokeColor;
	}

	/**
	 * This method is setter for stroke color.
	 * 
	 * @param color
	 */
	public void setPaintStrokeColor(int color) {
		this.paintStrokeColor = color;
	}

	/**
	 * This method is getter for fill color. But, current Android API cannot set
	 * fill color (?).
	 * 
	 * @return
	 */
	public int getPaintFillColor() {
		return this.paintFillColor;
	};

	/**
	 * This method is setter for fill color. But, current Android API cannot set
	 * fill color (?).
	 * 
	 * @param color
	 */
	public void setPaintFillColor(int color) {
		this.paintFillColor = color;
	}

	/**
	 * This method is getter for stroke width.
	 * 
	 * @return
	 */
	public float getPaintStrokeWidth() {
		return this.paintStrokeWidth;
	}

	/**
	 * This method is setter for stroke width.
	 * 
	 * @param width
	 */
	public void setPaintStrokeWidth(float width) {
		if (width >= 0) {
			this.paintStrokeWidth = width;
		} else {
			this.paintStrokeWidth = 3F;
		}
	}

	/**
	 * This method is getter for alpha.
	 * 
	 * @return
	 */
	public int getOpacity() {
		return this.opacity;
	}

	/**
	 * This method is setter for alpha. The 1st argument must be between 0 and
	 * 255.
	 * 
	 * @param opacity
	 */
	public void setOpacity(int opacity) {
		if ((opacity >= 0) && (opacity <= 255)) {
			this.opacity = opacity;
		} else {
			this.opacity = 255;
		}
	}

	/**
	 * This method is getter for amount of blur.
	 * 
	 * @return
	 */
	public float getBlur() {
		return this.blur;
	}

	/**
	 * This method is setter for amount of blur. The 1st argument is greater
	 * than or equal to 0.0.
	 * 
	 * @param blur
	 */
	public void setBlur(float blur) {
		if (blur >= 0) {
			this.blur = blur;
		} else {
			this.blur = 0F;
		}
	}

	/**
	 * This method is getter for line cap.
	 * 
	 * @return
	 */
	public Paint.Cap getLineCap() {
		return this.lineCap;
	}

	/**
	 * This method is setter for line cap.
	 * 
	 * @param cap
	 */
	public void setLineCap(Paint.Cap cap) {
		this.lineCap = cap;
	}

	/**
	 * This method is getter for font size,
	 * 
	 * @return
	 */
	public float getFontSize() {
		return this.fontSize;
	}

	/**
	 * This method is setter for font size. The 1st argument is greater than or
	 * equal to 0.0.
	 * 
	 * @param size
	 */
	public void setFontSize(float size) {
		if (size >= 0F) {
			this.fontSize = size;
		} else {
			this.fontSize = 32F;
		}
	}

	/**
	 * This method is getter for font-family.
	 * 
	 * @return
	 */
	public Typeface getFontFamily() {
		return this.fontFamily;
	}

	/**
	 * This method is setter for font-family.
	 * 
	 * @param face
	 */
	public void setFontFamily(Typeface face) {
		this.fontFamily = face;
	}

	/**
	 * This method gets current canvas as bitmap.
	 * 
	 * @return This is returned as bitmap.
	 */
	public Bitmap getBitmap() {
		this.setDrawingCacheEnabled(false);
		this.setDrawingCacheEnabled(true);

		return Bitmap.createBitmap(this.getDrawingCache());
	}

	/**
	 * This method gets current canvas as scaled bitmap.
	 * 
	 * @return This is returned as scaled bitmap.
	 */
	public Bitmap getScaleBitmap(int w, int h) {
		this.setDrawingCacheEnabled(false);
		this.setDrawingCacheEnabled(true);

		return Bitmap.createScaledBitmap(this.getDrawingCache(), w, h, true);
	}

	/**
	 * This method draws the designated bitmap to canvas.
	 * 
	 * @param bitmap
	 */
	public void drawBitmap(Bitmap bitmap) {
		this.bitmap = bitmap;

		if (this.bitmap != null) {
			canvas.drawBitmap(this.bitmap, getWidth() / 2, getHeight() / 2, new Paint());
		}
		this.invalidate();
	}

	/**
	 * This method draws the designated byte array of bitmap to canvas.
	 * 
	 * @param byteArray
	 *            This is returned as byte array of bitmap.
	 */
	public void drawBitmap(byte[] byteArray) {
		this.drawBitmap(BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length));
	}

	/**
	 * This static method gets the designated bitmap as byte array.
	 * 
	 * @param bitmap
	 * @param format
	 * @param quality
	 * @return This is returned as byte array of bitmap.
	 */
	public static byte[] getBitmapAsByteArray(Bitmap bitmap, CompressFormat format, int quality) {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		bitmap.compress(format, quality, byteArrayOutputStream);

		return byteArrayOutputStream.toByteArray();
	}

	/**
	 * This method gets the bitmap as byte array.
	 * 
	 * @param format
	 * @param quality
	 * @return This is returned as byte array of bitmap.
	 */
	public byte[] getBitmapAsByteArray(CompressFormat format, int quality) {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		this.getBitmap().compress(format, quality, byteArrayOutputStream);

		return byteArrayOutputStream.toByteArray();
	}

	/**
	 * This method gets the bitmap as byte array. Bitmap format is PNG, and
	 * quality is 100.
	 * 
	 * @return This is returned as byte array of bitmap.
	 */
	public byte[] getBitmapAsByteArray() {
		return this.getBitmapAsByteArray(CompressFormat.PNG, 100);
	}

	int gradientStyle;

	public void setGrdientStyle(int gradientStyle) {

		this.gradientStyle = gradientStyle;
	}

	// private class ScaleListener extends
	// ScaleGestureDetector.SimpleOnScaleGestureListener {
	// @Override
	// public boolean onScale(ScaleGestureDetector detector) {
	// mScaleFactor *= detector.getScaleFactor();
	//
	// // Don't let the object get too small or too large.
	// mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 5.0f));
	//
	// invalidate();
	// return true;
	// }
	// }

	public Paint getShader(int gradientType, int x, int y, Paint paint) {

		switch (gradientType) {
		case RADIEL_GRADIENT:

			RadialGradient gradient = new RadialGradient(x, y, this.paintStrokeWidth, 0xFFFFFFFF, 0xFF000000,
					android.graphics.Shader.TileMode.REPEAT);
			paint.setDither(true);
			paint.setShader(gradient);

			return paint;

		case LINER_GRADIENT:

			paint.setAntiAlias(true);
			Shader linearGradientShader;

			linearGradientShader = new LinearGradient(0, 0, screenWidth, screenHeight, shaderColor1, shaderColor0,
					Shader.TileMode.MIRROR);

			paint.setShader(linearGradientShader);

			linearGradientShader = new LinearGradient(SCREEN_HALF_WIDTH, SCREEN_HALF_HEIGHT,
					SCREEN_HALF_WIDTH + SCREEN_HALF_WIDTH / 4, SCREEN_HALF_HEIGHT + SCREEN_HALF_HEIGHT / 4,
					shaderColor0, shaderColor1, Shader.TileMode.MIRROR);

			paint.setShader(linearGradientShader);

			return paint;

		case SWEEP_GRADIENT:

			paint.setAntiAlias(true);
			paint.setShader(new SweepGradient(SCREEN_HALF_WIDTH, SCREEN_HALF_HEIGHT, shaderColor0, shaderColor1));

			return paint;

		case COMPOSE_GRADIENT:

			RadialGradient radial_gradient = new RadialGradient(SCREEN_HALF_WIDTH, SCREEN_HALF_HEIGHT,
					this.paintStrokeWidth, 0xFFFFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP);

			int morecolors[] = new int[13];
			float hsv[] = new float[3];
			hsv[1] = 1;
			hsv[2] = 1;
			for (int i = 0; i < 12; i++) {
				hsv[0] = (360 / 12) * i;
				morecolors[i] = Color.HSVToColor(hsv);
			}
			morecolors[12] = morecolors[0];

			SweepGradient sweep_gradient = new SweepGradient(SCREEN_HALF_WIDTH, SCREEN_HALF_HEIGHT, morecolors, null);

			ComposeShader shader = new ComposeShader(sweep_gradient, radial_gradient, PorterDuff.Mode.SRC_OVER);

			paint.setDither(true);
			paint.setShader(shader);

			return paint;

		default:
			break;
		}
		return paint;

	}

	int shaderColor0 = Color.RED;
	int shaderColor1 = Color.BLUE;

	public static final int RADIEL_GRADIENT = 0;
	public static final int LINER_GRADIENT = 1;
	public static final int SWEEP_GRADIENT = 2;
	public static final int COMPOSE_GRADIENT = 4;

}
