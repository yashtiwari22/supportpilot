package com.supportpilot.demo;

import com.supportpilot.ticket.CreateTicketRequest;
import com.supportpilot.ticket.TicketResponse;
import com.supportpilot.ticket.TicketService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Service generating realistic synthetic customer support tickets for demonstrations and simulator.
 */
@Service
public class DemoTicketGeneratorService {

    private final TicketService ticketService;
    private final Random random = new Random();

    private record SampleTicket(String name, String email, String subject, String body) {}

    private static final List<SampleTicket> SAMPLES = List.of(
            new SampleTicket(
                    "Sarah Smith",
                    "sarah.smith@example.com",
                    "Order #ORD-1042 ANC Headphones not delivered",
                    "Hi, I ordered the AcousticPro ANC headphones five days ago with express shipping (Order #ORD-1042). The tracking has not updated and I need them for my flight tomorrow. Please help!"
            ),
            new SampleTicket(
                    "Marcus Brody",
                    "marcus.brody@example.com",
                    "Requesting refund for Order #ORD-1042",
                    "I received my AcousticPro ANC headphones yesterday but they do not fit comfortably over my ears. I would like to return them and receive a full refund please."
            ),
            new SampleTicket(
                    "Elena Rostova",
                    "elena.rostova@example.com",
                    "AcousticPro Bluetooth disconnects constantly",
                    "My headphones keep dropping the Bluetooth connection after about 3 minutes of music playback on my Mac. I tried restarting my laptop already. Is there a firmware update or reset procedure?"
            ),
            new SampleTicket(
                    "David Chen",
                    "david.chen@example.com",
                    "Unrecognized charge on monthly invoice",
                    "I noticed a duplicate charge of $149.99 on my credit card statement from Acme Electronics. I only placed one order. Can you please check your billing records and refund the duplicate?"
            ),
            new SampleTicket(
                    "Jessica Miller",
                    "jessica.m@example.com",
                    "Terrible experience, cancel my order immediately!!",
                    "This is ridiculous. I ordered three days ago and haven't even received a shipping confirmation! Cancel my order immediately and return my money right now or I will contact my bank!"
            ),
            new SampleTicket(
                    "Michael Scott",
                    "michael.s@example.com",
                    "Compatibility question regarding USB-C dock",
                    "Hello, does your Dual 4K USB-C Docking Station support dual monitors on Apple M3 MacBook Air? Thanks!"
            )
    );

    public DemoTicketGeneratorService(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    public List<TicketResponse> generateDemoTickets(UUID tenantId, int count) {
        int actualCount = Math.max(1, Math.min(count, 10));
        List<TicketResponse> created = new ArrayList<>();

        for (int i = 0; i < actualCount; i++) {
            SampleTicket sample = SAMPLES.get(random.nextInt(SAMPLES.size()));
            String idempotencyKey = UUID.randomUUID().toString();
            CreateTicketRequest request = new CreateTicketRequest(
                    sample.email(),
                    sample.name(),
                    sample.subject(),
                    sample.body()
            );

            TicketResponse res = ticketService.ingestTicket(tenantId, request, idempotencyKey);
            created.add(res);
        }

        return created;
    }
}
