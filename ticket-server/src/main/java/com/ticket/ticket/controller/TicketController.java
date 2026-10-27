package com.ticket.ticket.controller;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.common.result.Result;
import com.ticket.ticket.service.TicketService;

@RestController 
@RequestMapping ("/api/ticket")
public class TicketController {
    private final TicketService ticketService;
    public TicketController(TicketService ticketService)
    {
        this.ticketService=ticketService;
    }
    @GetMapping ("/stock")
    public Result<?> seatstock(@RequestParam ("trainId") Long trainId, @RequestParam ("date") LocalDate date)
    {
        return Result.success(ticketService.seatstock(trainId,date));
    }
    
}
