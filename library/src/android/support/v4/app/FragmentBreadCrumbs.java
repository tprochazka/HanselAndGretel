/*
 * Copyright (C) 2011 Jake Wharton
 * Copyright (C) 2010 The Android Open Source Project
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

package android.support.v4.app;

import com.jakewharton.hanselandgretel.R;
import android.content.Context;
import android.support.v4.app.FragmentManager.BackStackEntry;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Helper class for showing "bread crumbs" representing the fragment
 * stack in an activity.  This is intended to be used with
 * {@link ActionBar#setCustomView(View)
 * ActionBar.setCustomView(View)} to place the bread crumbs in
 * the action bar.
 *
 * <p>The default style for this view is
 * {@link android.R.style#Widget_FragmentBreadCrumbs}.
 */
public class FragmentBreadCrumbs extends ViewGroup
        implements FragmentManager.OnBackStackChangedListener {
    private static final int MEASURED_HEIGHT_STATE_SHIFT = 16; //XXX As per View
    private static final int MEASURED_STATE_TOO_SMALL = 0x01000000; //XXX As per View
    private static final int MEASURED_STATE_MASK = 0xff000000; //XXX As per View

    //XXX As per View
    private static final int getMeasuredState(View child) {
        return (child.getMeasuredWidth()&MEASURED_STATE_MASK)
                | ((child.getMeasuredHeight()>>MEASURED_HEIGHT_STATE_SHIFT)
                        & (MEASURED_STATE_MASK>>MEASURED_HEIGHT_STATE_SHIFT));
    }

    //XXX As per View
    public static int combineMeasuredStates(int curState, int newState) {
        return curState | newState;
    }

    //XXX As per View
    public static int resolveSizeAndState(int size, int measureSpec, int childMeasuredState) {
        int result = size;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize =  MeasureSpec.getSize(measureSpec);
        switch (specMode) {
        case MeasureSpec.UNSPECIFIED:
            result = size;
            break;
        case MeasureSpec.AT_MOST:
            if (specSize < size) {
                result = specSize | MEASURED_STATE_TOO_SMALL;
            } else {
                result = size;
            }
            break;
        case MeasureSpec.EXACTLY:
            result = specSize;
            break;
        }
        return result | (childMeasuredState&MEASURED_STATE_MASK);
    }


    FragmentActivity mActivity;
    LayoutInflater mInflater;
    LinearLayout mContainer;
    int mMaxVisible = -1;

    // Hahah
    BackStackRecord mTopEntry;
    BackStackRecord mParentEntry;

    /** Listener to inform when a parent entry is clicked */
    private OnClickListener mParentClickListener;

    private OnBreadCrumbClickListener mOnBreadCrumbClickListener;

    /**
     * Interface to intercept clicks on the bread crumbs.
     */
    public interface OnBreadCrumbClickListener {
        /**
         * Called when a bread crumb is clicked.
         *
         * @param backStack The BackStackEntry whose bread crumb was clicked.
         * May be null, if this bread crumb is for the root of the back stack.
         * @param flags Additional information about the entry.  Currently
         * always 0.
         *
         * @return Return true to consume this click.  Return to false to allow
         * the default action (popping back stack to this entry) to occur.
         */
        public boolean onBreadCrumbClick(BackStackEntry backStack, int flags);
    }

    public FragmentBreadCrumbs(Context context) {
        this(context, null);
    }

    public FragmentBreadCrumbs(Context context, AttributeSet attrs) {
        this(context, attrs, R.style.Widget_HanselAndGretel_FragmentBreadCrumb);
    }

    public FragmentBreadCrumbs(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (getContext() instanceof FragmentActivity) {
            setActivity((FragmentActivity) getContext());
        } else {
            throw new IllegalStateException("FragmentBreadCrumbs can be used only in FragmentActivity");
        }
    }

    /**
     * Attach the bread crumbs to their activity.  This must be called once
     * when creating the bread crumbs.
     *
     * @deprecated It will be called automatically now when this will be attached to the window
     */
    public void setActivity(FragmentActivity a) {
        mActivity = a;
        if (mContainer == null) {
            mInflater = (LayoutInflater)a.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mContainer = (LinearLayout)mInflater.inflate(R.layout.hag__fragment_bread_crumbs, this, false);
            addView(mContainer);
            a.getSupportFragmentManager().addOnBackStackChangedListener(this);
            updateCrumbs();
        }
    }

    /**
     * The maximum number of breadcrumbs to show. Older fragment headers will be hidden from view.
     * @param visibleCrumbs the number of visible breadcrumbs. This should be greater than zero.
     */
    public void setMaxVisible(int visibleCrumbs) {
        if (visibleCrumbs < 1) {
            throw new IllegalArgumentException("visibleCrumbs must be greater than zero");
        }
        mMaxVisible = visibleCrumbs;
    }

    /**
     * Inserts an optional parent entry at the first position in the breadcrumbs. Selecting this
     * entry will result in a call to the specified listener's
     * {@link android.view.View.OnClickListener#onClick(View)}
     * method.
     *
     * @param title the title for the parent entry
     * @param shortTitle the short title for the parent entry
     * @param listener the {@link android.view.View.OnClickListener} to be called when clicked.
     * A null will result in no action being taken when the parent entry is clicked.
     */
    public void setParentTitle(CharSequence title, CharSequence shortTitle,
            OnClickListener listener) {
        mParentEntry = createBackStackEntry(title, shortTitle);
        mParentClickListener = listener;
        updateCrumbs();
    }

    /**
     * Sets a listener for clicks on the bread crumbs.  This will be called before
     * the default click action is performed.
     *
     * @param listener The new listener to set.  Replaces any existing listener.
     */
    public void setOnBreadCrumbClickListener(OnBreadCrumbClickListener listener) {
        mOnBreadCrumbClickListener = listener;
    }

    private BackStackRecord createBackStackEntry(CharSequence title, CharSequence shortTitle) {
        if (title == null) return null;

        final BackStackRecord entry = new BackStackRecord(
                (FragmentManagerImpl) mActivity.getSupportFragmentManager());
        entry.setBreadCrumbTitle(title);
        entry.setBreadCrumbShortTitle(shortTitle);
        return entry;
    }

    /**
     * Set a custom title for the bread crumbs.  This will be the first entry
     * shown at the left, representing the root of the bread crumbs.  If the
     * title is null, it will not be shown.
     */
    public void setTitle(CharSequence title, CharSequence shortTitle) {
        mTopEntry = createBackStackEntry(title, shortTitle);
        updateCrumbs();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Eventually we should implement our own layout of the views,
        // rather than relying on a linear layout.
        final int mPaddingLeft = getPaddingLeft(); //XXX
        final int mPaddingRight = getPaddingRight(); //XXX
        final int mPaddingTop = getPaddingTop(); //XXX
        final int mPaddingBottom = getPaddingBottom(); //XXX
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            int childRight = mPaddingLeft + child.getMeasuredWidth() - mPaddingRight;
            int childBottom = mPaddingTop + child.getMeasuredHeight() - mPaddingBottom;
            child.layout(mPaddingLeft, mPaddingTop, childRight, childBottom);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int count = getChildCount();

        final int mPaddingLeft = getPaddingLeft(); //XXX
        final int mPaddingRight = getPaddingRight(); //XXX
        final int mPaddingTop = getPaddingTop(); //XXX
        final int mPaddingBottom = getPaddingBottom(); //XXX

        int maxHeight = 0;
        int maxWidth = 0;
        int measuredChildState = 0;

        // Find rightmost and bottom-most child
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
                maxWidth = Math.max(maxWidth, child.getMeasuredWidth());
                maxHeight = Math.max(maxHeight, child.getMeasuredHeight());
                measuredChildState = combineMeasuredStates(measuredChildState,
                        getMeasuredState(child)); //XXX child.getMeasuredState());
            }
        }

        // Account for padding too
        maxWidth += mPaddingLeft + mPaddingRight;
        maxHeight += mPaddingTop + mPaddingBottom;

        // Check against our minimum height and width
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, measuredChildState),
                resolveSizeAndState(maxHeight, heightMeasureSpec,
                        measuredChildState<<MEASURED_HEIGHT_STATE_SHIFT));
    }

    @Override
    public void onBackStackChanged() {
        updateCrumbs();
    }

    /**
     * Returns the number of entries before the backstack, including the title of the current
     * fragment and any custom parent title that was set.
     */
    private int getPreEntryCount() {
        return (mTopEntry != null ? 1 : 0) + (mParentEntry != null ? 1 : 0);
    }

    /**
     * Returns the pre-entry corresponding to the index. If there is a parent and a top entry
     * set, parent has an index of zero and top entry has an index of 1. Returns null if the
     * specified index doesn't exist or is null.
     * @param index should not be more than {@link #getPreEntryCount()} - 1
     */
    private BackStackEntry getPreEntry(int index) {
        // If there's a parent entry, then return that for zero'th item, else top entry.
        if (mParentEntry != null) {
            return index == 0 ? mParentEntry : mTopEntry;
        } else {
            return mTopEntry;
        }
    }

    void updateCrumbs() {
        FragmentManager fm = mActivity.getSupportFragmentManager();
        int numEntries = fm.getBackStackEntryCount();
        int numPreEntries = getPreEntryCount();
        int numViews = mContainer.getChildCount();
        for (int i = 0; i < numEntries + numPreEntries; i++) {
            BackStackEntry bse = i < numPreEntries
                    ? getPreEntry(i)
                    : fm.getBackStackEntryAt(i - numPreEntries);
            if (i < numViews) {
                View v = mContainer.getChildAt(i);
                Object tag = v.getTag();
                if (tag != bse) {
                    for (int j = i; j < numViews; j++) {
                        mContainer.removeViewAt(i);
                    }
                    numViews = i;
                }
            }
            if (i >= numViews) {
                final View item = mInflater.inflate(
                        R.layout.hag__fragment_bread_crumb_item,
                        this, false);
                final TextView text = (TextView) item.findViewById(android.R.id.title);
                text.setText(bse.getBreadCrumbTitle());
                text.setTag(bse);
                if (i == 0) {
                    item.findViewById(android.R.id.icon).setVisibility(View.GONE);
                }
                mContainer.addView(item);
                text.setOnClickListener(mOnClickListener);
            }
        }
        int viewI = numEntries + numPreEntries;
        numViews = mContainer.getChildCount();
        while (numViews > viewI) {
            mContainer.removeViewAt(numViews - 1);
            numViews--;
        }
        // Adjust the visibility and availability of the bread crumbs and divider
        for (int i = 0; i < numViews; i++) {
            final View child = mContainer.getChildAt(i);
            // Disable the last one
            child.findViewById(android.R.id.title).setEnabled(i < numViews - 1);
            if (mMaxVisible > 0) {
                // Make only the last mMaxVisible crumbs visible
                child.setVisibility(i < numViews - mMaxVisible ? View.GONE : View.VISIBLE);
                final View leftIcon = child.findViewById(android.R.id.icon);
                // Remove the divider for all but the last mMaxVisible - 1
                leftIcon.setVisibility(i > numViews - mMaxVisible && i != 0 ? View.VISIBLE
                        : View.GONE);
            }
        }
    }

    private OnClickListener mOnClickListener = new OnClickListener() {
        public void onClick(View v) {
            if (v.getTag() instanceof BackStackEntry) {
                BackStackEntry bse = (BackStackEntry) v.getTag();
                if (bse == mParentEntry) {
                    if (mParentClickListener != null) {
                        mParentClickListener.onClick(v);
                    }
                } else {
                    if (mOnBreadCrumbClickListener != null) {
                        if (mOnBreadCrumbClickListener.onBreadCrumbClick(
                                bse == mTopEntry ? null : bse, 0)) {
                            return;
                        }
                    }
                    if (bse == mTopEntry) {
                        // Pop everything off the back stack.
                        mActivity.getSupportFragmentManager().popBackStack();
                    } else {
                        mActivity.getSupportFragmentManager().popBackStack(bse.getId(), 0);
                    }
                }
            }
        }
    };
}
