package com.azneotech.userauthservice.controllers;

import com.azneotech.userauthservice.exceptions.PasswordMismatchException;
import com.azneotech.userauthservice.exceptions.UserAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ControllerAdvisor {

    @ExceptionHandler(UserAlreadyExistsException.class)
    private ResponseEntity<String> handleUserAlreadyExistsException(Exception exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(PasswordMismatchException.class)
    private ResponseEntity<String> handlePasswordMismatchException(Exception exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.UNAUTHORIZED);
    }

}
