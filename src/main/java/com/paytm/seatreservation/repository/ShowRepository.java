package com.paytm.seatreservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.paytm.seatreservation.entity.Show;

public interface ShowRepository extends JpaRepository<Show, Long> {
}