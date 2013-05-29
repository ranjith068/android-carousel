package com.touchmenotapps.carousel.simple;

/*
 * Copyright (C) 2012 
 * Arindam Nath (strider2023@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;

import android.graphics.Camera;
import android.graphics.Matrix;

import android.os.SystemClock;

import android.util.AttributeSet;
import android.util.Log;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Transformation;

import android.widget.BaseAdapter;

public class HorizontalCarouselLayoutbackup extends ViewGroup {

	/* Scale ratio for each "layer" of children */
	private final float SCALE_RATIO = 0.9f;
	/* Gesture sensibility */
	private int mGestureSensitivity = 20;
	/* Animation time */
	private int DURATION = 120;

	/* Number of pixel between the top of two Views */
	private int mSpaceBetweenViews = 20;
	/* Rotation between two Views */
	private int mRotation;
	/* Status of rotation */
	private boolean mRotationEnabled = false;
	/* Tanslation between two Views */
	private int mTranslate;
	/* Status of translatation */
	private boolean mTranslatateEnabled = false;
	/* Transparency of incative child view */
	private float mSetInactiveViewTransparency = 1.0f;

	/* Number of internal Views */
	private int mHowManyViews = 99;
	/* Size of internal Views */
	private float mChildSizeRatio = 0.6f;
	/* Adapter */
	private BaseAdapter mAdapter = null;
	/* Item index of center view */
	private int mCurrentItem = 0;
	/* Index of center view in the ViewGroup */
	private int mCenterView = 0;
	/* Width of all children */
	private int mChildrenWidth;
	/* Width / 2 */
	private int mChildrenWidthMiddle;
	/* Height of all children */
	private int mChildrenHeight;
	/* Height / 2 */
	private int mChildrenHeightMiddle;
	/* Height center of the ViewGroup */
	private int mHeightCenter;
	/* Width center of the ViewGroup */
	private int mWidthCenter;
	/* Number of view below/above center view */
	private int mMaxChildUnderCenter;
	/* Inactive child view zoom out factor */
	private float mViewZoomOutFactor = 0.0f;
	/* Inactive child view coverflow rotation */
	private int mCoverflowRotation = 0;
	/* Collect crap views */
	private Collector mCollector = new Collector();
	/* Avoid multiple allocation */
	private Matrix mMatrix = new Matrix();

	private Context mContext;

	/* Gap between fixed position (for animation) */
	private float mGap;
	/* is animating */
	private boolean mIsAnimating = false;
	/* Avoid multiple allocation */
	private long mCurTime;
	/* Animation start time */
	private long mStartTime;
	/* Final item to reach (for animation from mCurrentItem to mItemToReach) */
	private int mItemtoReach = 0;

	private CarouselInterface mCallback;

	/**
	 * 
	 * @author Arindam Nath
	 * 
	 */
	public interface CarouselInterface {
		public void onItemChangedListener(View v, int position);
	}

	/* Animation Task */
	private Runnable animationTask = new Runnable() {
		public void run() {
			mCurTime = SystemClock.uptimeMillis();
			long totalTime = mCurTime - mStartTime;
			// Animation end
			if (totalTime > DURATION) {
				// Add new views
				if (mItemtoReach > mCurrentItem) {
					fillBottom();
				} else {
					fillTop();
				}
				Log.w("UITEST", "Size of mCollector: " + mCollector.mOldViews.size());
				// Register value to stop animation
				mCurrentItem = mItemtoReach;
				mGap = 0;
				mIsAnimating = false;
				// Calculate the new center view in the ViewGroup
				mCenterView = mCurrentItem;
				Log.w("UITEST", "mCenterView: " + mCenterView);
				if (mCurrentItem >= mMaxChildUnderCenter) {
					mCenterView = mMaxChildUnderCenter;
				}
				removeCallbacks(animationTask);
//				mCallback.onItemChangedListener(mAdapter.getView(mCurrentItem, null, HorizontalCarouselLayout.this), mCurrentItem);
				// Animate
			} else {
				float perCent = ((float) totalTime) / DURATION;
				mGap = (mCurrentItem - mItemtoReach) * perCent;
				post(this);
			}
			// Layout children
			childrenLayout(mGap);
			getChildAt(mCenterView).invalidate();
		}
	};

	/* Detect user gesture */
	private GestureDetector mGestureDetector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			// Intercept gestion only if not animating views
			if (!mIsAnimating && mAdapter != null) {
				int dx = (int) (e2.getX() - e1.getX());
				if ((Math.abs(dx) > mGestureSensitivity) && (Math.abs(velocityY) < Math.abs(velocityX))) {
					if (velocityX > 0) {
						// Top-bottom movement
						if (mCurrentItem > 0) {
							mItemtoReach = mCurrentItem - 1;
							mStartTime = SystemClock.uptimeMillis();
							mIsAnimating = true;
							post(animationTask);
							return true;
						}
					} else {
						// Bottom-Top movement
						if (mCurrentItem < (mAdapter.getCount() - 1)) {
							mItemtoReach = mCurrentItem + 1;
							mStartTime = SystemClock.uptimeMillis();
							mIsAnimating = true;
							post(animationTask);
							return true;
						}
					}
				}
			}
			return false;
		}
	});

	// ~--- constructors -------------------------------------------------------
	public HorizontalCarouselLayoutbackup(Context context) {
		super(context);
		mContext = context;
		initSlidingAnimation();
	}

	public HorizontalCarouselLayoutbackup(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;
		initSlidingAnimation();
	}

	public HorizontalCarouselLayoutbackup(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mContext = context;
		initSlidingAnimation();
	}

	// ~--- set methods --------------------------------------------------------
	public void disableRotation() {
		mRotationEnabled = false;
	}

	public void disableTranslate() {
		mTranslatateEnabled = false;
	}

	public void setOnCarouselViewChangedListener(CarouselInterface carouselInterface) {
		this.mCallback = carouselInterface;
	}

	/**
	 * @param gestureSensitivity
	 *            the mAJOR_MOVE to set
	 */
	public void setGestureSensitivity(int gestureSensitivity) {
		mGestureSensitivity = gestureSensitivity;
	}

	public void setStyle(HorizontalCarouselStyle style) {
		mSetInactiveViewTransparency = style.getInactiveViewTransparency();
		mSpaceBetweenViews = style.getSpaceBetweenViews();
		mRotation = style.getRotation();
		mRotationEnabled = style.isRotationEnabled();
		mTranslate = style.getTranslate();
		mTranslatateEnabled = style.isTranslatateEnbabled();
		mHowManyViews = style.getHowManyViews();
		mChildSizeRatio = style.getChildSizeRatio();
		mCoverflowRotation = style.getCoverflowRotation();
		mViewZoomOutFactor = style.getViewZoomOutFactor();
		DURATION = style.getAnimationTime();
	}

	/**
	 * Set adapter
	 * 
	 * @param adapter
	 */
	public void setAdapter(BaseAdapter adapter) {
		if (adapter != null) {
			mAdapter = adapter;
			mCenterView = mCurrentItem = 0;
			// even
			if ((mHowManyViews % 2) == 0) {
				// TODO : Fix it (for the moment work only with odd mHowManyViews)
				mMaxChildUnderCenter = (mHowManyViews / 2);
			} else {
				mMaxChildUnderCenter = (mHowManyViews / 2);
			}
			
			// Populate the ViewGroup
			for (int i = 0; i <= adapter.getCount(); i++) {
				Log.v("UITEST", "mMaxChildUnderCenter: " + mMaxChildUnderCenter);
				if (i > (mAdapter.getCount() - 1)) {
					break;
				}
				View v = mAdapter.getView(i, null, this);
//				Log.v("TESTUI", "view: " + v.getId());
				getChildStaticTransformation(v, new Transformation());
				addView(v, i);
				childrenLayout(i);
				getChildAt(i).invalidate();
				
			}
			post(animationTask);
//			for (int i = 0; i < getChildCount(); i++) {
//				getChildAt(i).invalidate();
//			}
		}
	}

	// ~--- methods ------------------------------------------------------------
	/* fillTop if required and garbage old views out of screen */
	// user swipes from left to right, index decrements
	private void fillTop() {
		Log.w("COVERFLOW", "fillTop(), childCount(): " + getChildCount());
		// Local (below center): too many children
		if (mCenterView < mMaxChildUnderCenter) {
			if (getChildCount() > mMaxChildUnderCenter + 1) {
				View old = getChildAt(getChildCount() - 1);
				detachViewFromParent(old);
				mCollector.collect(old);
			}
		}
		// Global : too many children
		if (getChildCount() >= mHowManyViews) {
			View old = getChildAt(mHowManyViews - 1);
			detachViewFromParent(old);
			mCollector.collect(old);
		}
		final int indexToRequest = mCurrentItem - (mMaxChildUnderCenter + 1);
		// retrieve if required
		if (indexToRequest >= 0) {
			Log.v("UITEST", "Fill top with " + indexToRequest);
			View recycled = mCollector.retrieve();
			View v = mAdapter.getView(indexToRequest, recycled, this);
			if (recycled != null) {
				Log.v("UITEST", "top: view attached");
				attachViewToParent(v, 0, generateDefaultLayoutParams());
				v.measure(mChildrenWidth, mChildrenHeight);
			} else {
				Log.v("UITEST", "top: view added");
				addView(v, 0);
			}
		}
		for (int i = 0; i < getChildCount(); i++) {
			getChildAt(i).invalidate();
		}
	}

	/* fillBottom if required and garbage old views out of screen */
	// user swipes from right to left, index increments
	private void fillBottom() {
		Log.w("COVERFLOW", "fillBottom(), childCount(): " + getChildCount());
		// Local (above center): too many children
		if (mCenterView >= mMaxChildUnderCenter) {
			View old = getChildAt(0);
			detachViewFromParent(old);
			mCollector.collect(old);
		}
		// Global : too many children
		if (getChildCount() >= mHowManyViews) {
			View old = getChildAt(0);
			detachViewFromParent(old);
			mCollector.collect(old);
		}
		final int indexToRequest = mCurrentItem + (mMaxChildUnderCenter + 1);
		if (indexToRequest < mAdapter.getCount()) {
			Log.v("UITEST", "Fill bottom with " + indexToRequest);
			View recycled = mCollector.retrieve();
			View v = mAdapter.getView(indexToRequest, recycled, this);
			if (recycled != null) {
				Log.v("UITEST", "bottom: view attached");
				attachViewToParent(v, -1, generateDefaultLayoutParams());
				v.measure(mChildrenWidth, mChildrenHeight);
			} else {
				Log.v("UITEST", "bottom: view added");
				addView(v, -1);
			}
		}
	}

	private void initSlidingAnimation() {
		setChildrenDrawingOrderEnabled(true);
		setStaticTransformationsEnabled(true);
		setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				mGestureDetector.onTouchEvent(event);
				return true;
			}
		});
	}

	/**
	 *  Fix position of all children
	 */
	private void childrenLayout(float gap) {
		final int leftCenterView = mWidthCenter - (mChildrenWidth / 2);
		final int topCenterView = mHeightCenter - (mChildrenHeight / 2);
		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);
			final float offset = mCenterView - i - gap;
			final int left = (int) (leftCenterView - (mSpaceBetweenViews * offset));
			child.layout(left, topCenterView, left + mChildrenWidth, topCenterView + mChildrenHeight);
			getChildAt(i).invalidate();
		}
	}

	// Overrides ----------------------------------------------------------------------------------


	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
	}

	@Override
	protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
		return new LayoutParams(p);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
	    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		Log.w("COVERFLOW", "onMeasure called");
		final int count = getChildCount();
		final int specWidthSize = MeasureSpec.getSize(widthMeasureSpec);
		final int specHeightSize = MeasureSpec.getSize(heightMeasureSpec);
		mWidthCenter = specWidthSize / 2;
		mHeightCenter = specHeightSize / 2;
		mChildrenWidth = (int) (specWidthSize * mChildSizeRatio);
		mChildrenHeight = (int) (specHeightSize * mChildSizeRatio);
		mChildrenWidthMiddle = mChildrenWidth / 2;
		mChildrenHeightMiddle = mChildrenHeight / 2;
		// Measure all children
		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);
//			getChildAt(i).invalidate();
			Log.w("COVERFLOW", "onMeasure children " + i);
			measureChild(child, mChildrenWidth, mChildrenHeight);
		}
//		setMeasuredDimension(specWidthSize, specHeightSize);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		Log.w("COVERFLOW", "onLayout called");
		childrenLayout(0);
	}
	
	@Override
	protected int getChildDrawingOrder(int childCount, int i) {
		int centerView = mCenterView;
		if (mGap > 0.5f) {
			centerView--;
		} else if (mGap < -0.5f) {
			centerView++;
		}
		if (i < centerView) {
			// before center view
			return i;
		} else if (i > centerView) {
			// after center view
			return centerView + (childCount - 1) - i;
		} else {
			// center view
			return childCount - 1;
		}
	}

	@Override
	protected boolean getChildStaticTransformation(View child, Transformation t) {
		final Camera camera = new Camera();
		final int leftCenterView = mWidthCenter - mChildrenWidthMiddle;
		final float offset = (-child.getLeft() + leftCenterView) / (float) mSpaceBetweenViews;
		
		if (child.getId() == mCenterView)
			return true;
		
		if (offset != 0) {
			final float absOffset = Math.abs(offset);
			float scale = (float) Math.pow(SCALE_RATIO, absOffset);
			t.clear();
			t.setTransformationType(Transformation.TYPE_MATRIX);
			t.setAlpha(mSetInactiveViewTransparency);
			final Matrix m = t.getMatrix();
			m.setScale(scale, scale);
			if (mTranslatateEnabled) {
				m.setTranslate(0, mTranslate * absOffset);
			}
			// scale from right
			if (offset > 0) {
				camera.save();
				camera.translate(0.0f, ((mViewZoomOutFactor * offset)/2), (mViewZoomOutFactor * offset));
				camera.rotateY(mCoverflowRotation);
				camera.getMatrix(m);
				camera.restore();
				m.preTranslate(-mChildrenWidthMiddle, -mChildrenHeight);
				m.postTranslate(mChildrenWidthMiddle, mChildrenHeight);
				// scale from left
			} else {
				camera.save();
				camera.translate(0.0f, -((mViewZoomOutFactor * offset)/2), -(mViewZoomOutFactor * offset));
				camera.rotateY(-mCoverflowRotation);
				camera.getMatrix(m);
				camera.restore();
				m.preTranslate(-mChildrenWidthMiddle, -mChildrenHeight);
				m.postTranslate(mChildrenWidthMiddle, mChildrenHeight);
			}
			mMatrix.reset();
			if (mRotationEnabled) {
				mMatrix.setRotate(mRotation * offset);
			}
			mMatrix.preTranslate(-mChildrenWidthMiddle, -mChildrenHeightMiddle);
			mMatrix.postTranslate(mChildrenWidthMiddle, mChildrenHeightMiddle);
			m.setConcat(m, mMatrix);
		}
//		for (int i = getChildCount() - 1; i >= 0; i--) {
//			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN)
//				getChildAt(i).invalidate();
//		}
		return true;
	}
}
