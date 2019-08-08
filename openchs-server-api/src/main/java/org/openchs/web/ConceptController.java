package org.openchs.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openchs.dao.ConceptRepository;
import org.openchs.domain.Concept;
import org.openchs.domain.ConceptDataType;
import org.openchs.projection.ConceptProjection;
import org.openchs.service.ConceptService;
import org.openchs.web.request.ConceptContract;
import org.openchs.web.request.ExportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public class ConceptController implements RestControllerResourceProcessor<Concept> {
    private final Logger logger;
    private ConceptRepository conceptRepository;
    private ConceptService conceptService;
    private ProjectionFactory projectionFactory;

    @Autowired
    public ConceptController(ConceptRepository conceptRepository, ConceptService conceptService, ProjectionFactory projectionFactory) {
        this.conceptRepository = conceptRepository;
        this.conceptService = conceptService;
        this.projectionFactory = projectionFactory;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @RequestMapping(value = "/concepts", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize("hasAnyAuthority('admin','organisation_admin')")
    void save(@RequestBody List<ConceptContract> conceptRequests) {
        conceptRequests.forEach(conceptService::saveOrUpdate);
    }

    @RequestMapping(value = "/concepts/export", method = RequestMethod.POST)
    @PreAuthorize("hasAnyAuthority('admin','organisation_admin')")
    void export(@RequestBody ExportRequest exportRequest) {
        List<ConceptContract> conceptContracts = new ArrayList<>();
        conceptRepository.findAll().iterator().forEachRemaining(concept -> conceptContracts.add(concept.toConceptContract()));
        ObjectMapper mapper = null; //Use global instance of ObjectMapper

        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(exportRequest.getFileName()), conceptContracts);
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("Concepts size: " + conceptContracts.size());
        logger.info(String.format("Exporting conceptContracts to : %s", exportRequest.getFileName()));
    }

    @GetMapping(value = "/web/concept/{uuid}")
    @PreAuthorize(value = "hasAnyAuthority('user', 'organisation_admin')")
    @ResponseBody
    public ConceptProjection getOneForWeb(@PathVariable String uuid) {
        return projectionFactory.createProjection(ConceptProjection.class, conceptService.get(uuid));
    }

    @GetMapping(value = "/web/concept")
    @PreAuthorize(value = "hasAnyAuthority('organisation_admin')")
    @ResponseBody
    public PagedResources<Resource<Concept>> getAll(@RequestParam(value = "name", required = false) String name, Pageable pageable) {
        if (name == null) {
            return wrap(conceptRepository.findAll(pageable));
        } else {
            return wrap(conceptRepository.findByNameIgnoreCaseContaining(name, pageable));
        }
    }


    @GetMapping(value = "/concept/dataTypes")
    @PreAuthorize(value = "hasAnyAuthority('organisation_admin')")
    @ResponseBody
    public List<String> getDataTypes() {
        return Stream.of(ConceptDataType.values())
                .map(ConceptDataType::name)
                .collect(Collectors.toList());
    }

}
