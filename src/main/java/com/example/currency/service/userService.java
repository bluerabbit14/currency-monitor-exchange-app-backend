package com.example.currency.service;


import java.util.List;

import org.springframework.stereotype.Service;

import com.example.currency.repository.userRepository;
import com.example.currency.model.user;

@Service 
public class userService {
  
     
    private userRepository repo;
    
    public List<user> getUsers(){
        return repo.getUsers();
    }
}
