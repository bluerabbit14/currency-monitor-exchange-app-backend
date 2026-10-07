package com.example.currency.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class CreateUserRequest {

    @NotBlank
    private String name;
    
    @NotBlank 
    @Email
    private String email;

    public String getName(){
        return name;
    }
    public String getEmail(){
        return email;
    }

    public void setName(String name){
        this.name=name;
    }

    public void setEmail(String email){
        this.email=email;
    }
}
