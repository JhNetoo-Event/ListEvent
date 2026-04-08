package org.crystalocean.listEvent.model;

import java.util.ArrayList;
import java.util.List;

public class AllowlistData {

    private List<AllowedPlayerRecord> players = new ArrayList<>();

    public List<AllowedPlayerRecord> getPlayers() {
        return players;
    }

    public void setPlayers(List<AllowedPlayerRecord> players) {
        this.players = players;
    }
}