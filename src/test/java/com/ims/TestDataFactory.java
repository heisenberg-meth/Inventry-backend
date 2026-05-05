package com.ims;

import java.util.UUID;

public class TestDataFactory {

    public static String email() {
        return "user_" + UUID.randomUUID() + "@test.com";
    }

    public static String business() {
        return "biz_" + UUID.randomUUID();
    }

    public static String slug() {
        return "slug_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public static String sku() {
        return "SKU-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public static String companyCode() {
        return "CC" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}