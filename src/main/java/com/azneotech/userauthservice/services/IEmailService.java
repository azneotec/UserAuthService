package com.azneotech.userauthservice.services;

public interface IEmailService {

    /** Delivers the password-reset link built from {@code rawToken} to {@code toEmail}. */
    void sendPasswordResetEmail(String toEmail, String rawToken);

}
