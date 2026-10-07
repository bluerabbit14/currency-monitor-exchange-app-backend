package com.example.currency.service;

import com.example.currency.entity.User;
import com.example.currency.repository.UserRepository;
import com.example.currency.dto.CreateUserRequest;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(CreateUserRequest request){
        User user = new User(request.getName(), request.getEmail());
        return userRepository.save(user);
    }

     public List<User> getallUser(){
        return userRepository.findAll();
    }
}
