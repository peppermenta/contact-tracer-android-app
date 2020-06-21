package com.example.contacttracing;

import io.jsonwebtoken.Jwts;

public class JWT_Token_Handler {
    private static final String key = "<Base64 encoded JWT Secret>";
    public static String getUUIDFromToken(String jwtToken) {

        String uuid = Jwts.parser()
                .setSigningKey(key)
                .parseClaimsJws(jwtToken)
                .getBody()
                .get("uuid",String.class);

        return uuid;
    }
}
