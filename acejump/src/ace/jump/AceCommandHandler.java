package ace.jump;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;

import acejump.Activator;

public class AceCommandHandler extends AbstractHandler {
	private boolean drawNow = false;
	private char jumpTargetChar;
    private MarkerCollection _markerCollection = new MarkerCollection();

    public static final char INFINITE_JUMP_CHAR = '/';
    private static final String MARKER_CHARSET = "asdfjeghiybcmnopqrtuvwkl";

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		final ITextEditor te;
		final ISourceViewer sv;
		final StyledText st;

		if (    null == (te = getActiveTextEditor())
		     || null == (te.getSelectionProvider().getSelection())
		     || null == (sv = getSourceViewer(te))
	         || null == (st = getStyledTextFromTextEditor(te))) {
			return null;
		}

        _markerCollection.clear();

		add_paint_listener__to__draw_jump_target_markers(st, sv);

		final Shell shell = new Shell(Display.getDefault(), SWT.MODELESS);
        shell.setBounds(1, 1, 1, 1);

		new org.eclipse.swt.widgets.Canvas(shell, SWT.ALPHA).addKeyListener(new KeyListener() {
		    @Override public void keyReleased(KeyEvent e) {}
		    @Override public void keyPressed(KeyEvent e) {
		    	if (Character.isISOControl(e.character)) {
		    		return;
		    	}

		        if (drawNow) {
		        	if (e.character >= 'a' && e.character <= 'z') {
						boolean jumpFinished = selectOrJumpNow(e.character, st, sv);
                        if (jumpFinished) {
                            drawNow = false;
                            shell.close();
                        }
		        	}
				} else {
                    drawNow = e.keyCode != 27;
                    jumpTargetChar = e.character;
                    if (e.keyCode == 27) {
                        shell.close();
                    }
                }
                st.redraw();
            }
		});

		shell.open();
		return null;
	}

	protected boolean selectOrJumpNow(char ch, StyledText st, ISourceViewer sv) {
        ArrayList<Integer> offsets = _markerCollection.getOffsetsOfKey(ch);

        if (!offsets.isEmpty()) {
            if (offsets.size() > 1) {
                _markerCollection.clear();
                sort_offsets_by_distance_to_caret(offsets, st, sv);
                assign_marker_to(offsets);
                st.redraw();
                return false;
            } else {
                st.setSelection(offsets.get(0));
            }
        }

        return true;
	}


    void sort_offsets_by_distance_to_caret(List<Integer> offsets, final StyledText st, final ISourceViewer sv) {
        int caretOffset =  ((ITextViewerExtension5) sv).modelOffset2WidgetOffset(st.getCaretOffset());
        Collections.sort(offsets, new Comparator<Integer>() {
            @Override
            public int compare(Integer oA, Integer oB) {
                int distA = Math.abs(oA - caretOffset);
                int distB = Math.abs(oB - caretOffset);
                return distA == distB ? oA - oB : distA - distB;
            }
        });
    }


    private void add_paint_listener__to__draw_jump_target_markers(final StyledText st, final ISourceViewer sv) {
		if (alreadyHasPaintListenerFor(st))
			return;

		PaintListener pl = new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
                if (_markerCollection.isEmpty()) {
                    List<Integer> offsets = get_offsets_of_char(st, sv);
                    sort_offsets_by_distance_to_caret(offsets, st, sv);
                    assign_marker_to(offsets);
                }

				if (drawNow) {
                    draw_jump_target_markers(e.gc, st, sv);
                }
			}

            private void draw_jump_target_markers(GC gc, final StyledText st, final ISourceViewer sv) {
                HashSet<Integer> firstJumpOffsets = new HashSet<Integer>();

                for (Marker marker : _markerCollection.values()) {
                    for (int offset : marker.getOffsets()) {
                        firstJumpOffsets.add(offset);
                        drawMarkerAt(offset, gc, st, marker.getMarkerChar(), SWT.COLOR_RED);
                    }
                }

                for (Marker marker : _markerCollection.values()) {
                    if (marker.getMarker().length() == 1 || marker.isMappingToMultipleOffset()) {
                        continue;
                    }

                    boolean alreadyHasFirstJumpCharInPlace = firstJumpOffsets.contains(marker.getOffset()+1);

                    //TODO:
                    //boolean isAtLineEnd = isLineEndOffset(marker.getOffset(), _editor.getDocument());
                    //if (alreadyHasFirstJumpCharInPlace && !isAtLineEnd) {

                    if (alreadyHasFirstJumpCharInPlace) {
                        continue;
                    }

                    for (int offset : marker.getOffsets()) {
                        drawMarkerAt(offset+1, gc, st, marker.getMarker().charAt(1), SWT.COLOR_BLUE);
                    }
                }
            }

            List<Integer> get_offsets_of_char(final StyledText st, final ISourceViewer sv) {
                List<Integer> offsets = new ArrayList<Integer>();

                int start = ((ITextViewerExtension5) sv).modelOffset2WidgetOffset(sv.getTopIndexStartOffset());
				int end = ((ITextViewerExtension5) sv).modelOffset2WidgetOffset(sv.getBottomIndexEndOffset());
				String src = st.getText(start, end);

                int caretOffset =  ((ITextViewerExtension5) sv).modelOffset2WidgetOffset(st.getCaretOffset());

                for (int i = 0; i < src.length(); ++i) {
                    if (is_jump_candidate(src, i)) {
                        int offset = start + i;
                        if ( /*char_is_at_caret_and_should_ignore =*/ caretOffset == offset) {
                            continue;
                        }
                        offsets.add(offset);
                    }
                }

                return offsets;
            }

			private boolean is_jump_candidate(String src, int i) {
				char c = src.charAt(i);

                boolean spaceMatchedTheNewlineChar = c == '\n' && jumpTargetChar == ' ';
                if (spaceMatchedTheNewlineChar) {
                    return true;
                }

				if (Character.toLowerCase(c) == Character.toLowerCase(jumpTargetChar)) {
                    if (i > 0) {
                        char prev = src.charAt(i - 1);
                        boolean isAtWordBoundary = !Character.isLetter(prev);
                        boolean isAtCamelCaseBoundary = Character.isUpperCase(c) && Character.isLowerCase(prev);
                        return isAtWordBoundary || isAtCamelCaseBoundary;
                    }
                    return true;
				}
				return false;
			}

            private void drawMarkerAt(int offset, GC gc, StyledText st, char marker, int foregroundColor) {
				String word = Character.toString(marker);

				Rectangle bounds = st.getTextBounds(offset, offset);
				gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
				Point textExtent = gc.textExtent(word);

				int ex = 0;
				int dex = 2 * ex;
				gc.fillRoundRectangle(bounds.x - ex, bounds.y - ex, textExtent.x + dex, textExtent.y + dex, 0, 0);

				gc.setForeground(Display.getDefault().getSystemColor(foregroundColor));
				gc.drawString(word, bounds.x, bounds.y, true);
			}
		};
		st.addPaintListener(pl);
	}

    ////////////////////////////////////////////////////////////////////////////
    private void assign_marker_to(List<Integer> offsets) {
        int twiceJumpGroupCount = calcTwiceJumpGroupCount(offsets);
        int singleJumpCount = Math.min(MARKER_CHARSET.length() - twiceJumpGroupCount, offsets.size());

        createSingleJumpMarkers(singleJumpCount, offsets);
        if (twiceJumpGroupCount > 0) {
            createMultipleJumpMarkers(singleJumpCount, twiceJumpGroupCount, offsets);
        }
    }

    private void createMultipleJumpMarkers(int singleJumpCount, int groupsNeedsTwiceJump, List<Integer> offsets) {
        int i = singleJumpCount;

        for (;i < offsets.size(); i++) {
            int group = (i - singleJumpCount) / MARKER_CHARSET.length();
            int markerCharIndex = singleJumpCount + group;

            if (markerCharIndex > MARKER_CHARSET.length() - 1) {
                break;
            }

            char markerChar = MARKER_CHARSET.charAt(markerCharIndex);
            char secondJumpMarkerChar = MARKER_CHARSET.charAt((i - singleJumpCount) % MARKER_CHARSET.length());

            String marker = "" + markerChar + secondJumpMarkerChar;
            _markerCollection.addMarker(marker, offsets.get(i));
        }


        boolean hasMarkersNeedMoreJumps = i < offsets.size();
        if (hasMarkersNeedMoreJumps) {
            for (; i < offsets.size(); i++) {
                _markerCollection.addMarker(String.valueOf(INFINITE_JUMP_CHAR), offsets.get(i));
            }
        }
    }

    private void createSingleJumpMarkers(int singleJumpCount, List<Integer> offsets) {
        for (int i = 0; i < singleJumpCount ; i++) {
            String marker = String.valueOf(MARKER_CHARSET.charAt(i));
            _markerCollection.addMarker(marker, offsets.get(i));
        }
    }

    private int calcTwiceJumpGroupCount(List<Integer> offsets) {
        int makerCharSetSize = MARKER_CHARSET.length();

        for (int groupsNeedMultipleJump = 0; groupsNeedMultipleJump <= makerCharSetSize; groupsNeedMultipleJump++) {
            int oneJumpMarkerCount = makerCharSetSize - groupsNeedMultipleJump;
            if (groupsNeedMultipleJump * makerCharSetSize + oneJumpMarkerCount >= offsets.size()) {
                return groupsNeedMultipleJump;
            }
        }

        return makerCharSetSize;
    }

    ////////////////////////////////////////////////////////////////////////////
	private List<StyledText> listeners = new ArrayList<StyledText>();

	private boolean alreadyHasPaintListenerFor(final StyledText st) {
		if (listeners.contains(st))
			return true;

		listeners.add(st);
		st.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				listeners.remove(st);
			}
		});

		return false;
	}

    ////////////////////////////////////////////////////////////////////////////
	private ITextEditor getActiveTextEditor() {
		IEditorPart ae = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
        return ae instanceof ITextEditor ?  (ITextEditor) ae : null;
	}

	private ISourceViewer getSourceViewer(ITextEditor te) {
		try {
			Method m = AbstractTextEditor.class.getDeclaredMethod("getSourceViewer");
			m.setAccessible(true);
			return (ISourceViewer) m.invoke(te);
		} catch (Exception e1) {
			Activator.log(e1);
			return null;
		}
	}

	private StyledText getStyledTextFromTextEditor(ITextEditor te) {
		Control c = te.getAdapter(Control.class);
		return c instanceof StyledText ? (StyledText) c : null;
	}
}
