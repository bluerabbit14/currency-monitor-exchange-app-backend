package com.example.currency.feedback.repository;

import com.example.currency.feedback.entity.AppFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<AppFeedback, UUID> {
}
