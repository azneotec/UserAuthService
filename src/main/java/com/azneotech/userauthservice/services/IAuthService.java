package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.models.User;
import org.antlr.v4.runtime.misc.Pair;

public interface IAuthService {
    User signup(String name, String email, String phoneNumber, String password);

    Pair<User, String> login(String email, String password);

    Boolean validateToken(String token);
}
