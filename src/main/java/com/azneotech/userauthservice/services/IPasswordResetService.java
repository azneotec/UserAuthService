package com.azneotech.userauthservice.services;

public interface IPasswordResetService {

    /**
     * Issues a reset token and emails it if {@code email} belongs to an active user. Never throws
     * for an unknown address, so callers can't use this to discover which emails are registered.
     */
    void requestReset(String email);

    /**
     * Sets a new password for the user the token belongs to, consumes the token, and revokes all
     * of that user's sessions.
     *
     * @throws com.azneotech.userauthservice.exceptions.InvalidPasswordResetTokenException if the token is
     *                                                                                    unknown, used, or expired
     */
    void resetPassword(String rawToken, String newPassword);

}
