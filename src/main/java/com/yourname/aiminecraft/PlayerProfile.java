package com.yourname.aiminecraft;

import java.util.ArrayList;
import java.util.List;

public class PlayerProfile {
    public int interactionCount = 0;
    public long firstSeen = System.currentTimeMillis();
    public List<String> aiNotes = new ArrayList<>();
}
