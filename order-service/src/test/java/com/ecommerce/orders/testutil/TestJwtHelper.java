package com.ecommerce.orders.testutil;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

public class TestJwtHelper {

    private final RSAPrivateKey privateKey;
    private final String issuer;

    public TestJwtHelper(Path pemPath, String issuer) {
        this.privateKey = loadPrivateKey(pemPath);
        this.issuer = issuer;
    }

    public String generate(String subject, String scope) {
        try {
            var now = Instant.now();
            var claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(subject)
                    .claim("scope", scope)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(3600)))
                    .build();

            var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID("dev-key-1")
                    .build();

            var jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test JWT", e);
        }
    }

    private static RSAPrivateKey loadPrivateKey(Path pemPath) {
        try {
            var content = Files.readString(pemPath)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            var der = Base64.getDecoder().decode(content);
            var spec = new PKCS8EncodedKeySpec(der);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load RSA private key from: " + pemPath, e);
        }
    }
}
