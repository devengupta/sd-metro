package com.sdmetro.app;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * What is on screen but not part of saved progress: selected tabs, half-typed answers, running drills.
 * Kept in memory for the signed-in account and written to the saved instance state so it survives
 * rotation and the system recreating the process.
 */
public final class UiState implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Id of the account this state belongs to; "" on the sign-in screen. */
    public String owner = "";
    /** Content key -> ride, pager or teach. */
    public final HashMap<String, String> mode = new HashMap<>();
    public String lens = "plain";
    /** "mode:key" present when the Claude-generated variant is selected. */
    public final HashMap<String, Boolean> live = new HashMap<>();
    public final HashMap<String, Quiz> sRide = new HashMap<>(), sPager = new HashMap<>();
    public final HashMap<String, BTeach> sTeach = new HashMap<>();
    public final HashMap<String, GRide> ride = new HashMap<>();
    public final HashMap<String, GPager> pager = new HashMap<>();
    public final HashMap<String, GTeach> teach = new HashMap<>();
    /** Selected arcade game, and the round in progress. */
    public String game;
    public Arcade.Ladder ladder;
    public Arcade.Quiz quiz;

    public String authTab = "in", authErr = "", authName = "", authEmail = "";
    public transient boolean authBusy, delArmed;

    /** Built-in Ride question or Pager incident: option order and the pick (-1 none). */
    public static final class Quiz implements Serializable {
        private static final long serialVersionUID = 1L;
        public int[] order;
        public int pick = -1;
    }

    /** Built-in Teach. */
    public static final class BTeach implements Serializable {
        private static final long serialVersionUID = 1L;
        public String draft = "";
        public boolean shown;
        public boolean[] ticks = new boolean[3];
        /** -1 until scored. */
        public int score = -1;
        public transient boolean open;
    }

    public static class Live implements Serializable {
        private static final long serialVersionUID = 1L;
        public boolean loading;
        /** Failure of the last request, or null. */
        public String errCode, errMsg;
        public void fail(Gen.Fail f) { errCode = f.code; errMsg = f.getMessage(); }
        public void clearErr() { errCode = null; errMsg = null; }
        public boolean hasErr() { return errCode != null; }
    }

    public static final class GRide extends Live {
        private static final long serialVersionUID = 1L;
        public Gen.Ride data;
        public String lens;
        /** Question index -> chosen option. */
        public final HashMap<Integer, Integer> answers = new HashMap<>();
    }

    public static final class GPager extends Live {
        private static final long serialVersionUID = 1L;
        public String company, service;
        public ArrayList<Gen.Round> rounds;
        public Gen.Debrief debrief;
    }

    public static final class GTeach extends Live {
        private static final long serialVersionUID = 1L;
        public String q, a, draft = "";
        public Gen.Grade g;
        public transient boolean open;
    }

    /** After the process was recreated nothing is in flight any more. */
    public void clearInFlight() {
        for (GRide s : ride.values()) s.loading = false;
        for (GTeach s : teach.values()) s.loading = false;
        for (GPager s : pager.values()) {
            if (s.loading && s.rounds != null && !s.rounds.isEmpty()) {
                Gen.Round r = s.rounds.get(s.rounds.size() - 1);
                if (r.quality == null) r.chosen = -1;
            }
            s.loading = false;
        }
        authBusy = false;
        delArmed = false;
    }
}
