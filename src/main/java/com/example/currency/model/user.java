package com.example.currency.model;

public class user {
    private int id;
    private String email;

    public user(int id, String email){
        this.id=id;
        this.email=email;
    }

    public int getId(){
        return id;
    }

    public String getEmail(){
       return email;
    }
}
