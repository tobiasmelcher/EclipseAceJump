package ace.jump;

public class AceBeginningWordCommandHandler extends AbstractAceCommandHandler {
	protected boolean isMatch(String src, int i, char match) {
		char c = src.charAt(i);
		if (c == match) {
			if (i == 0)
				return true;
			if (Character.isLetter(c) == false)
				return true;
			char prev = src.charAt(i - 1);
			if (Character.isLetter(prev))
				return false;
			return true;
		}
		return false;
	}
}