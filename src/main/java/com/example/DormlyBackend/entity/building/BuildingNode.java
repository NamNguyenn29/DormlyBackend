package com.example.DormlyBackend.entity.building;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.example.DormlyBackend.configuration.AuditMetaData;
import com.example.DormlyBackend.enums.Gender;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "building_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BuildingNode {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = true)
    private BuildingNode parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_type_id", nullable = false)
    private NodeType nodeType;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Lob
    @Column(name = "description", nullable = true)
    private String description;

    @Column(name = "max_capacity", nullable = true)
    private Long maxCapacity;

    @Column(name = "current_occupancy", nullable = false)
    private Long currentOccupancy = 0L;

    @Column(name = "gender_policy", nullable = true)
    @Enumerated(EnumType.STRING)
    private Gender genderPolicy;

    @Column(name = "status", nullable = false)
    private String status;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @JsonManagedReference
    List<BuildingNode> children = new ArrayList<>();

    @Embedded
    AuditMetaData auditMetaData = new AuditMetaData();
}