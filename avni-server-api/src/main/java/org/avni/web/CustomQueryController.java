package org.avni.web;


import org.avni.dao.QueryRepository;
import org.avni.web.request.CustomQueryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.transaction.Transactional;

@RestController
public class CustomQueryController {

    private final QueryRepository queryRepository;

    @Autowired
    public CustomQueryController(QueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @RequestMapping(name = "/executeQuery", method = RequestMethod.POST)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    public ResponseEntity<?> executeQuery(@RequestBody CustomQueryRequest customQueryRequest) {
        if (customQueryRequest.getName() == null) {
            return ResponseEntity.badRequest().body("No query name passed in the request");
        }
        return queryRepository.runQuery(customQueryRequest);
    }
}