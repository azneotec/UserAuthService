package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.models.User;

public interface IUserService {

    /** @throws com.azneotech.userauthservice.exceptions.UserNotFoundException if no such user */
    User getById(Long userId);

    /**
     * Applies the non-null fields to the user's profile.
     *
     * @throws com.azneotech.userauthservice.exceptions.UserNotFoundException      if no such user
     * @throws com.azneotech.userauthservice.exceptions.UserAlreadyExistsException if the phone number belongs to someone else
     */
    User updateProfile(Long userId, String name, String phoneNumber);

    /**
     * Grants {@code ROLE_ADMIN}. Idempotent: promoting an existing admin changes nothing. When the role is newly
     * granted, the user's live sessions are revoked, because roles are baked into the token and a token issued
     * before the promotion would otherwise keep its old roles until it expires.
     *
     * @throws com.azneotech.userauthservice.exceptions.UserNotFoundException if no such user
     */
    User promoteToAdmin(Long userId);

}
