package com.example.DormlyBackend.service.ticket;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.entity.ticket.TicketHistoryEvent;
import com.example.DormlyBackend.repository.TicketHistoryEventRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.ticket.command.TicketEventDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketHistoryService {

    private final TicketHistoryEventRepository historyRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<TicketHistoryEvent> append(Ticket ticket, List<TicketEventDraft> drafts, Instant now) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }

        long lastSequence = historyRepository.findTopByTicketIdOrderBySequenceDesc(ticket.getId())
                .map(TicketHistoryEvent::getSequence)
                .orElse(0L);

        List<TicketHistoryEvent> saved = new ArrayList<>();
        for (TicketEventDraft draft : drafts) {
            lastSequence++;
            User actor = draft.actorId() != null ? userRepository.findById(draft.actorId()).orElse(null) : null;

            TicketHistoryEvent event = TicketHistoryEvent.builder()
                    .ticket(ticket)
                    .cycleId(draft.cycleId())
                    .sequence(lastSequence)
                    .type(draft.type())
                    .actor(actor)
                    .occurredAt(now)
                    .visibility(draft.visibility())
                    .fromStatus(draft.fromStatus())
                    .toStatus(draft.toStatus())
                    .note(draft.note())
                    .payload(draft.payload())
                    .build();

            saved.add(historyRepository.save(event));
        }
        return saved;
    }
}
