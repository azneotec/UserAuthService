package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.models.User;

public interface IAuthService {
    User signup(String name, String email, String phoneNumber, String password);

    User login(String email, String password);
}
