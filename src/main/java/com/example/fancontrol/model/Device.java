package com.example.fancontrol.model;

import jakarta.persistence.*;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private boolean fanState;

    private LocalDateTime lastSensorActivation;

    private LocalDateTime lastSeen;
}