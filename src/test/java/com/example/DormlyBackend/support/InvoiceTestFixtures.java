package com.example.DormlyBackend.support;

import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.BuildingNode;
import com.example.DormlyBackend.entity.building.Invoice;
import com.example.DormlyBackend.entity.building.NodeType;
import com.example.DormlyBackend.entity.building.RoomAssignment;
import com.example.DormlyBackend.enums.FeeCategory;
import com.example.DormlyBackend.enums.InvoiceStatus;
import com.example.DormlyBackend.repository.BuildingNodeRepository;
import com.example.DormlyBackend.repository.InvoiceRepository;
import com.example.DormlyBackend.repository.NodeTypeRepository;
import com.example.DormlyBackend.repository.RoleRepository;
import com.example.DormlyBackend.repository.RoomAssignmentRepository;
import com.example.DormlyBackend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Builds persisted users, rooms, room assignments and invoices for invoice tests.
 *
 * <p>Plain helper (no Spring annotations): tests construct it with their autowired repositories.
 * Every call creates fresh rows with random identifiers so tests never collide on unique columns.
 */
public class InvoiceTestFixtures {

    private static final int ROOM_LEVEL = 3;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final NodeTypeRepository nodeTypeRepository;
    private final BuildingNodeRepository buildingNodeRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final InvoiceRepository invoiceRepository;

    public InvoiceTestFixtures(UserRepository userRepository,
                               RoleRepository roleRepository,
                               NodeTypeRepository nodeTypeRepository,
                               BuildingNodeRepository buildingNodeRepository,
                               RoomAssignmentRepository roomAssignmentRepository,
                               InvoiceRepository invoiceRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.nodeTypeRepository = nodeTypeRepository;
        this.buildingNodeRepository = buildingNodeRepository;
        this.roomAssignmentRepository = roomAssignmentRepository;
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Persists an active user holding the given role, creating the role when it does not exist.
     *
     * @param roleName role name such as {@code STUDENT}, {@code STAFF} or {@code ADMIN}
     */
    public User createUser(String roleName) {
        Role role = roleRepository.findByName(roleName).orElseGet(() -> {
            Role created = new Role();
            created.setName(roleName);
            return roleRepository.save(created);
        });

        User user = new User();
        user.setEmail("user-" + UUID.randomUUID() + "@test.com");
        user.setFullName("User " + roleName);
        user.setPassword("pass");
        user.setActive(true);
        user.setRoles(Set.of(role));
        return userRepository.save(user);
    }

    /**
     * Persists an invoice with the given status for a fresh room assigned to {@code student}.
     *
     * @param student owner of the room assignment the invoice is billed to
     * @param status  initial invoice status; {@code paidAt} stays null whatever the status
     */
    public Invoice createInvoice(User student, InvoiceStatus status) {
        NodeType nodeType = new NodeType();
        nodeType.setName("ROOM-" + UUID.randomUUID());
        nodeType.setLevel(ROOM_LEVEL);
        nodeType = nodeTypeRepository.save(nodeType);

        BuildingNode room = new BuildingNode();
        room.setNodeType(nodeType);
        room.setName("Room " + UUID.randomUUID());
        room.setStatus("AVAILABLE");
        room = buildingNodeRepository.save(room);

        RoomAssignment assignment = new RoomAssignment();
        assignment.setUser(student);
        assignment.setRoomNode(room);
        assignment.setStartDate(LocalDateTime.now());
        assignment = roomAssignmentRepository.save(assignment);

        Invoice invoice = Invoice.builder()
                .roomAssignment(assignment)
                .feeCategory(FeeCategory.values()[0])
                .amount(new BigDecimal("1500000.00"))
                .status(status)
                .month("2026-11")
                .dueDate(LocalDateTime.now().plusDays(7))
                .build();
        return invoiceRepository.save(invoice);
    }
}
