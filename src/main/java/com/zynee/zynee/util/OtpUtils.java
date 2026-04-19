package com.zynee.zynee.util;

import java.security.SecureRandom;

public class OtpUtils {
    private static final SecureRandom random = new SecureRandom();

    public static String generateOtp() {
        int otp = 100000 + random.nextInt(900000); // ensures 6-digit OTP
        return String.valueOf(otp);
    }
}
