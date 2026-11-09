package com.example.DormlyBackend.support;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.*;
import com.example.DormlyBackend.enums.*;
import com.example.DormlyBackend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TicketFixtures {

    private final TicketRepository ticketRepository;
    private final TicketProcessingCycleRepository cycleRepository;
    private final TicketHistoryEventRepository historyRepository;
    private final TicketOutboxEventRepository outboxRepository;
    private final UserRepository userRepository;

    public record TicketFixture(UUID id, long version, UUID reporterId, UUID cycleId) {}

    @Transactional
    public TicketFixture givenTicket(TicketStatus status,
                                     TicketPriority priority,
                                     Instant openedAt,
                                     UUID reporterId,
                                     Set<UUID> assigneeIds) {
        User reporter = userRepository.findById(reporterId).orElseGet(() -> {
            User u = new User();
            u.setId(reporterId);
            u.setEmail("user-" + reporterId + "@test.com");
            u.setFullName("Test User " + reporterId);
            u.setPassword("password");
            u.setActive(true);
            return userRepository.save(u);
        });

        Set<User> assignees = new LinkedHashSet<>();
        if (assigneeIds != null) {
            for (UUID aId : assigneeIds) {
                User a = userRepository.findById(aId).orElseGet(() -> {
                    User u = new User();
                    u.setId(aId);
                    u.setEmail("staff-" + aId + "@test.com");
                    u.setFullName("Staff " + aId);
                    u.setPassword("password");
                    u.setActive(true);
                    return userRepository.save(u);
                });
                assignees.add(a);
            }
        }

        Ticket ticket = new Ticket();
        ticket.setCode("TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ticket.setTitle("Fixture Ticket");
        ticket.setDescription("Fixture Description");
        ticket.setCategory(TicketCategory.FACILITY);
        ticket.setReporter(reporter);
        ticket.setStatus(status);
        ticket.setPriority(priority);
        ticket.setAssignees(assignees);
        ticket.setPhase3EnabledAt(openedAt);
        ticket.setDataOrigin(TicketDataOrigin.LIVE);
        ticket = ticketRepository.save(ticket);

        TicketProcessingCycle cycle = TicketProcessingCycle.builder()
                .ticket(ticket)
                .cycleNo(1)
                .openedAt(openedAt)
                .openedBy(reporter)
                .prioritySnapshot(priority)
                .createdAt(openedAt)
                .build();
        cycle = cycleRepository.save(cycle);

        ticket.setCurrentCycleId(cycle.getId());
        ticket = ticketRepository.save(ticket);

        return new TicketFixture(ticket.getId(), ticket.getVersion(), reporter.getId(), cycle.getId());
    }

    @Transactional(readOnly = true)
    public Ticket loadTicket(UUID id) {
        return ticketRepository.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<TicketProcessingCycle> cycles(UUID ticketId) {
        return cycleRepository.findByTicketIdOrderByCycleNoDesc(ticketId);
    }

    @Transactional(readOnly = true)
    public List<TicketHistoryEvent> history(UUID ticketId) {
        return historyRepository.findByTicketIdOrderBySequenceAsc(ticketId);
    }

    @Transactional(readOnly = true)
    public List<TicketOutboxEvent> outbox(UUID ticketId) {
        return outboxRepository.findByTicketId(ticketId);
    }
}
