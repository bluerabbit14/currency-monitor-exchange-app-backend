package com.example.currency.entity;

import jakarta.persistence.*;


//Hibernate/JPA will manage the mapping
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String name;

    @Column(nullable=false,unique=true)
    private String email;


    public User() {
    }

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public Long getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getEmail() {
        return email;
    }

    public void SetName(String name) {
        this.name = name;
    }

    public void SetEmail(String email) {
        this.email = email;
    }
}
