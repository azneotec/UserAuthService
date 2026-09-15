package com.azneotech.userauthservice.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
public class User extends BaseModel {
    private String name;
    private String email;
    private String password;
    private String phoneNumber;
    @Builder.Default
    @ManyToMany
    private List<Role> roles = new ArrayList<>();
}
