package com.ims.tenant.dto;

import java.math.BigDecimal;

public record MonthlyRevenue(int year, int month, BigDecimal revenue) {}
