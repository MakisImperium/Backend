package org.backendbridge;

public final class PrintPasswordHash {
    public static void main(String[] args) {
        String newPassword = "Marcel.2006";
        System.out.println(PasswordUtil.hashPbkdf2(newPassword));
    }
}
