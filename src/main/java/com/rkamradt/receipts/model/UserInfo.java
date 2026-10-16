package com.rkamradt.receipts.model;

public record UserInfo(boolean signedIn, String name, String email, String picture) {}
