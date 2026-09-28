package com.example.currency.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.currency.model.user;
import com.example.currency.service.userService;

import java.util.List;



@RestController
@RequestMapping("/user")
public class userController {
    @Autowired 
    private userService service;
    
    @GetMapping 
    public List<user> get(){
        return service.getUsers();
    }
}
