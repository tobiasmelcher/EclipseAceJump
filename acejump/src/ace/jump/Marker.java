package ace.jump;

import java.util.ArrayList;

public class Marker {
    private String _marker;
    private ArrayList<Integer> _offsets = new ArrayList<Integer>();

    public Marker(String marker, int offset) {
        _marker = marker;
        addOffsetToMarker(offset);
    }

    public void addOffsetToMarker(int offset) {
        _offsets.add(offset);
    }

    public ArrayList<Integer> getOffsets() {
        return _offsets;
    }

    public char getMarkerChar() {
        return _marker.charAt(0);
    }

    public String getMarker() {
        return _marker;
    }

    public int getOffset() {
        return _offsets.get(0);
    }

    public boolean isMappingToMultipleOffset() {
        return _offsets.size() > 1;
    }
}
