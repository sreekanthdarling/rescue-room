package com.rescueroom.backend.admin;

import java.time.Instant;

public record AdminStatistics(long totalPlayers, long playingNow, long winners, long losers,
        long emailsSubmitted, int inactivitySeconds, Instant generatedAt) { }
