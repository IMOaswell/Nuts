/*
 *   Copyright 2020 Rosemoe
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package io.github.rosemoe.editor.widget;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import io.github.rosemoe.editor.R;
import io.github.rosemoe.editor.interfaces.EditorEventListener;
import io.github.rosemoe.editor.interfaces.EditorLanguage;
import io.github.rosemoe.editor.langs.EmptyLanguage;
import io.github.rosemoe.editor.text.Content;
import io.github.rosemoe.editor.text.ContentListener;
import io.github.rosemoe.editor.text.Cursor;
import io.github.rosemoe.editor.text.FormatThread;
import io.github.rosemoe.editor.text.TextAnalyzeResult;
import io.github.rosemoe.editor.text.TextAnalyzer;
import io.github.rosemoe.editor.struct.BlockLine;
import io.github.rosemoe.editor.struct.Span;

import java.util.ArrayList;
import java.util.List;

import android.view.inputmethod.CursorAnchorInfo;
import android.graphics.Matrix;

import android.widget.OverScroller;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.SearchView;
import android.app.AlertDialog;
import android.content.DialogInterface;

import static io.github.rosemoe.editor.BuildConfig.DEBUG;

/**
 * CodeEditor is a editor that can highlight text regions by doing basic syntax analyzing
 * Features:
 * Highlight
 * Auto-completion
 * Scroll freely
 * Code Format
 * Shortcuts
 * <p>
 * Actually it can adapt to Android level 11
 * Me in GitHub: Rosemoe
 * This project in GitHub: https://github.com/Rosemoe/CodeEditor
 * <p>
 * Thanks following people for advice on UI:
 * NTX
 * 吾乃幼儿园扛把子
 * Xiue
 * Scave
 *
 * @author Rose
 */
public class CodeEditor extends View implements ContentListener, TextAnalyzer.Callback, FormatThread.FormatResultReceiver {

    private static final String LOG_TAG = "CodeEditor";

    /**
     * The default size when creating the editor object. Unit is sp.
     */
    public static final int DEFAULT_TEXT_SIZE = 20;

    private int mTabWidth;
    private int mCursorPosition;
    private float mDpUnit;
    private float mMaxPaintX;
    private float mSpaceWidth;
    private float mDividerWidth;
    private float mDividerMargin;
    private float mInsertSelWidth;
    private float mBlockLineWidth;
    private boolean mWait;
    private boolean mDrag;
    private boolean mScale;
    private boolean mEditable;
    private boolean mAutoIndent;
    private boolean mPaintLabel;
    private boolean mUndoEnabled;
    private boolean mDisplayLnPanel;
    private boolean mHighlightSelectedText;
    private boolean mHighlightCurrentBlock;
    private boolean mVerticalScrollBarEnabled;
    private boolean mHorizontalScrollBarEnabled;
    private RectF mRect;
    private RectF mLeftHandle;
    private RectF mRightHandle;
    private RectF mInsertHandle;
    private RectF mVerticalScrollBar;
    private RectF mHorizontalScrollBar;
    private ClipboardManager mClipboardManager;
    private InputMethodManager mInputMethodManager;

    private Cursor mCursor;
    private Content mText;
    private TextAnalyzer mSpanner;

    private Paint mPaint;
    private Paint mPaintOther;
    private char[] mChars;
    private Matrix mMatrix;
    private Rect mViewRect;
    private EditorColorScheme mColors;
    private String mLnTip = "Line:";
    private EditorLanguage mLanguage;
    private long mLastMakeVisible = 0;
    private EditorAutoCompleteWindow mACPanel;
    private EditorTouchEventHandler mEventHandler;
    private Paint.Align mLineNumberAlign;
    private GestureDetector mBasicDetector;
    private EditorTextActionWindow mTextActionWindow;
    private ScaleGestureDetector mScaleDetector;
    private EditorInputConnection mConnection;
    private CursorAnchorInfo.Builder mAnchorInfoBuilder;
    private FormatThread mFormatThread;
    private EditorSearcher mSearcher;
    private EditorEventListener mListener;
    private Paint.FontMetricsInt mTextMetrics;
    private Paint.FontMetricsInt mLineNumberMetrics;
    //For debug
    private final StringBuilder mErrorBuilder = new StringBuilder();

    /**
     * Create a new editor
     *
     * @param context Your activity
     */
    public CodeEditor(Context context) {
        this(context, null);
    }

    public CodeEditor(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CodeEditor(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        prepare();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public CodeEditor(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        prepare();
    }

    /**
     * Set event listener
     *
     * @see EditorEventListener
     */
    public void setEventListener(EditorEventListener eventListener) {
        this.mListener = eventListener;
    }

    /**
     * Cancel the next animation for {@link CodeEditor#ensurePositionVisible(int, int)}
     */
    protected void cancelAnimation() {
        mLastMakeVisible = System.currentTimeMillis();
    }

    /**
     * Send current selection position to input method
     */
    protected void updateSelection() {
        mInputMethodManager.updateSelection(this, mCursor.getLeft(), mCursor.getRight(), mCursor.getLeft(), mCursor.getRight());
    }

    /**
     * Notify input method that text has been changed for external reason
     */
    protected void cursorChangeExternal() {
        updateSelection();
        updateCursorAnchor();
        mConnection.invalid();
        mInputMethodManager.restartInput(this);
    }

    /**
     * Send cursor position in text and on screen to input method
     */
    protected void updateCursor() {
        updateCursorAnchor();
        updateSelection();
    }

    /**
     * Get the rect of left selection handle painted on view
     *
     * @return Rect of left handle
     */
    protected RectF getLeftHandleRect() {
        return mLeftHandle;
    }

    /**
     * Get the rect of right selection handle painted on view
     *
     * @return Rect of right handle
     */
    protected RectF getRightHandleRect() {
        return mRightHandle;
    }

    /**
     * Whether the editor should use a different color to draw
     * the current code block line and this code block's start line and end line's
     * background.
     *
     * @param highlightCurrentBlock Enabled / Disabled this module
     */
    public void setHighlightCurrentBlock(boolean highlightCurrentBlock) {
        this.mHighlightCurrentBlock = highlightCurrentBlock;
        if (!mHighlightCurrentBlock) {
            mCursorPosition = -1;
        } else {
            mCursorPosition = findCursorBlock();
        }
        invalidate();
    }

    /**
     * Returns whether highlight current code block
     *
     * @return This module enabled / disabled
     * @see CodeEditor#setHighlightCurrentBlock(boolean)
     */
    public boolean isHighlightCurrentBlock() {
        return mHighlightCurrentBlock;
    }

    /**
     * Whether we should use a different color to draw current code block's start line and end line background
     *
     * @param paintLabel Enabled or disabled
     */
    public void setPaintLabel(boolean paintLabel) {
        this.mPaintLabel = paintLabel;
        invalidate();
    }

    /**
     * Get the width of line number and divider line
     *
     * @return The width
     */
    protected float measurePrefix() {
        return measureLineNumber() + mDividerMargin * 2 + mDividerWidth;
    }

    /**
     * @return Enabled / disabled
     * @see CodeEditor#setPaintLabel(boolean)
     */
    public boolean isPaintLabel() {
        return mPaintLabel;
    }

    /**
     * Prepare editor
     * Initialize variants
     */
    private void prepare() {
        mPaint = new Paint();
        mPaintOther = new Paint();
        mMatrix = new Matrix();
        mSearcher = new EditorSearcher(this);
        //Only Android.LOLLIPOP and upper level device can use this builder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mAnchorInfoBuilder = new CursorAnchorInfo.Builder();
        }
        mPaint.setAntiAlias(true);
        mPaintOther.setAntiAlias(true);
        mPaintOther.setTypeface(Typeface.MONOSPACE);
        setTextSize(DEFAULT_TEXT_SIZE);
        mColors = new EditorColorScheme(this);
        mEventHandler = new EditorTouchEventHandler(this);
        mBasicDetector = new GestureDetector(getContext(), mEventHandler);
        mBasicDetector.setOnDoubleTapListener(mEventHandler);
        mScaleDetector = new ScaleGestureDetector(getContext(), mEventHandler);
        mViewRect = new Rect(0, 0, 0, 0);
        mRect = new RectF();
        mInsertHandle = new RectF();
        mLeftHandle = new RectF();
        mRightHandle = new RectF();
        mVerticalScrollBar = new RectF();
        mHorizontalScrollBar = new RectF();
        mDividerMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, Resources.getSystem().getDisplayMetrics());
        mDividerWidth = mDividerMargin;
        mInsertSelWidth = mDividerWidth;
        mDpUnit = mDividerWidth / 2;
        mDividerMargin = mDpUnit * 5;
        mLineNumberAlign = Paint.Align.RIGHT;
        mChars = new char[256];
        mEditable = true;
        mScale = true;
        mDrag = false;
        mWait = false;
        mPaintLabel = true;
        mDisplayLnPanel = true;
        mHighlightSelectedText = true;
        mBlockLineWidth = 2;
        mInputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        mClipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        setUndoEnabled(true);
        mTabWidth = 4;
        mAutoIndent = true;
        mCursorPosition = -1;
        mHighlightCurrentBlock = true;
        setFocusable(true);
        setFocusableInTouchMode(true);
        mConnection = new EditorInputConnection(this);
        mACPanel = new EditorAutoCompleteWindow(this);
        mTextActionWindow = new EditorTextActionWindow(this);
        mTextActionWindow.setHeight((int) (mDpUnit * 60));
        mTextActionWindow.setWidth((int) (mDpUnit * 230));
        setEditorLanguage(null);
        setText(null);
    }

    /**
     * Set the editor's language.
     * A language is a for auto completion,highlight and auto indent analysis.
     *
     * @param lang New EditorLanguage for editor
     */
    public void setEditorLanguage(EditorLanguage lang) {
        if (lang == null) {
            lang = new EmptyLanguage();
        }
        this.mLanguage = lang;
        if (mSpanner != null) {
            mSpanner.shutdown();
            mSpanner.setCallback(null);
        }
        mSpanner = new TextAnalyzer(lang.getAnalyzer());
        mSpanner.setCallback(this);
        if (mText != null) {
            mSpanner.analyze(mText);
        }
        if (mACPanel != null) {
            mACPanel.hide();
            mACPanel.setProvider(lang.getAutoCompleteProvider());
        }
        if (mCursor != null) {
            mCursor.setLanguage(mLanguage);
        }
        invalidate();
    }

    /**
     * Set the width of code block line
     *
     * @param dp Width in dp unit
     */
    public void setBlockLineWidth(float dp) {
        mBlockLineWidth = dp;
        invalidate();
    }

    /**
     * Get the character's x offset on view
     *
     * @param line   The line position of character
     * @param column The column position of character
     * @return The x offset on screen
     */
    protected float getOffset(int line, int column) {
        prepareLine(line);
        return measureText(mChars, 0, column) + measureLineNumber() + mDividerMargin * 2 + mDividerWidth - getOffsetX();
    }

    /**
     * Getter
     *
     * @return The width in dp unit
     * @see CodeEditor#setBlockLineWidth(float)
     */
    public float getBlockLineWidth() {
        return mBlockLineWidth;
    }

    /**
     * Whether display vertical scroll bar when scrolling
     *
     * @param enabled Enabled / disabled
     */
    public void setScrollBarEnabled(boolean enabled) {
        mVerticalScrollBarEnabled = mHorizontalScrollBarEnabled = enabled;
        invalidate();
    }

    /**
     * Whether display the line number panel beside vertical scroll bar
     * when the scroll bar is touched by user
     *
     * @param displayLnPanel Enabled / disabled
     */
    public void setDisplayLnPanel(boolean displayLnPanel) {
        this.mDisplayLnPanel = displayLnPanel;
        invalidate();
    }

    /**
     * @return Enabled / disabled
     * @see CodeEditor#setDisplayLnPanel(boolean)
     */
    public boolean isDisplayLnPanel() {
        return mDisplayLnPanel;
    }

    /**
     * Get TextActionWindow instance of this editor
     *
     * @return TextActionWindow
     */
    protected EditorTextActionWindow getTextActionWindow() {
        return mTextActionWindow;
    }

    /**
     * Set the tip text before line number for the line number panel
     *
     * @param prefix The prefix for text
     */
    public void setLnTip(String prefix) {
        if (prefix == null) {
            prefix = "";
        }
        mLnTip = prefix;
        invalidate();
    }

    /**
     * @return The prefix
     * @see CodeEditor#setLnTip(String)
     */
    public String getLnTip() {
        return mLnTip;
    }

    /**
     * Set whether auto indent should be executed when user enters
     * a NEWLINE
     *
     * @param enabled Enabled / disabled
     */
    public void setAutoIndentEnabled(boolean enabled) {
        mAutoIndent = enabled;
        mCursor.setAutoIndent(enabled);
    }

    /**
     * @return Enabled / disabled
     * @see CodeEditor#setAutoIndentEnabled(boolean)
     */
    public boolean isAutoIndentEnabled() {
        return mAutoIndent;
    }

    @Override
    public void setHorizontalScrollBarEnabled(boolean horizontalScrollBarEnabled) {
        mHorizontalScrollBarEnabled = horizontalScrollBarEnabled;
    }

    @Override
    public void setVerticalScrollBarEnabled(boolean verticalScrollBarEnabled) {
        mVerticalScrollBarEnabled = verticalScrollBarEnabled;
    }

    @Override
    public boolean isHorizontalScrollBarEnabled() {
        return mHorizontalScrollBarEnabled;
    }

    @Override
    public boolean isVerticalScrollBarEnabled() {
        return mVerticalScrollBarEnabled;
    }

    /**
     * Get the rect of vertical scroll bar on view
     *
     * @return Rect of scroll bar
     */
    protected RectF getVerticalScrollBarRect() {
        return mVerticalScrollBar;
    }

    /**
     * Get the rect of horizontal scroll bar on view
     *
     * @return Rect of scroll bar
     */
    protected RectF getHorizontalScrollBarRect() {
        return mHorizontalScrollBar;
    }

    /**
     * Get the rect of insert cursor handle on view
     *
     * @return Rect of insert handle
     */
    protected RectF getInsertHandleRect() {
        return mInsertHandle;
    }

    /**
     * Set text size in pixel unit
     *
     * @param size Text size in pixel unit
     */
    public void setTextSizePx(float size) {
        mPaint.setTextSize(size);
        mPaintOther.setTextSize(size);
        mSpaceWidth = mPaint.measureText(" ");
        mTextMetrics = mPaint.getFontMetricsInt();
        mLineNumberMetrics = mPaintOther.getFontMetricsInt();
        invalidate();
    }

    /**
     * Get text size in pixel unit
     *
     * @return Text size in pixel unit
     * @see CodeEditor#setTextSize(float)
     * @see CodeEditor#setTextSizePx(float)
     */
    public float getTextSizePx() {
        return mPaint.getTextSize();
    }

    /**
     * Paint the view on given Canvas
     *
     * @param canvas Canvas you want to draw
     */
    private void drawView(Canvas canvas) {
        long startTime = System.currentTimeMillis();

        if (mFormatThread != null) {
            String text = "Formatting your code...";
            float centerY = getHeight() / 2f;
            drawColor(canvas, mColors.getColor(EditorColorScheme.LINE_NUMBER_PANEL), mRect);
            float baseline = centerY - getLineHeight() / 2f + getLineBaseLine(0);
            float centerX = getWidth() / 2f;
            mPaint.setColor(mColors.getColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT));
            Paint.Align align = mPaint.getTextAlign();
            mPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(text, centerX, baseline, mPaint);
            mPaint.setTextAlign(align);
            return;
        }

        getCursor().updateCache(Math.max(getFirstVisibleLine(), 0));

        EditorColorScheme color = mColors;
        drawColor(canvas, color.getColor(EditorColorScheme.WHOLE_BACKGROUND), mViewRect);

        float lineNumberWidth = measureLineNumber();
        float offsetX = -getOffsetX();

        drawLineNumberBackground(canvas, offsetX, lineNumberWidth + mDividerMargin, color.getColor(EditorColorScheme.LINE_NUMBER_BACKGROUND));

        drawCurrentLineBackground(canvas, color.getColor(EditorColorScheme.CURRENT_LINE));
        //drawCurrentCodeBlockLabelBg(canvas);

        drawDivider(canvas, offsetX + lineNumberWidth + mDividerMargin, color.getColor(EditorColorScheme.LINE_DIVIDER));

        drawLineNumbers(canvas, offsetX, lineNumberWidth, color.getColor(EditorColorScheme.LINE_NUMBER));
        offsetX += lineNumberWidth + mDividerMargin * 2 + mDividerWidth;

        drawMatchedTextBackground(canvas, offsetX);
        if (mCursor.isSelected()) {
            drawSelectedTextBackground(canvas, offsetX, color.getColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND));
        }

        drawText(canvas, offsetX, color.getColor(EditorColorScheme.TEXT_NORMAL));
        drawComposingTextUnderline(canvas, offsetX, color.getColor(EditorColorScheme.UNDERLINE));

        drawBlockLines(canvas, offsetX);

        if (!mCursor.isSelected()) {
            drawCursor(canvas, mCursor.getLeftLine(), mCursor.getLeftColumn(), offsetX, color.getColor(EditorColorScheme.SELECTION_INSERT));
            if (mEventHandler.shouldDrawInsertHandle()) {
                drawHandle(canvas, mCursor.getLeftLine(), mCursor.getLeftColumn(), mInsertHandle);
            }
        } else if (!mTextActionWindow.isShowing()) {
            drawCursor(canvas, mCursor.getLeftLine(), mCursor.getLeftColumn(), offsetX, color.getColor(EditorColorScheme.SELECTION_INSERT));
            drawCursor(canvas, mCursor.getRightLine(), mCursor.getRightColumn(), offsetX, color.getColor(EditorColorScheme.SELECTION_INSERT));
            drawHandle(canvas, mCursor.getLeftLine(), mCursor.getLeftColumn(), mLeftHandle);
            drawHandle(canvas, mCursor.getRightLine(), mCursor.getRightColumn(), mRightHandle);
        } else {
            mLeftHandle.setEmpty();
            mRightHandle.setEmpty();
        }

        drawScrollBars(canvas);
        
        long timeUsage = System.currentTimeMillis() - startTime;
        if (DEBUG) {
            Log.d(LOG_TAG, "Draw view cost time:" + timeUsage + "ms");
        }
    }

    /**
     * Draw matched text background(EditorSearcher)
     */
    private void drawMatchedTextBackground(Canvas canvas, float offset) {
        final String searchText = getSearcher().mSearchText;
        if (searchText == null) {
            return;
        }
        int searchTextLength = searchText.length();
        if (searchTextLength == 0) {
            return;
        }
        int color = mColors.getColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND);
        outer:
        for (int i = getFirstVisibleLine(); i <= getLastVisibleLine(); i++) {
            boolean prepared = false;
            StringBuilder raw = mText.getRawData(i);
            int index = 0;
            while (index < raw.length()) {
                index = raw.indexOf(searchText, index);
                if (index != -1) {
                    if (!prepared) {
                        prepareLine(i);
                        prepared = true;
                    }
                    float left = offset + measureText(mChars, 0, index);
                    float right = left + measureText(mChars, index, searchTextLength);
                    if (right > 0 && left < getWidth()) {
                        mRect.top = getLineTop(i) - getOffsetY();
                        mRect.bottom = mRect.top + getLineHeight();
                        mRect.left = left;
                        mRect.right = right;
                        drawColor(canvas, color, mRect);
                    } else if (right >= getWidth()) {
                        continue outer;
                    }
                    index += searchTextLength;
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Draw a handle.
     * The handle can be insert handle,left handle or right handle
     *
     * @param canvas     The Canvas to draw handle
     * @param line       The line you want to attach handle to its bottom (Usually the selection line)
     * @param column     The column you want to attach handle center to its center x offset
     * @param resultRect The rect of handle this method drew
     */
    private void drawHandle(Canvas canvas, int line, int column, RectF resultRect) {
        float radius = mDpUnit * 10;
        float top = getLineBottom(line) - getOffsetY();
        float bottom = top + radius * 2;
        prepareLine(line);
        float centerX = measureLineNumber() + mDividerMargin * 2 + mDividerWidth + measureText(mChars, 0, column) - getOffsetX();
        float left = centerX - radius;
        float right = centerX + radius;
        if (right < 0 || left > getWidth() || bottom < 0 || top > getHeight()) {
            resultRect.setEmpty();
            return;
        }
        resultRect.left = left;
        resultRect.right = right;
        resultRect.top = top;
        resultRect.bottom = bottom;
        mPaint.setColor(mColors.getColor(EditorColorScheme.SELECTION_HANDLE));
        canvas.drawCircle(centerX, (top + bottom) / 2, radius, mPaint);
    }

    /**
     * Whether this region has visible region on screen
     *
     * @param begin The begin line of code block
     * @param end   The end line of code block
     * @param first The first visible line on screen
     * @param last  The last visible line on screen
     * @return Whether this block can be seen
     */
    private static boolean hasVisibleRegion(int begin, int end, int first, int last) {
        return (end > first && begin < last);
    }

    /**
     * Draw code block lines on screen
     *
     * @param canvas  The canvas to draw
     * @param offsetX The start x offset for text
     */
    private void drawBlockLines(Canvas canvas, float offsetX) {
        List<BlockLine> blocks = mSpanner == null ? null : mSpanner.getResult().getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        int first = getFirstVisibleLine();
        int last = getLastVisibleLine();
        boolean mark = false;
        int jCount = 0;
        int maxCount = Integer.MAX_VALUE;
        if (mSpanner != null) {
            TextAnalyzeResult colors = mSpanner.getResult();
            if (colors != null) {
                maxCount = colors.getSuppressSwitch();
            }
        }
        int mm = binarySearchEndBlock(first, blocks);
        int cursorIdx = mCursorPosition;
        for (int curr = mm; curr < blocks.size(); curr++) {
            BlockLine block = blocks.get(curr);
            if (hasVisibleRegion(block.startLine, block.endLine, first, last)) {
                try {
                    prepareLine(block.endLine);
                    float offset1 = measureText(mChars, 0, block.endColumn);
                    prepareLine(block.startLine);
                    float offset2 = measureText(mChars, 0, block.startColumn);
                    float offset = Math.min(offset1, offset2);
                    float centerX = offset + offsetX;
                    mRect.top = Math.max(0, getLineBottom(block.startLine) - getOffsetY());
                    mRect.bottom = Math.min(getHeight(), getLineTop(block.endLine) - getOffsetY());
                    mRect.left = centerX - mDpUnit * mBlockLineWidth / 2;
                    mRect.right = centerX + mDpUnit * mBlockLineWidth / 2;
                    drawColor(canvas, mColors.getColor(curr == cursorIdx ? EditorColorScheme.BLOCK_LINE_CURRENT : EditorColorScheme.BLOCK_LINE), mRect);
                } catch (IndexOutOfBoundsException e) {
                    e.printStackTrace();
                    //Not handled.
                    //Because the exception usually occurs when the content changed.
                }
                mark = true;
            } else if (mark) {
                if (jCount >= maxCount)
                    break;
                jCount++;
            }
        }
    }

    /**
     * Find the smallest code block that cursor is in
     *
     * @return The smallest code block index.
     * If cursor is not in any code block,just -1.
     */
    private int findCursorBlock() {
        List<BlockLine> blocks = mSpanner == null ? null : mSpanner.getResult().getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return -1;
        }
        return findCursorBlock(blocks);
    }

    /**
     * Find the cursor code block internal
     *
     * @param blocks Current code blocks
     * @return The smallest code block index.
     * If cursor is not in any code block,just -1.
     */
    private int findCursorBlock(List<BlockLine> blocks) {
        int line = mCursor.getLeftLine();
        int min = binarySearchEndBlock(line, blocks);
        int max = blocks.size() - 1;
        int minDis = Integer.MAX_VALUE;
        int found = -1;
        int jCount = 0;
        int maxCount = Integer.MAX_VALUE;
        if (mSpanner != null) {
            TextAnalyzeResult colors = mSpanner.getResult();
            if (colors != null) {
                maxCount = colors.getSuppressSwitch();
            }
        }
        for (int i = min; i <= max; i++) {
            BlockLine block = blocks.get(i);
            if (block.endLine >= line && block.startLine <= line) {
                int dis = block.endLine - block.startLine;
                if (dis < minDis) {
                    minDis = dis;
                    found = i;
                }
            } else if (minDis != Integer.MAX_VALUE) {
                jCount++;
                if (jCount >= maxCount) {
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Find the first code block that maybe seen on screen
     * Because the code blocks is sorted by its end line position
     * we can use binary search to quicken this process in order to decrease
     * the time we use on finding
     *
     * @param firstVis The first visible line
     * @param blocks   Current code blocks
     * @return The block we found.Always a valid index(Unless there is no block)
     */
    private int binarySearchEndBlock(int firstVis, List<BlockLine> blocks) {
        //end > firstVis
        int left = 0, right = blocks.size() - 1, mid, row;
        int max = right;
        while (left <= right) {
            mid = (left + right) / 2;
            if (mid < 0) return 0;
            if (mid > max) return max;
            row = blocks.get(mid).endLine;
            if (row > firstVis) {
                right = mid - 1;
            } else if (row < firstVis) {
                left = mid + 1;
            } else {
                left = mid;
                break;
            }
        }
        return Math.max(0, Math.min(left, max));
    }

    /**
     * Draw scroll bars and tracks
     *
     * @param canvas The canvas to draw
     */
    private void drawScrollBars(Canvas canvas) {
        if (!mEventHandler.shouldDrawScrollBar()) {
            return;
        }
        mVerticalScrollBar.setEmpty();
        mHorizontalScrollBar.setEmpty();
        if (getScrollMaxY() > getHeight() / 2) {
            drawScrollBarTrackVertical(canvas);
        }
        if (getScrollMaxX() > getWidth() * 3 / 4) {
            drawScrollBarTrackHorizontal(canvas);
        }
        if (getScrollMaxY() > getHeight() / 2) {
            drawScrollBarVertical(canvas);
        }
        if (getScrollMaxX() > getWidth() * 3 / 4) {
            drawScrollBarHorizontal(canvas);
        }
    }

    /**
     * Draw vertical scroll bar track
     *  @param canvas Canvas to draw
     *
     */
    private void drawScrollBarTrackVertical(Canvas canvas) {
        if (mEventHandler.holdVerticalScrollBar()) {
            mRect.right = getWidth();
            mRect.left = getWidth() - mDpUnit * 10;
            mRect.top = 0;
            mRect.bottom = getHeight();
            drawColor(canvas, mColors.getColor(EditorColorScheme.SCROLL_BAR_TRACK), mRect);
        }
    }

    /**
     * Draw vertical scroll bar
     *  @param canvas Canvas to draw
     *
     */
    private void drawScrollBarVertical(Canvas canvas) {
        int page = getHeight();
        float all = getLineHeight() * getLineCount() + getHeight() / 2f;
        float length = page / all * getHeight();
        float topY;
        if (length < mDpUnit * 30) {
            length = mDpUnit * 30;
            topY = (getOffsetY() + page / 2f) / all * (getHeight() - length);
        } else {
            topY = getOffsetY() / all * getHeight();
        }
        if (mEventHandler.holdVerticalScrollBar()) {
            float centerY = topY + length / 2f;
            drawLineInfoPanel(canvas, centerY, mRect.left - mDpUnit * 5);
        }
        mRect.right = getWidth();
        mRect.left = getWidth() - mDpUnit * 10;
        mRect.top = topY;
        mRect.bottom = topY + length;
        mVerticalScrollBar.set(mRect);
        drawColor(canvas, mColors.getColor(mEventHandler.holdVerticalScrollBar() ? EditorColorScheme.SCROLL_BAR_THUMB_PRESSED : EditorColorScheme.SCROLL_BAR_THUMB), mRect);
    }

    /**
     * Draw line number panel
     *
     * @param canvas  Canvas to draw
     * @param centerY The center y on screen for the panel
     * @param rightX  The right x on screen for the panel
     */
    private void drawLineInfoPanel(Canvas canvas, float centerY, float rightX) {
        if (!mDisplayLnPanel) {
            return;
        }
        float expand = mDpUnit * 3;
        String text = mLnTip + ((2 + getFirstVisibleLine() + getLastVisibleLine()) / 2);
        float textWidth = mPaint.measureText(text);
        mRect.top = centerY - getLineHeight() / 2f - expand;
        mRect.bottom = centerY + getLineHeight() / 2f + expand;
        mRect.right = rightX;
        mRect.left = rightX - expand * 2 - textWidth;
        drawColor(canvas, mColors.getColor(EditorColorScheme.LINE_NUMBER_PANEL), mRect);
        float baseline = centerY - getLineHeight() / 2f + getLineBaseLine(0);
        float centerX = (mRect.left + mRect.right) / 2;
        mPaint.setColor(mColors.getColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT));
        Paint.Align align = mPaint.getTextAlign();
        mPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, centerX, baseline, mPaint);
        mPaint.setTextAlign(align);
    }

    /**
     * Draw horizontal scroll bar track
     *  @param canvas Canvas to draw
     *
     */
    private void drawScrollBarTrackHorizontal(Canvas canvas) {
        if (mEventHandler.holdHorizontalScrollBar()) {
            mRect.top = getHeight() - mDpUnit * 10;
            mRect.bottom = getHeight();
            mRect.right = getWidth();
            mRect.left = 0;
            drawColor(canvas, mColors.getColor(EditorColorScheme.SCROLL_BAR_TRACK), mRect);
        }
    }

    /**
     * Draw horizontal scroll bar
     *  @param canvas Canvas to draw
     *
     */
    private void drawScrollBarHorizontal(Canvas canvas) {
        int page = getWidth();
        float all = getScrollMaxX() + getWidth();
        float length = page / all * getWidth();
        float leftX = getOffsetX() / all * getWidth();
        mRect.top = getHeight() - mDpUnit * 10;
        mRect.bottom = getHeight();
        mRect.right = leftX + length;
        mRect.left = leftX;
        mHorizontalScrollBar.set(mRect);
        drawColor(canvas, mColors.getColor(mEventHandler.holdHorizontalScrollBar() ? EditorColorScheme.SCROLL_BAR_THUMB_PRESSED : EditorColorScheme.SCROLL_BAR_THUMB), mRect);
    }

    /**
     * Draw text background for text
     *
     * @param canvas  Canvas to draw
     * @param offsetX Start x of text region
     * @param color   Color of text background
     */
    private void drawSelectedTextBackground(Canvas canvas, float offsetX, int color) {
        int startLine = mCursor.getLeftLine();
        int endLine = mCursor.getRightLine();
        int leftLine = startLine;
        int rightLine = endLine;
        if (startLine < getFirstVisibleLine()) {
            startLine = getFirstVisibleLine();
        }
        if (endLine > getLastVisibleLine()) {
            endLine = getLastVisibleLine();
        }
        if (startLine < 0) {
            startLine = 0;
        }
        if (endLine > getLineCount()) {
            endLine = getLineCount() - 1;
        }
        for (int line = startLine; line <= endLine; line++) {
            int start = 0, end = getText().getColumnCount(line);
            if (line == leftLine) {
                start = mCursor.getLeftColumn();
            }
            if (line == rightLine) {
                end = mCursor.getRightColumn();
            }
            mRect.top = getLineTop(line) - getOffsetY();
            mRect.bottom = mRect.top + getLineHeight();
            prepareLine(line);
            mRect.left = offsetX + measureText(mChars, 0, start);
            mRect.right = mRect.left + measureText(mChars, start, end - start) + (end == start ? mDpUnit * 10 : 0);
            drawColor(canvas, color, mRect);
        }
    }

    /**
     * Draw a underline for composing text
     *
     * @param canvas  Canvas to draw
     * @param offsetX The start x of text region
     * @param color   The color of underline
     */
    private void drawComposingTextUnderline(Canvas canvas, float offsetX, int color) {
        if (mConnection != null && mConnection.mComposingLine != -1) {
            int offY = getLineBottom(mConnection.mComposingLine) - getOffsetY();
            prepareLine(mConnection.mComposingLine);
            offsetX += measureText(mChars, 0, mConnection.mComposingStart);
            float width = measureText(mChars, mConnection.mComposingStart, mConnection.mComposingEnd - mConnection.mComposingStart);
            mRect.top = offY - getLineHeight() * 0.06f;
            mRect.bottom = offY;
            mRect.left = offsetX;
            mRect.right = offsetX + width;
            drawColor(canvas, color, mRect);
        }
    }

    /**
     * Draw a line as insert cursor
     *
     * @param canvas  Canvas to draw
     * @param offsetX Start x of text region
     * @param color   Color of cursor
     */
    private void drawCursor(Canvas canvas, int line, int column, float offsetX, int color) {
        if (isLineVisible(line)) {
            prepareLine(line);
            float width = measureText(mChars, 0, column);
            mRect.top = getLineTop(line) - getOffsetY();
            mRect.bottom = getLineBottom(line) - getOffsetY();
            mRect.left = offsetX + width;
            mRect.right = offsetX + width + mInsertSelWidth;
            drawColor(canvas, color, mRect);
        }
    }

    /**
     * Draw text for the view
     *
     * @param canvas       Canvas to draw
     * @param offsetX      Start x of text region
     * @param defaultColor Default color for no spans
     */
    private void drawText(Canvas canvas, float offsetX, int defaultColor) {
        TextAnalyzeResult colors = mSpanner == null ? null : mSpanner.getResult();
        if (colors == null || colors.getSpanMap().isEmpty()) {
            drawTextDirect(canvas, offsetX, defaultColor, getFirstVisibleLine());
        } else {
            drawTextLines(canvas, offsetX, colors);
        }
    }

    /**
     * Check whether the given character is a start sign for emoji
     *
     * @param ch Character to check
     * @return Whether this is leading a emoji
     */
    private static boolean isEmoji(char ch) {
        return ch == 0xd83c || ch == 0xd83d;
    }

    /**
     * Measure text width
     *
     * @param src   Source characters array
     * @param index Start index in array
     * @param count Count of characters
     * @return The width measured
     */
    private float measureText(char[] src, int index, int count) {
        float extraWidth;
        int tabCount = 0;
        for (int i = 0; i < count; i++) {
            if (src[index + i] == '\t') {
                tabCount++;
            }
        }
        if (count > 0 && isEmoji(src[index + count - 1])) {
            count--;
        }
        extraWidth = mSpaceWidth * (getTabWidth() - 1);
        return mPaint.measureText(src, index, count) + tabCount * extraWidth;
    }

    /**
     * Draw text on the given position
     *
     * @param canvas Canvas to draw
     * @param src    Source of characters
     * @param index  The index in array
     * @param count  Count of characters
     * @param offX   Offset x for paint
     * @param offY   Offset y for paint(baseline)
     */
    private void drawText(Canvas canvas, char[] src, int index, int count, float offX, float offY) {
        int end = index + count;
        int st = index;
        for (int i = index; i < end; i++) {
            if (src[i] == '\t') {
                canvas.drawText(src, st, i - st, offX, offY, mPaint);
                offX = offX + measureText(src, st, i - st + 1);
                st = i + 1;
            }
        }
        if (st < end) {
            canvas.drawText(src, st, end - st, offX, offY, mPaint);
        }
    }

    /**
     * Draw text lines
     */
    private void drawTextLines(Canvas canvas, float offsetX, TextAnalyzeResult colors) {
        EditorColorScheme cs = mColors;
        List<List<Span>> spanMap = colors.getSpanMap();
        int first = getFirstVisibleLine();
        int last = getLastVisibleLine();
        boolean selected = mCursor.isSelected();
        int leftLine = mCursor.getLeftLine();
        int rightLine = mCursor.getRightLine();
        int leftColumn = mCursor.getLeftColumn();
        int rightColumn = mCursor.getRightColumn();
        for (int line = first; line <= last; line++) {
            List<Span> spans = spanMap.size() > line ? spanMap.get(line) : new ArrayList<Span>();
            Span span = null;
            if (spans.isEmpty()) {
                spans.add(span = Span.obtain(0, EditorColorScheme.TEXT_NORMAL));
            }
            try {
                boolean hasSelectionOnLine = false;
                int start = 0, end = 0;
                if (isHighlightSelectedText() && selected && line >= leftLine && line <= rightLine) {
                    hasSelectionOnLine = true;
                    if (line == leftLine) {
                        start = leftColumn;
                    }
                    if (line == rightLine) {
                        end = rightColumn;
                    } else {
                        end = mText.getColumnCount(line);
                    }
                }
                drawTextLineWithSpans(canvas, offsetX, line, cs, spans, hasSelectionOnLine, start, end);
            } catch (IndexOutOfBoundsException e) {
                Log.w(LOG_TAG, "Exception in rendering line " + line, e);
            }
            if (span != null) {
                span.recycle();
            }
        }
    }

    private float measureTextRegion(char[] src, int index, int count) {
        float extraWidth;
        int tabCount = 0;
        for (int i = 0; i < count; i++) {
            if (src[index + i] == '\t') {
                tabCount++;
            }
        }
        extraWidth = mSpaceWidth * (getTabWidth() - 1);
        return mPaint.measureText(src, index, count) + tabCount * extraWidth;
    }

    private float measureTextRelatively(char[] chars, int endIndex, int lastIndex, float lastMeasureResult) {
        if (endIndex == lastIndex) {
            return lastMeasureResult;
        } else if (endIndex > lastIndex) {
            return lastMeasureResult + measureTextRegion(chars, lastIndex, endIndex - lastIndex);
        } else {
            return lastMeasureResult - measureTextRegion(chars, endIndex, lastIndex - endIndex);
        }
    }

    private int binaryFindCharIndex(float initialPosition, float targetOffset, int left, int right, char[] chars) {
        float measureResult = 0;
        int lastCommitMeasure = 0;
        int min = left, max = right;
        while (left < right) {
            int mid = (left + right) / 2;
            measureResult = measureTextRelatively(chars, mid, lastCommitMeasure, measureResult);
            lastCommitMeasure = mid;
            if (measureResult + initialPosition > targetOffset) {
                right = mid - 1;
            } else if (measureResult + initialPosition < targetOffset) {
                left = mid + 1;
            } else {
                return mid;
            }
        }
        return Math.max(min, Math.min(max, left));
    }

    /**
     * Draw single line
     */
    private void drawTextLineWithSpans(Canvas canvas, float offsetX, int line, EditorColorScheme cs, List<Span> spans, boolean hasSelectionOnLine, int selectionStart, int selectionEnd) {
        prepareLine(line);
        int columnCount = mText.getColumnCount(line);
        int minPaintChar = binaryFindCharIndex(offsetX, 0, 0, columnCount, mChars);
        int maxPaintChar = binaryFindCharIndex(offsetX, getWidth(), minPaintChar, columnCount, mChars);
        maxPaintChar = Math.min(maxPaintChar + 1, columnCount);
        float baseline = getLineBaseLine(line) - getOffsetY();
        int spanIndex = 0;
        while (spanIndex < spans.size()) {
            Span span = spans.get(spanIndex);
            if (span.column > minPaintChar) {
                spanIndex--;
                break;
            } else if (span.column == minPaintChar) {
                break;
            }
            spanIndex++;
        }
        if (spanIndex == spans.size() && spanIndex > 0) {
            spanIndex--;
        }
        spanIndex = Math.max(0, spanIndex);
        boolean firstPaint = true;
        boolean continueFlag = true;
        while (continueFlag && spanIndex < spans.size()) {
            Span span = spans.get(spanIndex);
            int startIndex = span.column;
            if (firstPaint) {
                firstPaint = false;
                startIndex = minPaintChar;
                offsetX += measureText(mChars, 0, startIndex);
            }
            int endIndex = spanIndex + 1 < spans.size() ? spans.get(spanIndex + 1).column : columnCount;
            if (maxPaintChar <= endIndex) {
                continueFlag = false;
                endIndex = maxPaintChar;
            }
            float width = measureText(mChars, startIndex, endIndex - startIndex);
            mPaint.setColor(cs.getColor(span.colorId));
            if (hasSelectionOnLine) {
                if (endIndex <= selectionStart || startIndex >= selectionEnd) {
                    drawText(canvas, mChars, startIndex, endIndex - startIndex, offsetX, baseline);
                } else {
                    if (startIndex <= selectionStart) {
                        if (endIndex >= selectionEnd) {
                            //Three regions
                            //startIndex - selectionStart
                            drawText(canvas, mChars, startIndex, selectionStart - startIndex, offsetX, baseline);
                            float deltaX = measureText(mChars, startIndex, selectionStart - startIndex);
                            //selectionStart - selectionEnd
                            mPaint.setColor(cs.getColor(EditorColorScheme.TEXT_SELECTED));
                            drawText(canvas, mChars, selectionStart, selectionEnd - selectionStart, offsetX + deltaX, baseline);
                            deltaX += measureText(mChars, selectionStart, selectionEnd - selectionStart);
                            //selectionEnd - endIndex
                            mPaint.setColor(cs.getColor(span.colorId));
                            drawText(canvas, mChars, selectionEnd, endIndex - selectionEnd, offsetX + deltaX, baseline);
                        } else {
                            //Two regions
                            //startIndex - selectionStart
                            drawText(canvas, mChars, startIndex, selectionStart - startIndex, offsetX, baseline);
                            //selectionStart - endIndex
                            mPaint.setColor(cs.getColor(EditorColorScheme.TEXT_SELECTED));
                            drawText(canvas, mChars, selectionStart, endIndex - selectionStart, offsetX + measureText(mChars, startIndex, selectionStart - startIndex), baseline);
                        }
                    } else {
                        //selectionEnd > startIndex > selectionStart
                        if (endIndex > selectionEnd) {
                            //Two regions
                            //selectionEnd - endIndex
                            drawText(canvas, mChars, selectionEnd, endIndex - selectionEnd, offsetX + measureText(mChars, startIndex, selectionEnd - startIndex), baseline);
                            //startIndex - selectionEnd
                            mPaint.setColor(cs.getColor(EditorColorScheme.TEXT_SELECTED));
                            drawText(canvas, mChars, startIndex, selectionEnd - startIndex, offsetX, baseline);
                        } else {
                            //One region
                            mPaint.setColor(cs.getColor(EditorColorScheme.TEXT_SELECTED));
                            drawText(canvas, mChars, startIndex, endIndex - startIndex, offsetX, baseline);
                        }
                    }
                }
            } else {
                drawText(canvas, mChars, startIndex, endIndex - startIndex, offsetX, baseline);
            }
            if (span.underlineColor != 0) {
                mRect.bottom = getLineBottom(line) - getOffsetY() - mDpUnit * 1;
                mRect.top = mRect.bottom - getLineHeight() * 0.06f;
                mRect.left = offsetX;
                mRect.right = offsetX + width;
                drawColor(canvas, span.underlineColor, mRect);
            }
            offsetX += width;
            spanIndex++;
        }
        float width = measureText(mChars, 0, columnCount);
        if (width > mMaxPaintX) {
            mMaxPaintX = width;
        }
    }

    /**
     * Draw text without any spans
     *
     * @param canvas    Canvas to draw
     * @param offsetX   Start x of text region
     * @param color     Color to draw text
     * @param startLine The start line to paint
     */
    private void drawTextDirect(Canvas canvas, float offsetX, int color, int startLine) {
        mPaint.setColor(color);
        int last = getLastVisibleLine();
        for (int i = startLine; i <= last; i++) {
            if (mText.getColumnCount(i) == 0) {
                continue;
            }
            prepareLine(i);
            drawText(canvas, mChars, 0, mText.getColumnCount(i), offsetX, getLineBaseLine(i) - getOffsetY());
            float width = measureText(mChars, 0, mText.getColumnCount(i)) + offsetX;
            if (width > mMaxPaintX) {
                mMaxPaintX = width;
            }
        }
    }

    /**
     * Read out characters to mChars for the given line
     *
     * @param line Line going to draw or measure
     */
    private void prepareLine(int line) {
        int length = mText.getColumnCount(line);
        if (length >= mChars.length) {
            mChars = new char[length + 100];
        }
        mText.getLineChars(line, mChars);
    }

    /**
     * Draw background for line
     *
     * @param canvas Canvas to draw
     * @param color  Color of background
     * @param line   Line index
     */
    private void drawLineBackground(Canvas canvas, int color, int line) {
        if (!isLineVisible(line)) {
            return;
        }
        mRect.top = getLineTop(line) - getOffsetY();
        mRect.bottom = getLineBottom(line) - getOffsetY();
        mRect.left = 0;
        mRect.right = mViewRect.right;
        drawColor(canvas, color, mRect);
    }

    /**
     * Paint current code block's start and end line's background
     *
     * @param canvas Canvas to draw
     */
    private void drawCurrentCodeBlockLabelBg(Canvas canvas) {
        if (mCursor.isSelected() || !mPaintLabel) {
            return;
        }
        int pos = mCursorPosition;
        if (pos == -1) {
            return;
        }
        List<BlockLine> blocks = mSpanner == null ? null : mSpanner.getResult().getBlocks();
        BlockLine block = (blocks != null && blocks.size() > pos) ? blocks.get(pos) : null;
        if (block != null) {
            int left = mCursor.getLeftLine();
            int color = mColors.getColor(EditorColorScheme.LINE_BLOCK_LABEL);
            if (block.startLine != left) {
                drawLineBackground(canvas, color, block.startLine);
            }
            if (block.endLine != left) {
                drawLineBackground(canvas, color, block.endLine);
            }
        }
    }

    /**
     * Draw the cursor line's background
     *
     * @param canvas Canvas to draw
     * @param color  Color for background
     */
    private void drawCurrentLineBackground(Canvas canvas, int color) {
        if (mCursor.isSelected()) {
            return;
        }
        int curr = mCursor.getLeftLine();
        drawLineBackground(canvas, color, curr);
    }

    /**
     * Draw line numbers on screen
     *
     * @param canvas  Canvas to draw
     * @param offsetX Start region of line number region
     * @param width   The width of line number region
     * @param color   Color of line number
     */
    private void drawLineNumbers(Canvas canvas, float offsetX, float width, int color) {
        if (width + offsetX <= 0) {
            return;
        }
        int first = getFirstVisibleLine();
        int last = getLastVisibleLine();
        mPaintOther.setTextAlign(mLineNumberAlign);
        mPaintOther.setColor(color);
        for (int i = first; i <= last; i++) {
            float y = (getLineBottom(i) + getLineTop(i)) / 2f - (mLineNumberMetrics.descent - mLineNumberMetrics.ascent) / 2f - mLineNumberMetrics.ascent - getOffsetY();
            switch (mLineNumberAlign) {
                case LEFT:
                    canvas.drawText(Integer.toString(i + 1), offsetX, y, mPaintOther);
                    break;
                case RIGHT:
                    canvas.drawText(Integer.toString(i + 1), offsetX + width, y, mPaintOther);
                    break;
                case CENTER:
                    canvas.drawText(Integer.toString(i + 1), offsetX + width / 2f, y, mPaintOther);
            }
        }
    }

    /**
     * Draw line number background
     *
     * @param canvas  Canvas to draw
     * @param offsetX Start x of line number region
     * @param width   Width of line number region
     * @param color   Color of line number background
     */
    private void drawLineNumberBackground(Canvas canvas, float offsetX, float width, int color) {
        float right = offsetX + width;
        if (right < 0) {
            return;
        }
        float left = Math.max(0f, offsetX);
        mRect.bottom = getHeight();
        mRect.top = 0;
        int offY = getOffsetY();
        if (offY < 0) {
            mRect.bottom = mRect.bottom - offY;
            mRect.top = mRect.top - offY;
        }
        mRect.left = left;
        mRect.right = right;
        drawColor(canvas, color, mRect);
    }

    /**
     * Draw divider line
     *
     * @param canvas  Canvas to draw
     * @param offsetX End x of line number region
     * @param color   Color to draw divider
     */
    private void drawDivider(Canvas canvas, float offsetX, int color) {
        float right = offsetX + mDividerWidth;
        if (right < 0) {
            return;
        }
        float left = Math.max(0f, offsetX);
        mRect.bottom = getHeight();
        mRect.top = 0;
        int offY = getOffsetY();
        if (offY < 0) {
            mRect.bottom = mRect.bottom - offY;
            mRect.top = mRect.top - offY;
        }
        mRect.left = left;
        mRect.right = right;
        drawColor(canvas, color, mRect);
    }

    /**
     * Get the width of line number region
     *
     * @return width of line number region
     */
    private float measureLineNumber() {
        int count = 0;
        int lineCount = getLineCount();
        while (lineCount > 0) {
            count++;
            lineCount /= 10;
        }
        float single = mPaintOther.measureText("0");
        return single * count * 1.01f;
    }

    /**
     * Draw rect on screen
     * Will not do any thing if color is zero
     *
     * @param canvas Canvas to draw
     * @param color  Color of rect
     * @param rect   Rect to draw
     */
    private void drawColor(Canvas canvas, int color, RectF rect) {
        if (color != 0) {
            mPaint.setColor(color);
            canvas.drawRect(rect, mPaint);
        }
    }

    /**
     * Draw rect on screen
     * Will not do any thing if color is zero
     *
     * @param canvas Canvas to draw
     * @param color  Color of rect
     * @param rect   Rect to draw
     */
    private void drawColor(Canvas canvas, int color, Rect rect) {
        if (color != 0) {
            mPaint.setColor(color);
            canvas.drawRect(rect, mPaint);
        }
    }

    /**
     * Commit a tab to cursor
     */
    private void commitTab() {
        if (mConnection != null) {
            mConnection.commitText("\t", 0);
        }
    }

    /**
     * Update the information of cursor
     * Such as the position of cursor on screen(For input method that can go to any position on screen like PC input method)
     *
     * @return The offset x of right cursor on view
     */
    protected float updateCursorAnchor() {
        CursorAnchorInfo.Builder builder = mAnchorInfoBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.reset();
            mMatrix.set(getMatrix());
            int[] b = new int[2];
            getLocationOnScreen(b);
            mMatrix.postTranslate(b[0], b[1]);
            builder.setMatrix(mMatrix);
            builder.setSelectionRange(mCursor.getLeft(), mCursor.getRight());
        }
        int l = mCursor.getRightLine();
        int column = mCursor.getRightColumn();
        prepareLine(l);
        boolean visible = true;
        float x = measureLineNumber() + mDividerMargin * 2 + mDividerWidth;
        x = x + measureText(mChars, 0, column);
        x = x - getOffsetX();
        if (x < 0) {
            visible = false;
            x = 0;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setInsertionMarkerLocation(x, getLineTop(l) - getOffsetY(), getLineBaseLine(l) - getOffsetY(), getLineBottom(l) - getOffsetY(), visible ? CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION : CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION);
            mInputMethodManager.updateCursorAnchorInfo(this, builder.build());
        } else {
            mInputMethodManager.updateCursor(this, (int) x, getLineTop(l) - getOffsetY(), (int) (x + mInsertSelWidth), getLineBottom(l) - getOffsetY());
        }
        return x;
    }

    /**
     * Make the right line visible
     */
    public void makeRightVisible() {
        ensurePositionVisible(getCursor().getRightLine(), getCursor().getRightColumn());
    }

    /**
     * Make the given character position visible
     *
     * @param line   Line of char
     * @param column Column of char
     */
    public void ensurePositionVisible(int line, int column) {
        float y = getLineHeight() * line;
        float minY = getOffsetY();
        float maxY = minY + getHeight();
        float targetY = minY;
        if (y < minY) {
            targetY = y;
        } else if (y + getLineHeight() > maxY) {
            targetY = y + getLineHeight() - getHeight();
        }
        float prefix_width = measureLineNumber() + mDividerMargin * 2 + mDividerWidth;
        float minX = getOffsetX();
        float maxX = minX + getWidth();
        float targetX = minX;
        prepareLine(line);
        float x = prefix_width + measureText(mChars, 0, column);
        float char_width = 2 * measureText(mChars, column, 1);
        if (x < minX) {
            targetX = x;
        } else if (x + char_width > maxX) {
            targetX = x + char_width - getWidth();
        }
        if (targetY == minY && targetX == minX) {
            invalidate();
            return;
        }
        boolean animation = true;
        if (System.currentTimeMillis() - mLastMakeVisible < 100) {
            animation = false;
        }
        mLastMakeVisible = System.currentTimeMillis();
        if (animation) {
            getScroller().forceFinished(true);
            getScroller().startScroll(getOffsetX(), getOffsetY(), (int) (targetX - getOffsetX()), (int) (targetY - getOffsetY()));
            if (Math.abs(getOffsetY() - targetY) > mDpUnit * 100) {
                mEventHandler.notifyScrolled();
            }
        } else {
            getScroller().startScroll(getOffsetX(), getOffsetY(), (int) (targetX - getOffsetX()), (int) (targetY - getOffsetY()), 0);
        }

        invalidate();
    }

    /**
     * Whether there is clip
     *
     * @return whether clip in clip board
     */
    public boolean hasClip() {
        return mClipboardManager.hasPrimaryClip();
    }

    /**
     * Get 1dp = ?px
     *
     * @return 1dp in pixel
     */
    protected float getDpUnit() {
        return mDpUnit;
    }

    /**
     * Get scroller from EventHandler
     * You would better not use it for your own scrolling
     *
     * @return The scroller
     */
    public OverScroller getScroller() {
        return mEventHandler.getScroller();
    }

    /**
     * Whether the position is over max Y position
     *
     * @param posOnScreen Y position on view
     * @return Whether over max Y
     */
    public boolean isOverMaxY(float posOnScreen) {
        return getPointLine(posOnScreen + getOffsetY()) > getLineCount();
    }

    /**
     * Whether the position is over max X position
     *
     * @param posOnScreen X position on view
     * @return Whether over max X
     */
    public boolean isOverMaxX(int line, float posOnScreen) {
        float xx = posOnScreen + getOffsetX() - mDividerMargin * 2 - mDividerWidth - measureLineNumber();
        prepareLine(line);
        return xx > (measureText(mChars, 0, mText.getColumnCount(line)) + 2 * mDpUnit);
    }

    /**
     * Get y position in scroll bounds's line's line
     *
     * @param yPos Y in scroll bounds
     * @return line
     */
    public int getPointLine(float yPos) {
        int r = (int) yPos / getLineHeight();
        return Math.max(r, 0);
    }

    /**
     * Get Y position on screen's line
     *
     * @param y Y on screen
     * @return Line
     */
    public int getPointLineOnScreen(float y) {
        return Math.min(getPointLine(y + getOffsetY()), getLineCount() - 1);
    }

    /**
     * Get column in line for offset x
     *
     * @param line Line
     * @param x    Offset x
     * @return Column in line
     */
    public int getPointColumn(int line, float x) {
        if (x < 0) {
            return 0;
        }
        if (line >= getLineCount()) {
            line = getLineCount() - 1;
        }
        prepareLine(line);
        int max = mText.getColumnCount(line);
        int index = binaryFindCharIndex(0, x, 0, max, mChars);
        float offset = measureText(mChars, 0, index);
        if (offset < x) {
            index++;
        }
        return Math.min(index, max);
    }

    /**
     * Get column for x offset on screen
     *
     * @param line Line
     * @param x    X offset on screen
     * @return Column in line
     */
    public int getPointColumnOnScreen(int line, float x) {
        float xx = x + getOffsetX() - mDividerMargin * 2 - mDividerWidth - measureLineNumber();
        return getPointColumn(line, xx);
    }

    /**
     * Get max scroll y
     *
     * @return max scroll y
     */
    public int getScrollMaxY() {
        return Math.max(0, getLineHeight() * getLineCount() - getHeight() / 2);
    }

    /**
     * Get max scroll x
     *
     * @return max scroll x
     */
    public int getScrollMaxX() {
        return (int) Math.max(0, mMaxPaintX - getWidth() / 2f);
    }

    /**
     * Use color {@link EditorColorScheme#TEXT_SELECTED} to draw selected text
     * instead of color specified by its span
     */
    public void setHighlightSelectedText(boolean highlightSelected) {
        mHighlightSelectedText = highlightSelected;
        invalidate();
    }

    /**
     * @see CodeEditor#setHighlightSelectedText(boolean)
     */
    public boolean isHighlightSelectedText() {
        return mHighlightSelectedText;
    }

    /**
     * Set tab width
     *
     * @param w tab width compared to space
     */
    public void setTabWidth(int w) {
        if (w < 1) {
            throw new IllegalArgumentException("width can not be under 1");
        }
        mTabWidth = w;
        if (mCursor != null) {
            mCursor.setTabWidth(mTabWidth);
        }
    }

    /**
     * Get EditorSearcher
     *
     * @return EditorSearcher
     */
    public EditorSearcher getSearcher() {
        return mSearcher;
    }

    /**
     * Format text in this thread
     * <strong>Note:</strong>Current thread must be UI thread
     *
     * @return Whether the format is successful
     * @deprecated Use {@link CodeEditor#formatCodeAsync()} instead
     */
    @Deprecated
    public boolean formatCode() {
        if (mListener != null && mListener.onRequestFormat(this, false)) {
            return false;
        }
        //Check thread
        invalidate();
        StringBuilder content = mText.toStringBuilder();
        int line = mCursor.getLeftLine();
        int column = mCursor.getLeftColumn();
        mText.replace(0, 0, getLineCount() - 1, mText.getColumnCount(getLineCount() - 1), mLanguage.format(content));
        getScroller().forceFinished(true);
        mACPanel.hide();
        setSelectionAround(line, column);
        mSpanner.analyze(mText);
        return true;
    }

    /**
     * Set selection around the given position
     * It will try to set selection as near as possible (Exactly the position if that position exists)
     */
    protected void setSelectionAround(int line, int column) {
        if (line < getLineCount()) {
            int columnCount = mText.getColumnCount(line);
            if (column > columnCount) {
                column = columnCount;
            }
            setSelection(line, column);
        } else {
            setSelection(getLineCount() - 1, mText.getColumnCount(getLineCount() - 1));
        }
    }

    /**
     * Format text Async
     *
     * @return Whether the format task is scheduled
     */
    public synchronized boolean formatCodeAsync() {
        if (mFormatThread != null || (mListener != null && mListener.onRequestFormat(this, true))) {
            return false;
        }
        mFormatThread = new FormatThread(mText, mLanguage, this);
        mFormatThread.start();
        return true;
    }

    /**
     * Get tab width
     *
     * @return tab width
     */
    public int getTabWidth() {
        return mTabWidth;
    }

    /**
     * Undo last action
     */
    public void undo() {
        mText.undo();
    }

    /**
     * Redo last action
     */
    public void redo() {
        mText.redo();
    }

    /**
     * Whether can undo
     *
     * @return whether can undo
     */
    public boolean canUndo() {
        return mText.canUndo();
    }

    /**
     * Whether can redo
     *
     * @return whether can redo
     */
    public boolean canRedo() {
        return mText.canRedo();
    }

    /**
     * Enable / disabled undo manager
     *
     * @param enabled Enable/Disable
     */
    public void setUndoEnabled(boolean enabled) {
        mUndoEnabled = enabled;
        if (mText != null) {
            mText.setUndoEnabled(enabled);
        }
    }

    /**
     * @return Enabled/Disabled
     * @see CodeEditor#setUndoEnabled(boolean)
     */
    public boolean isUndoEnabled() {
        return mUndoEnabled;
    }

    /**
     * Set whether drag mode
     * drag:no fling scroll
     *
     * @param drag Whether drag
     */
    public void setDrag(boolean drag) {
        mDrag = drag;
        if (drag && !mEventHandler.getScroller().isFinished()) {
            mEventHandler.getScroller().forceFinished(true);
        }
    }

    /**
     * @return Whether drag
     * @see CodeEditor#setDrag(boolean)
     */
    public boolean isDrag() {
        return mDrag;
    }

    /**
     * Start search action mode
     */
    public void beginSearchMode() {
        ActionMode.Callback callback = new ActionMode.Callback() {

            @Override
            public boolean onCreateActionMode(ActionMode p1, Menu p2) {
                p2.add(0, 0, 0, R.string.next).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                p2.add(0, 1, 0, R.string.last).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER);
                p2.add(0, 2, 0, R.string.replace).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER);
                p2.add(0, 3, 0, R.string.replaceAll).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER);
                SearchView sv = new SearchView(getContext());
                sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

                    @Override
                    public boolean onQueryTextSubmit(String text) {
                        getSearcher().gotoNext();
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String text) {
                        getSearcher().search(text);
                        return false;
                    }

                });
                p1.setCustomView(sv);
                sv.performClick();
                sv.setQueryHint(getContext().getString(R.string.text_to_search));
                sv.setIconifiedByDefault(false);
                sv.setIconified(false);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode p1, Menu p2) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(final ActionMode am, MenuItem p2) {
                switch (p2.getItemId()) {
                    case 1:
                        getSearcher().gotoLast();
                        break;
                    case 0:
                        getSearcher().gotoNext();
                        break;
                    case 2:
                    case 3:
                        final boolean replaceAll = p2.getItemId() == 3;
                        final EditText et = new EditText(getContext());
                        et.setHint(R.string.replacement);
                        new AlertDialog.Builder(getContext())
                                .setTitle(replaceAll ? R.string.replaceAll : R.string.replace)
                                .setView(et)
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.replace, new AlertDialog.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface p1, int p2) {
                                        if (replaceAll) {
                                            getSearcher().replaceAll(et.getText().toString());
                                        } else {
                                            getSearcher().replaceThis(et.getText().toString());
                                        }
                                        am.finish();
                                        p1.dismiss();
                                    }

                                })
                                .show();
                        break;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode p1) {
                getSearcher().stopSearch();
            }

        };
        startActionMode(callback);
    }

    /**
     * Set divider line's left and right margin
     *
     * @param dividerMargin Margin for divider line
     */
    public void setDividerMargin(float dividerMargin) {
        if (dividerMargin < 0) {
            throw new IllegalArgumentException("margin can not be under zero");
        }
        this.mDividerMargin = dividerMargin;
        invalidate();
    }

    /**
     * @return Margin of divider line
     * @see CodeEditor#setDividerMargin(float)
     */
    public float getDividerMargin() {
        return mDividerMargin;
    }

    /**
     * Set divider line's width
     *
     * @param dividerWidth Width of divider line
     */
    public void setDividerWidth(float dividerWidth) {
        if (dividerWidth < 0) {
            throw new IllegalArgumentException("width can not be under zero");
        }
        this.mDividerWidth = dividerWidth;
        invalidate();
    }

    /**
     * @return Width of divider line
     * @see CodeEditor#setDividerWidth(float)
     */
    public float getDividerWidth() {
        return mDividerWidth;
    }

    /**
     * Set line number's typeface
     *
     * @param typefaceLineNumber New typeface
     */
    public void setTypefaceLineNumber(Typeface typefaceLineNumber) {
        if (typefaceLineNumber == null) {
            typefaceLineNumber = Typeface.MONOSPACE;
        }
        mPaintOther.setTypeface(typefaceLineNumber);
        mLineNumberMetrics = mPaintOther.getFontMetricsInt();
        invalidate();
    }

    /**
     * Set text's typeface
     *
     * @param typefaceText New typeface
     */
    public void setTypefaceText(Typeface typefaceText) {
        if (typefaceText == null) {
            typefaceText = Typeface.DEFAULT;
        }
        mPaint.setTypeface(typefaceText);
        mTextMetrics = mPaint.getFontMetricsInt();
        invalidate();
    }

    /**
     * @return Typeface of line number
     * @see CodeEditor#setTypefaceLineNumber(Typeface)
     */
    public Typeface getTypefaceLineNumber() {
        return mPaintOther.getTypeface();
    }

    /**
     * @return Typeface of text
     * @see CodeEditor#setTypefaceText(Typeface)
     */
    public Typeface getTypefaceText() {
        return mPaint.getTypeface();
    }

    /**
     * Set line number align
     *
     * @param align Align for line number
     */
    public void setLineNumberAlign(Paint.Align align) {
        if (align == null) {
            align = Paint.Align.LEFT;
        }
        mLineNumberAlign = align;
        invalidate();
    }

    /**
     * @return Line number align
     * @see CodeEditor#setLineNumberAlign(Paint.Align)
     */
    public Paint.Align getLineNumberAlign() {
        return mLineNumberAlign;
    }

    /**
     * Widht for insert cursor
     *
     * @param width Cursor width
     */
    public void setCursorWidth(float width) {
        if (width < 0) {
            throw new IllegalArgumentException("width can not be under zero");
        }
        mInsertSelWidth = width;
        invalidate();
    }

    /**
     * Get Cursor
     * Internal method!
     *
     * @return Cursor of text
     */
    public Cursor getCursor() {
        return mCursor;
    }

    /**
     * Display soft input method for self
     */
    public void showSoftInput() {
        if (isEditable() && isEnabled()) {
            if (isInTouchMode()) {
                requestFocusFromTouch();
            }
            if (!hasFocus()) {
                requestFocus();
            }
            mInputMethodManager.showSoftInput(this, 0);
        }
    }

    /**
     * Hide soft input
     */
    public void hideSoftInput() {
        mInputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    /**
     * Get line count
     *
     * @return line count
     */
    public int getLineCount() {
        return mText.getLineCount();
    }

    /**
     * Get first visible line on screen
     *
     * @return first visible line
     */
    public int getFirstVisibleLine() {
        int j = Math.min(getOffsetY() / getLineHeight(), getLineCount() - 1);
        return Math.max(j, 0);
    }

    /**
     * Get last visible line on screen
     *
     * @return last visible line
     */
    public int getLastVisibleLine() {
        int l = Math.min((getOffsetY() + getHeight()) / getLineHeight(), getLineCount() - 1);
        return Math.max(l, 0);
    }

    /**
     * Whether this line is visible on screen
     *
     * @param line Line to check
     * @return Whether visible
     */
    public boolean isLineVisible(int line) {
        return (getFirstVisibleLine() <= line && line <= getLastVisibleLine());
    }

    /**
     * Get baseline directly
     *
     * @param line Line
     * @return baseline y offset
     */
    public int getLineBaseLine(int line) {
        return getLineHeight() * (line + 1) - mTextMetrics.descent;
    }

    /**
     * Get line height
     *
     * @return height of single line
     */
    public int getLineHeight() {
        return mTextMetrics.descent - mTextMetrics.ascent;
    }

    /**
     * Get line top y offset
     *
     * @param line Line
     * @return top y offset
     */
    public int getLineTop(int line) {
        return getLineHeight() * line;
    }

    /**
     * Get line bottom y offset
     *
     * @param line Line
     * @return Bottom y offset
     */
    public int getLineBottom(int line) {
        return getLineHeight() * (line + 1);
    }

    /**
     * Get scroll x
     *
     * @return scroll x
     */
    public int getOffsetX() {
        return mEventHandler.getScroller().getCurrX();
    }

    /**
     * Get scroll y
     *
     * @return scroll y
     */
    public int getOffsetY() {
        return mEventHandler.getScroller().getCurrY();
    }

    /**
     * Set whether text can be edited
     *
     * @param editable Editable
     */
    public void setEditable(boolean editable) {
        mEditable = editable;
        if (!editable) {
            hideSoftInput();
        }
    }

    /**
     * @return Whether editable
     * @see CodeEditor#setEditable(boolean)
     */
    public boolean isEditable() {
        return mEditable;
    }

    /**
     * Allow scale text size by thumb
     *
     * @param scale Whether allow
     */
    public void setCanScale(boolean scale) {
        mScale = scale;
    }

    /**
     * @return Whether allow to scale
     * @see CodeEditor#setCanScale(boolean)
     */
    public boolean canScale() {
        return mScale;
    }

    /**
     * Move the selection down
     * If the auto complete panel is shown,move the selection in panel to next
     */
    public void moveSelectionDown() {
        if (mACPanel.isShowing()) {
            mACPanel.moveDown();
            return;
        }
        Cursor c = getCursor();
        int line = c.getLeftLine();
        int column = c.getLeftColumn();
        int c_line = getText().getLineCount();
        if (line + 1 >= c_line) {
            setSelection(line, getText().getColumnCount(line));
        } else {
            int c_column = getText().getColumnCount(line + 1);
            if (column > c_column) {
                column = c_column;
            }
            setSelection(line + 1, column);
        }
    }

    /**
     * Move the selection up
     * If Auto complete panel is shown,move the selection in panel to last
     */
    public void moveSelectionUp() {
        if (mACPanel.isShowing()) {
            mACPanel.moveUp();
            return;
        }
        Cursor c = getCursor();
        int line = c.getLeftLine();
        int column = c.getLeftColumn();
        if (line - 1 < 0) {
            line = 1;
        }
        int c_column = getText().getColumnCount(line - 1);
        if (column > c_column) {
            column = c_column;
        }
        setSelection(line - 1, column);
    }

    /**
     * Move the selection left
     */
    public void moveSelectionLeft() {
        Cursor c = getCursor();
        int line = c.getLeftLine();
        int column = c.getLeftColumn();
        if (column - 1 >= 0) {
            int toLeft = 1;
            if (column - 2 >= 0) {
                char ch = mText.charAt(line, column - 2);
                if (isEmoji(ch)) {
                    column--;
                    toLeft++;
                }
            }
            setSelection(line, column - 1);
            if (mACPanel.isShowing()) {
                String prefix = mACPanel.getPrefix();
                if (prefix.length() > toLeft) {
                    prefix = prefix.substring(0, prefix.length() - toLeft);
                    mACPanel.setPrefix(prefix);
                } else {
                    mACPanel.hide();
                }
            }
            if (column - 1 <= 0) {
                mACPanel.hide();
            }
        } else {
            if (line == 0) {
                setSelection(0, 0);
            } else {
                int c_column = getText().getColumnCount(line - 1);
                setSelection(line - 1, c_column);
            }
        }
    }

    /**
     * Move the selection right
     */
    public void moveSelectionRight() {
        Cursor c = getCursor();
        int line = c.getLeftLine();
        int column = c.getLeftColumn();
        int c_column = getText().getColumnCount(line);
        if (column + 1 <= c_column) {
            char ch = mText.charAt(line, column);
            boolean emoji;
            if (emoji = isEmoji(ch)) {
                column++;
                if (column + 1 > c_column) {
                    column--;
                }
            }
            if (!emoji && mACPanel.isShowing()) {
                if (!mLanguage.isAutoCompleteChar(ch)) {
                    mACPanel.hide();
                } else {
                    String prefix = mACPanel.getPrefix() + ch;
                    mACPanel.setPrefix(prefix);
                }
            }
            setSelection(line, column + 1);
        } else {
            if (line + 1 == getLineCount()) {
                setSelection(line, c_column);
            } else {
                setSelection(line + 1, 0);
            }
        }
    }

    /**
     * Move selection to end of line
     */
    public void moveSelectionEnd() {
        int line = mCursor.getLeftLine();
        setSelection(line, getText().getColumnCount(line));
    }

    /**
     * Move selection to start of line
     */
    public void moveSelectionHome() {
        setSelection(mCursor.getLeftLine(), 0);
    }

    /**
     * Move selection to given position
     *
     * @param line   The line to move
     * @param column The column to move
     */
    public void setSelection(int line, int column) {
        if (column > 0 && isEmoji(mText.charAt(line, column - 1))) {
            column++;
            if (column > mText.getColumnCount(line)) {
                column--;
            }
        }
        mCursor.set(line, column);
        if (mHighlightCurrentBlock) {
            mCursorPosition = findCursorBlock();
        }
        updateCursorAnchor();
        updateSelection();
        ensurePositionVisible(line, column);
        mTextActionWindow.hide();
    }

    /**
     * Select all text
     */
    public void selectAll() {
        setSelectionRegion(0, 0, getLineCount() - 1, getText().getColumnCount(getLineCount() - 1));
    }

    /**
     * Set selection region with a call to {@link CodeEditor#makeRightVisible()}
     *
     * @param lineLeft    Line left
     * @param columnLeft  Column Left
     * @param lineRight   Line right
     * @param columnRight Column right
     */
    public void setSelectionRegion(int lineLeft, int columnLeft, int lineRight, int columnRight) {
        setSelectionRegion(lineLeft, columnLeft, lineRight, columnRight, true);
    }

    /**
     * Set selection region
     *
     * @param lineLeft         Line left
     * @param columnLeft       Column Left
     * @param lineRight        Line right
     * @param columnRight      Column right
     * @param makeRightVisible Whether make right cursor visible
     */
    public void setSelectionRegion(int lineLeft, int columnLeft, int lineRight, int columnRight, boolean makeRightVisible) {
        int start = getText().getCharIndex(lineLeft, columnLeft);
        int end = getText().getCharIndex(lineRight, columnRight);
        if (start == end) {
            setSelection(lineLeft, columnLeft);
            return;
        }
        if (start > end) {
            throw new IllegalArgumentException("start > end:start = " + start + " end = " + end + " lineLeft = " + lineLeft + " columnLeft = " + columnLeft + " lineRight = " + lineRight + " columnRight = " + columnRight);
        }
        if (columnLeft > 0) {
            int column = columnLeft - 1;
            char ch = mText.charAt(lineLeft, column);
            if (isEmoji(ch)) {
                columnLeft++;
                if (columnLeft > mText.getColumnCount(lineLeft)) {
                    columnLeft--;
                }
            }
        }
        if (columnRight > 0) {
            int column = columnRight;
            char ch = mText.charAt(lineRight, column);
            if (isEmoji(ch)) {
                columnRight++;
                if (columnRight > mText.getColumnCount(lineRight)) {
                    columnRight--;
                }
            }
        }
        mCursor.setLeft(lineLeft, columnLeft);
        mCursor.setRight(lineRight, columnRight);
        updateCursorAnchor();
        updateSelection();
        if (makeRightVisible) {
            ensurePositionVisible(lineRight, columnRight);
        } else {
            invalidate();
        }
    }

    /**
     * Move to next page
     */
    public void movePageDown() {
        mEventHandler.onScroll(null, null, 0, getHeight());
        mACPanel.hide();
    }

    /**
     * Move to previous page
     */
    public void movePageUp() {
        mEventHandler.onScroll(null, null, 0, -getHeight());
        mACPanel.hide();
    }

    /**
     * Paste text from clip board
     */
    public void pasteText() {
        try {
            if (!mClipboardManager.hasPrimaryClip() || mClipboardManager.getPrimaryClip() == null) {
                return;
            }
            ClipData.Item data = mClipboardManager.getPrimaryClip().getItemAt(0);
            CharSequence text = data.getText();
            if (text != null && mConnection != null) {
                mConnection.commitText(text, 0);
            }
            cursorChangeExternal();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Copy text to clip board
     */
    public void copyText() {
        try {
            if (mCursor.isSelected()) {
                String clip = getText().subContent(mCursor.getLeftLine(),
                        mCursor.getLeftColumn(),
                        mCursor.getRightLine(),
                        mCursor.getRightColumn()).toString();
                mClipboardManager.setPrimaryClip(ClipData.newPlainText(clip, clip));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Copy text to clipboard and delete them
     */
    public void cutText() {
        copyText();
        if (mCursor.isSelected()) {
            mCursor.onDeleteKeyPressed();
            cursorChangeExternal();
        }
    }

    /**
     * Set the editor's text displaying
     *
     * @param text the new text you want to display
     */
    public void setText(CharSequence text) {
        if (text == null) {
            text = "";
        }

        if (mText != null) {
            mText.removeContentListener(this);
        }
        mText = new Content(text);
        mCursor = mText.getCursor();
        mCursor.setAutoIndent(mAutoIndent);
        mCursor.setLanguage(mLanguage);
        mEventHandler.reset();
        mText.addContentListener(this);
        mText.setUndoEnabled(mUndoEnabled);

        if (mSpanner != null) {
            mSpanner.setCallback(null);
            mSpanner.shutdown();
        }
        mSpanner = new TextAnalyzer(mLanguage.getAnalyzer());
        mSpanner.setCallback(this);

        TextAnalyzeResult colors = mSpanner.getResult();
        colors.getSpanMap().clear();
        mSpanner.analyze(getText());

        mMaxPaintX = 0;
        requestLayout();

        if (mListener != null) {
            mListener.onNewTextSet(this);
        }
        if (mInputMethodManager != null) {
            mInputMethodManager.restartInput(this);
        }
        invalidate();
    }

    /**
     * @return Text displaying, the result is read-only. You should not make changes to this obejct as it is used internally
     * @see CodeEditor#setText(CharSequence)
     */
    public Content getText() {
        return mText;
    }

    /**
     * Set the editor's text size in sp unit. This value must be > 0
     *
     * @param textSize the editor's text size in <strong>Sp</strong> units.
     */
    public void setTextSize(float textSize) {
        Context context = getContext();
        Resources res;

        if (context == null) {
            res = Resources.getSystem();
        } else {
            res = context.getResources();
        }

        setTextSizePx(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSize, res.getDisplayMetrics()));
    }

    /**
     * Get the paint of the editor
     * Users should not change text size and other arguments that are related to text measure by the object
     *
     * @return The paint which is used by the editor now
     */
    public Paint getPaint() {
        return mPaint;
    }

    /**
     * Get the ColorScheme object of this editor
     * You can config colors of some regions, texts and highlight text
     *
     * @return ColorScheme object using
     */
    public EditorColorScheme getColorScheme() {
        return mColors;
    }

    /**
     * Move selection to line start with scrolling
     *
     * @param line Line to jump
     */
    public void jumpToLine(int line) {
        setSelection(line, 0);
    }

    /**
     * Get spans
     *
     * @return spans
     */
    public TextAnalyzeResult getTextColor() {
        return mSpanner.getResult();
    }

    /**
     * Hide auto complete panel if shown
     */
    public void hideAutoCompletePanel() {
        mACPanel.hide();
    }

    /**
     * Get cursor code block index
     *
     * @return index of cursor's code block
     */
    public int getBlockIndex() {
        return mCursorPosition;
    }


    //------------------------Internal Callbacks------------------------------

    /**
     * Called by ColorScheme to notify invalidate
     *
     * @param type Color type changed
     */
    protected void onColorUpdated(int type) {
        if (type == EditorColorScheme.AUTO_COMP_PANEL_BG || type == EditorColorScheme.AUTO_COMP_PANEL_CORNER) {
            if (mACPanel != null)
                mACPanel.applyColor();
            return;
        }
        invalidate();
    }

    /**
     * Get the InputMethodManager using
     */
    protected InputMethodManager getInputMethodManager() {
        return mInputMethodManager;
    }

    /**
     * Called by CodeEditorInputConnection
     */
    protected void onCloseConnection() {
        invalidate();
    }

    //------------------------Overrides---------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        try {
            drawView(canvas);
        } catch (Throwable t) {
            StringBuilder sb = mErrorBuilder;
            sb.setLength(0);
            sb.append(t.toString());
            for (Object o : t.getStackTrace()) {
                sb.append('\n').append(o);
            }
            Toast.makeText(getContext(), sb, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public AccessibilityNodeInfo createAccessibilityNodeInfo() {
        AccessibilityNodeInfo node = super.createAccessibilityNodeInfo();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            node.setEditable(isEditable());
            node.setTextSelection(mCursor.getLeft(), mCursor.getRight());
        }
        node.setScrollable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            node.setInputType(InputType.TYPE_CLASS_TEXT);
            node.setMultiLine(true);
        }
        node.setText(getText().toStringBuilder());
        node.setLongClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_COPY);
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CUT);
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE);
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT);
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                node.addAction(AccessibilityNodeInfo.ACTION_COPY);
                node.addAction(AccessibilityNodeInfo.ACTION_CUT);
                node.addAction(AccessibilityNodeInfo.ACTION_PASTE);
            }
            node.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            node.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }
        return node;
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        switch (action) {
            case AccessibilityNodeInfo.ACTION_COPY:
                copyText();
                return true;
            case AccessibilityNodeInfo.ACTION_CUT:
                copyText();
                mCursor.onDeleteKeyPressed();
                return true;
            case AccessibilityNodeInfo.ACTION_PASTE:
                pasteText();
                return true;
            case AccessibilityNodeInfo.ACTION_SET_TEXT:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setText(arguments.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE));
                    return true;
                }
                return false;
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:
                movePageDown();
                return true;
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:
                movePageUp();
                return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return isEnabled() && isEditable();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (!isEditable() || !isEnabled()) {
            return null;
        }
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
        outAttrs.initialSelStart = getCursor() != null ? getCursor().getLeft() : 0;
        outAttrs.initialSelEnd = getCursor() != null ? getCursor().getRight() : 0;
        mConnection.reset();
        return mConnection;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        boolean handling = mEventHandler.handlingMotions();
        boolean res = mEventHandler.onTouchEvent(event);
        boolean res2 = false;
        boolean res3 = mScaleDetector.onTouchEvent(event);
        if (!handling) {
            res2 = mBasicDetector.onTouchEvent(event);
        }
        
        return (res3 || res2 || res);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DEL:
                case KeyEvent.KEYCODE_FORWARD_DEL:
                    if (mConnection != null) {
                        mConnection.deleteSurroundingText(0, 0);
                        cursorChangeExternal();
                    }
                    return true;
                case KeyEvent.KEYCODE_ENTER:
                    if (mACPanel.isShowing()) {
                        mACPanel.select();
                        return true;
                    }
                    mConnection.commitText("\n", 0);
                    cursorChangeExternal();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    moveSelectionDown();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    moveSelectionUp();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    moveSelectionLeft();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    moveSelectionRight();
                    return true;
                case KeyEvent.KEYCODE_MOVE_END:
                    moveSelectionEnd();
                    return true;
                case KeyEvent.KEYCODE_MOVE_HOME:
                    moveSelectionHome();
                    return true;
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    movePageDown();
                    return true;
                case KeyEvent.KEYCODE_PAGE_UP:
                    movePageUp();
                    return true;
                case KeyEvent.KEYCODE_TAB:
                    commitTab();
                    return true;
                case KeyEvent.KEYCODE_PASTE:
                    pasteText();
                    return true;
                case KeyEvent.KEYCODE_COPY:
                    copyText();
                    return true;
                case KeyEvent.KEYCODE_SPACE:
                    getCursor().onCommitText(" ");
                    cursorChangeExternal();
                    return true;
                default:
                    if (event.isCtrlPressed() && !event.isAltPressed()) {
                        switch (keyCode) {
                            case KeyEvent.KEYCODE_V:
                                pasteText();
                                return true;
                            case KeyEvent.KEYCODE_C:
                                copyText();
                                return true;
                            case KeyEvent.KEYCODE_X:
                                cutText();
                                return true;
                            case KeyEvent.KEYCODE_A:
                                selectAll();
                                return true;
                            case KeyEvent.KEYCODE_Z:
                                undo();
                                return true;
                            case KeyEvent.KEYCODE_Y:
                                redo();
                                return true;
                        }
                    } else if (!event.isCtrlPressed() && !event.isAltPressed()) {
                        if (event.isPrintingKey()) {
                            getCursor().onCommitText(new String(Character.toChars(event.getUnicodeChar(event.getMetaState()))));
                            cursorChangeExternal();
                        } else {
                            return super.onKeyDown(keyCode, event);
                        }
                        return true;
                    }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean warn = false;
        //Fill the horizontal layout if WRAP_CONTENT mode
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
            warn = true;
        }
        //Fill the vertical layout if WRAP_CONTENT mode
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY);
            warn = true;
        }
        if (warn) {
            Log.i(LOG_TAG, "onMeasure():Code editor does not support wrap_content mode when measuring.It will just fill the whole space.");
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL) {
            float v_scroll = -event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (v_scroll != 0) {
                mEventHandler.onScroll(event, event, 0, v_scroll * 20);
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
        super.onSizeChanged(w, h, oldWidth, oldHeight);
        mViewRect.right = w;
        mViewRect.bottom = h;
    }

    @Override
    public void computeScroll() {
        if (mEventHandler.getScroller().computeScrollOffset()) {
            invalidate();
        }
        super.computeScroll();
    }

    @Override
    public void beforeReplace(Content content) {
        mWait = true;
        if (mListener != null) {
            mListener.beforeReplace(this, content);
        }
    }

    private static int findSpanIndexFor(List<Span> spans, int initialPosition, int targetCol) {
        for (int i = initialPosition; i < spans.size(); i++) {
            if (spans.get(i).column >= targetCol) {
                return i;
            }
        }
        return -1;
    }

    private void shiftSpansOnSingleLineInsert(int line, int startCol, int endCol) {
        List<List<Span>> spans = mSpanner.getResult().getSpanMap();
        if (spans == null || spans.isEmpty()) {
            return;
        }
        List<Span> spanList = spans.get(line);
        int index = findSpanIndexFor(spanList, 0, startCol);
        if (index == -1) {
            return;
        }
        int originIndex = index;
        // Shift spans after insert position
        int delta = endCol - startCol;
        while (index < spanList.size()) {
            spanList.get(index++).column += delta;
        }
        // Add extra span for line start
        if (originIndex == 0) {
            Span first = spanList.get(0);
            if (first.colorId == EditorColorScheme.TEXT_NORMAL && first.underlineColor == 0) {
                first.column = 0;
            } else {
                spanList.add(0, Span.obtain(0, EditorColorScheme.TEXT_NORMAL));
            }
        }
    }

    private void shiftSpansOnMultiLineInsert(int startLine, int startColumn, int endLine, int endColumn) {
        List<List<Span>> map = mSpanner.getResult().getSpanMap();
        // Find extended span
        List<Span> startLineSpans = map.get(startLine);
        int extendedSpanIndex = findSpanIndexFor(startLineSpans, 0, startColumn);
        if (extendedSpanIndex == -1) {
            extendedSpanIndex = startLineSpans.size() - 1;
        }
        if (startLineSpans.get(extendedSpanIndex).column > startColumn) {
            extendedSpanIndex--;
        }
        Span extendedSpan;
        if (extendedSpanIndex < 0 || extendedSpanIndex >= startLineSpans.size()) {
            extendedSpan = Span.obtain(0, EditorColorScheme.TEXT_NORMAL);
        } else {
            extendedSpan = startLineSpans.get(extendedSpanIndex);
        }
        // Create map link for new lines
        for (int i = 0; i < endLine - startLine; i++) {
            List<Span> list = new ArrayList<>();
            list.add(extendedSpan.copy().setColumn(0));
            map.add(startLine + 1, list);
        }
        // Add original spans to new line
        List<Span> endLineSpans = map.get(endLine);
        if (endColumn == 0 && extendedSpanIndex + 1 < startLineSpans.size()) {
            endLineSpans.clear();
        }
        int delta = Integer.MIN_VALUE;
        while (extendedSpanIndex + 1 < startLineSpans.size()) {
            Span span = startLineSpans.remove(extendedSpanIndex + 1);
            if (delta == Integer.MIN_VALUE) {
                delta = span.column;
            }
            endLineSpans.add(span.setColumn(span.column - delta + endColumn));
        }
    }

    private void shiftSpansOnSingleLineDelete(int line, int startCol, int endCol) {
        List<List<Span>> spans = mSpanner.getResult().getSpanMap();
        if (spans == null || spans.isEmpty()) {
            return;
        }
        List<Span> spanList = spans.get(line);
        int startIndex = findSpanIndexFor(spanList, 0, startCol);
        if (startIndex == -1) {
            //No span is to be updated
            return;
        }
        int endIndex = findSpanIndexFor(spanList, startIndex, endCol);
        if (endIndex == -1) {
            endIndex = spanList.size();
        }
        // Remove spans inside delete text
        int removeCount = endIndex - startIndex;
        for (int i = 0; i < removeCount; i++) {
            spanList.remove(startIndex).recycle();
        }
        // Shift spans
        int delta = endCol - startCol;
        while (startIndex < spanList.size()) {
            spanList.get(startIndex).column -= delta;
            startIndex++;
        }
        // Ensure there is span
        if (spanList.isEmpty() || spanList.get(0).column != 0) {
            spanList.add(0, Span.obtain(0, EditorColorScheme.TEXT_NORMAL));
        }
        // Remove spans with length 0
        for (int i = 0; i + 1 < spanList.size(); i++) {
            if (spanList.get(i).column >= spanList.get(i + 1).column) {
                spanList.remove(i).recycle();
                i--;
            }
        }
    }

    private void shiftSpansOnMultiLineDelete(int startLine, int startColumn, int endLine, int endColumn) {
        List<List<Span>> map = mSpanner.getResult().getSpanMap();
        int lineCount = endLine - startLine - 1;
        // Remove unrelated lines
        while (lineCount > 0) {
            Span.recycleAll(map.remove(startLine + 1));
            lineCount--;
        }
        // Clean up start line
        List<Span> startLineSpans = map.get(startLine);
        int index = startLineSpans.size() - 1;
        while (index > 0) {
            if (startLineSpans.get(index).column >= startColumn) {
                startLineSpans.remove(index).recycle();
                index--;
            } else {
                break;
            }
        }
        // Shift end line
        List<Span> endLineSpans = map.get(startLine + 1);
        while (endLineSpans.size() > 1) {
            Span first = endLineSpans.get(0);
            if (first.column >= endColumn) {
                break;
            } else {
                int spanEnd = endLineSpans.get(1).column;
                if (spanEnd <= endColumn) {
                    endLineSpans.remove(first);
                    first.recycle();
                } else {
                    break;
                }
            }
        }
        for (int i = 0; i < endLineSpans.size(); i++) {
            Span span = endLineSpans.get(i);
            if (span.column < endColumn) {
                span.column = 0;
            } else {
                span.column -= endColumn;
            }
        }
    }

    private boolean isSpanMapPrepared() {
        List<List<Span>> map = mSpanner.getResult().getSpanMap();
        return (map != null && map.size() != 0);
    }

    @Override
    public void afterInsert(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence insertedContent) {
        if (isSpanMapPrepared()) {
            if (startLine == endLine) {
                shiftSpansOnSingleLineInsert(startLine, startColumn, endColumn);
            } else {
                shiftSpansOnMultiLineInsert(startLine, startColumn, endLine, endColumn);
            }
        }
        mWait = false;
        updateCursor();
        if (endColumn == 0 || startLine != endLine) {
            mACPanel.hide();
            makeRightVisible();
            mSpanner.analyze(mText);
            mEventHandler.hideInsertHandle();
            if (mListener != null) {
                mListener.afterInsert(this, mText, startLine, startColumn, endLine, endColumn, insertedContent);
            }
            return;
        } else {
            int end = endColumn;
            while (endColumn > 0) {
                if (mLanguage.isAutoCompleteChar(content.charAt(endLine, endColumn - 1))) {
                    endColumn--;
                } else {
                    break;
                }
            }
            String line = content.getLineString(endLine);
            if (end == endColumn) {
                mACPanel.hide();
                makeRightVisible();
                mSpanner.analyze(mText);
                mEventHandler.hideInsertHandle();
                if (mListener != null) {
                    mListener.afterInsert(this, mText, startLine, startColumn, endLine, endColumn, insertedContent);
                }
                return;
            }
            String prefix = line.substring(endColumn, end);
            mACPanel.setPrefix(prefix);
        }
        applyNewPanelPosition();
        //mACPanel.show();
        makeRightVisible();
        mSpanner.analyze(mText);
        mEventHandler.hideInsertHandle();
        if (mListener != null) {
            mListener.afterInsert(this, mText, startLine, startColumn, endLine, endColumn, insertedContent);
        }
    }

    private void applyNewPanelPosition() {
        float panelX = updateCursorAnchor() + mDpUnit * 20;
        float panelY = getLineBottom(mCursor.getRightLine()) - getOffsetY() + getLineHeight() / 2f;
        float restY = getHeight() - panelY;
        if (restY > mDpUnit * 200) {
            restY = mDpUnit * 200;
        } else if (restY < mDpUnit * 100) {
            float offset = 0;
            while (restY < mDpUnit * 100) {
                restY += getLineHeight();
                panelY -= getLineHeight();
                offset += getLineHeight();
            }
            getScroller().startScroll(getOffsetX(), getOffsetY(), (int) offset, 0, 0);
        }
        if (mACPanel.getY() != panelY) {
            mACPanel.hide();
        }
        mACPanel.setExtendedX(panelX);
        mACPanel.setExtendedY(panelY);
        if (getWidth() < 500 * mDpUnit) {
            //Open center mode
            mACPanel.setWidth(getWidth() * 7 / 8);
            mACPanel.setExtendedX(getWidth() / 8f / 2f);
        } else {
            mACPanel.setWidth(getWidth() / 3);
        }
        mACPanel.setHeight((int) restY);
        mACPanel.setMaxHeight((int) restY);
        mACPanel.updatePosition();
    }

    @Override
    public void afterDelete(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence deletedContent) {
        if (isSpanMapPrepared()) {
            if (startLine == endLine) {
                shiftSpansOnSingleLineDelete(startLine, startColumn, endColumn);
            } else {
                shiftSpansOnMultiLineDelete(startLine, startColumn, endLine, endColumn);
            }
        }
        updateCursor();
        if (mConnection.mComposingLine == -1 && mACPanel.isShowing()) {
            if (startLine != endLine || startColumn != endColumn - 1) {
                mACPanel.hide();
            }
            String prefix = mACPanel.getPrefix();
            if (prefix == null || prefix.length() - 1 == 0) {
                mACPanel.hide();
            } else {
                prefix = prefix.substring(0, prefix.length() - 1);
                applyNewPanelPosition();
                mACPanel.setPrefix(prefix);
            }
        }
        if (!mWait) {
            updateCursorAnchor();
            makeRightVisible();
            mSpanner.analyze(mText);
            mEventHandler.hideInsertHandle();
        }
        if (mListener != null) {
            mListener.afterDelete(this, mText, startLine, startColumn, endLine, endColumn, deletedContent);
        }
    }

    @Override
    public void onFormatFail(final Throwable throwable) {
        if (mListener != null && !mListener.onFormatFail(this, throwable)) {
            post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getContext(), throwable.toString(), Toast.LENGTH_SHORT).show();
                }
            });
        }
        mFormatThread = null;
    }

    @Override
    public void onFormatSucceed(CharSequence originalText, final CharSequence newText) {
        if (mListener != null) {
            mListener.onFormatSucceed(this);
        }
        mFormatThread = null;
        if (originalText != mText) {
            post(new Runnable() {
                @Override
                public void run() {
                    mText.beginBatchEdit();
                    int line = mCursor.getLeftLine();
                    int column = mCursor.getLeftColumn();
                    mText.delete(0, 0, getLineCount() - 1, mText.getColumnCount(getLineCount() - 1));
                    mText.insert(0, 0, newText);
                    mText.endBatchEdit();
                    getScroller().forceFinished(true);
                    mACPanel.hide();
                    setSelectionAround(line, column);
                    mSpanner.analyze(mText);
                }
            });
        }
    }

    @Override
    public void onAnalyzeDone(TextAnalyzer provider) {
        if (mHighlightCurrentBlock) {
            mCursorPosition = findCursorBlock();
        }
        postInvalidate();
        long lastAnalyzeThreadTime = System.currentTimeMillis() - provider.mOpStartTime;
        if(DEBUG)
            Log.d(LOG_TAG, "Highlight cost time:" + lastAnalyzeThreadTime);
    }

}
