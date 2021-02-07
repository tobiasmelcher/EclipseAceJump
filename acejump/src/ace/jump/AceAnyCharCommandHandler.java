package ace.jump;

public class AceAnyCharCommandHandler extends AbstractAceCommandHandler {
	@Override
	protected boolean isMatch(String src, int i, char match) {
		char c = src.charAt(i);
		if (c == match) {
			return true;
		}
		return false;
	}
}