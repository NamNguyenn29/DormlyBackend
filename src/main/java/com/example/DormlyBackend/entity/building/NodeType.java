package com.example.DormlyBackend.entity.building;

import java.util.UUID;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.example.DormlyBackend.configuration.AuditMetaData;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Table(name = "node_types")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class NodeType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    private int level;

    @Embedded
    AuditMetaData auditMetaData = new AuditMetaData();

}
