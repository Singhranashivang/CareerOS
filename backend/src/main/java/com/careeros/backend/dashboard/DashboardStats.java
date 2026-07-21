package com.careeros.backend.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {

    private long repositories;

    private long commits;

    private long pullRequests;

    private long achievements;

}