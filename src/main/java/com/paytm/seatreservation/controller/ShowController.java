package com.paytm.seatreservation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.dto.ShowResponse;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.service.ShowService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    // Create Show
    @PostMapping
    public ResponseEntity<Show> createShow(
            @Valid @RequestBody CreateShowRequest request) {

        Show show = showService.createShow(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(show);
    }

    // Get Show
    @GetMapping("/{showId}")
    public ResponseEntity<ShowResponse> getShow(
            @PathVariable Long showId) {

        return ResponseEntity.ok(
                showService.getShow(showId)
        );
    }
}