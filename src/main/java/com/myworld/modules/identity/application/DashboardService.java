package com.myworld.modules.identity.application;

import com.myworld.modules.identity.web.UserDashboardDTO;

public interface DashboardService {
    UserDashboardDTO getDashboardData(String email);
}
