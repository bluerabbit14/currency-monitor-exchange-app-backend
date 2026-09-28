package com.example.currency.repository;

import com.example.currency.model.user;

import java.util.List;


import java.util.Arrays;
import java.util.ArrayList;


public class userRepository {
    List<user> users = new ArrayList<>(
        Arrays.asList(new user(101, "14asifcr7@gmail.com"), new user(102, "Danish@gmail.com"))
    );

    public List<user> getUsers(){
        return users;
    }
}
