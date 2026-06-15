import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Base64;
import java.nio.file.*;

public class GenerateJwks {
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "rsa-public-key.pem";
        String pem = Files.readString(Path.of(path))
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] encoded = Base64.getDecoder().decode(pem);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPublicKey pub = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(encoded));

        Base64.Encoder urlEnc = Base64.getUrlEncoder().withoutPadding();

        byte[] modBytes = pub.getModulus().toByteArray();
        if (modBytes[0] == 0) {
            byte[] tmp = new byte[modBytes.length - 1];
            System.arraycopy(modBytes, 1, tmp, 0, tmp.length);
            modBytes = tmp;
        }

        String n = urlEnc.encodeToString(modBytes);
        String e = urlEnc.encodeToString(pub.getPublicExponent().toByteArray());

        System.out.printf("""
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "dev-key-1",
      "alg": "RS256",
      "n": "%s",
      "e": "%s"
    }
  ]
}
%n""", n, e);
    }
}
