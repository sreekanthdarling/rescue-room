package com.rescueroom.backend.admin;

import java.util.List;

public record AdminSessionPage(List<AdminSession> sessions, long totalElements, int page,
        int size, long totalPages) { }
