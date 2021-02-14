package ace.jump;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;

import acejump.Activator;

public abstract class AbstractAceCommandHandler extends AbstractHandler {

	private boolean drawNow = false;
	private char currentChar;
	private List<StyledText> listeners = new ArrayList<StyledText>();
	private Map<String, Integer> offsetForCharacter = new HashMap<String, Integer>();

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart ae = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		if (ae instanceof MultiPageEditorPart) {
			MultiPageEditorPart mpe = (MultiPageEditorPart) ae;
			Object page = mpe.getSelectedPage();
			if (page instanceof IEditorPart) {
				ae = (IEditorPart) page;
			}
		}
		if (ae instanceof ITextEditor) {
			final ITextEditor te = (ITextEditor) ae;
			ISelection sel = te.getSelectionProvider().getSelection();
			if (sel instanceof ITextSelection) {
				ISourceViewer sv = null;
				try {
					Method m = AbstractTextEditor.class.getDeclaredMethod("getSourceViewer");
					m.setAccessible(true);
					sv = (ISourceViewer) m.invoke(te);
				} catch (Exception e1) {
					Activator.log(e1);
				}
				Control c = (Control) te.getAdapter(Control.class);
				if (c instanceof StyledText) {
					final StyledText st = (StyledText) c;
					addPaintListener(st, sv);
					offsetForCharacter.clear();

					final Shell shell = new Shell(Display.getDefault(), SWT.MODELESS);
					final Text text = new Text(shell, SWT.BORDER);
					text.setText("");
					Font textFont = JFaceResources.getTextFont();
					text.setFont(textFont);
					text.setVisible(true);
					text.setFocus();
					Rectangle bounds = st.getCaret().getBounds();
					text.setBounds(0, 0, 100, bounds.height + 10);
					text.addFocusListener(new FocusAdapter() {
						@Override
						public void focusLost(FocusEvent e) {
							super.focusLost(e);
							shell.close();
							drawNow = false;
							st.redraw();
						}
					});
					text.addModifyListener(new ModifyListener() {

						@Override
						public void modifyText(ModifyEvent e) {
							String txt = text.getText();
							if (txt.length() == 0) {
								drawNow = false;
								return;
							}
							if (drawNow) {
								// check if user adds another character
								if (txt.length() > 1) {
									// jump to cursor
									final String ch = txt.substring(1);
									if (selectOrJumpNow(ch, st, te)) {
										drawNow = false;
										shell.close();
										st.redraw();
									}
									return;
								}
							} else {
								currentChar = txt.charAt(0);
								drawNow = true;
							}
							st.redraw();
						}
					});
					text.addKeyListener(new KeyAdapter() {
						@Override
						public void keyPressed(KeyEvent e) {
							super.keyPressed(e);
							if (e.keyCode == 27) {
								shell.close();
								drawNow = false;
								st.redraw();
							}
						}
					});
					// get global x/y pos at end of current line
					int off = st.getOffsetAtLocation(new Point(bounds.x, bounds.y));
					int line = st.getLineAtOffset(off);
					int lineLength = st.getLine(line).length();
					int endLineOffset = st.getOffsetAtLine(line) + lineLength;
					Rectangle b = st.getTextBounds(endLineOffset, endLineOffset);
					Point p = st.toDisplay(b.x, b.y);

					shell.setBounds(p.x + 20, p.y - 5, 100, bounds.height + 5);
					shell.open();
				}
			}
		}
		return null;
	}

	protected boolean selectOrJumpNow(String ch, StyledText st, ITextEditor te) {
		Integer off = offsetForCharacter.get(ch);
		if (off != null) {
			st.setSelection(off);
			return true;
		}
		return false;
	}
	protected abstract boolean isMatch(String src, int i, char match);
	
	private void addPaintListener(final StyledText st, final ISourceViewer sv) {
		if (listeners.contains(st))
			return;
		listeners.add(st);
		Display display = Display.getDefault();
		final Color black = display.getSystemColor(SWT.COLOR_BLACK);
		final Color white = display.getSystemColor(SWT.COLOR_WHITE);
		PaintListener pl = new PaintListener() {
			char letterCounter=0;
			List<String> prefixes=Arrays.asList("",";",",",".");
			int prefixIndex=0;
			
			@Override
			public void paintControl(PaintEvent e) {
				if (drawNow == false)
					return;
				GC gc = e.gc;

				findMatchesAfterCursor(st, sv, gc);
				findMatchesBeforeCursor(st, sv, gc);
			}

			private void findMatchesBeforeCursor(final StyledText st, final ISourceViewer sv, GC gc) {
				// get visible editor area
				int start = sv.getTopIndexStartOffset();
				start = ((ITextViewerExtension5) sv).modelOffset2WidgetOffset(start);

				int line = st.getLineAtOffset(st.getCaretOffset());
				int end = st.getOffsetAtLine(line);

				String src = st.getText(start, end).toLowerCase(Locale.ENGLISH);
				int i = src.length() - 1;
				int len = st.getCharCount();
				char current = Character.toLowerCase(currentChar);
				letterCounter = 'A';
				prefixIndex=0;
				while (true) {
					if (isMatch(src, i, current)) {
						int off = start + i;
						if (off >= len) {
							break;
						}
						drawNextCharAt(off, gc, st);
						if (letterCounter - 1 == 'Z') {
							prefixIndex++;
							letterCounter='A';
							if (prefixIndex>=prefixes.size()) {
								break;	
							}
						}
					}
					i--;
					if (i <= 0) {
						break;
					}
				}
			}

			

			private void findMatchesAfterCursor(final StyledText st, final ISourceViewer sv, GC gc) {
				// get visible editor area
				int line = st.getLineAtOffset(st.getCaretOffset());
				int start = st.getOffsetAtLine(line);
				int end = sv.getBottomIndexEndOffset();
				end = ((ITextViewerExtension5) sv).modelOffset2WidgetOffset(end);

				String src = st.getText(start, end).toLowerCase(Locale.ENGLISH);
				int i = 0;
				int len = st.getCharCount();
				letterCounter = 'a';
				prefixIndex = 0;
				char current = Character.toLowerCase(currentChar);
				while (true) {
					if (isMatch(src, i, current)) {
						int off = start + i;
						if (off >= len) {
							break;
						}
						drawNextCharAt(off, gc, st);
						if (letterCounter - 1 == 'z') {
							prefixIndex++;
							letterCounter='a';
							if (prefixIndex>=prefixes.size()) {
								break;	
							}
						}
					}
					i++;
					if (i >= src.length())
						break;
				}
			}

			private void drawNextCharAt(int offset, GC gc, StyledText st) {
				String word = prefixes.get(prefixIndex) + Character.toString(letterCounter);
				offsetForCharacter.put(word, offset);
				letterCounter++;

				Rectangle bounds = st.getTextBounds(offset, offset);
				gc.setBackground(black);
				Point textExtent = gc.textExtent(word);
				int ex = 4;
				int dex = 2 * ex;
				gc.fillRoundRectangle(bounds.x - ex, bounds.y - ex, textExtent.x + dex, textExtent.y + dex, 4, 4);
				gc.setForeground(white);
				gc.drawString(word, bounds.x, bounds.y, true);
			}
		};
		st.addPaintListener(pl);
		st.addDisposeListener(new DisposeListener() {

			@Override
			public void widgetDisposed(DisposeEvent e) {
				listeners.remove(st);
			}
		});
	}
}
