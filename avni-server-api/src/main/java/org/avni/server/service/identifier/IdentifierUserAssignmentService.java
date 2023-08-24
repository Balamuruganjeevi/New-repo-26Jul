package org.avni.server.service.identifier;

import org.avni.server.dao.IdentifierUserAssignmentRepository;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.domain.identifier.IdentifierGeneratorType;
import org.avni.server.domain.identifier.IdentifierOverlappingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IdentifierUserAssignmentService {
    private final IdentifierUserAssignmentRepository identifierUserAssignmentRepository;

    @Autowired
    public IdentifierUserAssignmentService(IdentifierUserAssignmentRepository identifierUserAssignmentRepository) {
        this.identifierUserAssignmentRepository = identifierUserAssignmentRepository;
    }

    public void save(IdentifierUserAssignment identifierUserAssignment) throws IdentifierOverlappingException {
        IdentifierSource identifierSource = identifierUserAssignment.getIdentifierSource();
        if (identifierSource.getType().equals(IdentifierGeneratorType.userPoolBasedIdentifierGenerator)) {
            List<IdentifierUserAssignment> overlappingWithAssignments = identifierUserAssignmentRepository.getOverlappingAssignmentForIdentifierSourceWithPrefix(identifierUserAssignment);
            throw new IdentifierOverlappingException(overlappingWithAssignments);
        }
        identifierUserAssignmentRepository.save(identifierUserAssignment);
    }
}
