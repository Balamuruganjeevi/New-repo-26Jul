package org.avni.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.web.request.EncounterTypeContract;
import org.avni.server.web.request.rules.RulesContractWrapper.*;
import org.avni.server.web.request.rules.constructWrappers.*;
import org.avni.server.web.request.rules.request.*;
import org.avni.server.web.request.rules.response.*;
import org.codehaus.jettison.json.JSONException;
import org.avni.server.application.Form;
import org.avni.server.application.RuleType;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.dao.individualRelationship.RuleFailureLogRepository;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.RestClient;
import org.avni.server.web.request.RuleRequest;
import org.avni.server.web.request.rules.constant.WorkFlowTypeEnum;
import org.avni.server.web.request.rules.validateRules.RuleValidationService;
import org.avni.server.web.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.transaction.Transactional;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.joda.time.DateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RuleService implements NonScopeAwareService {
    private final Logger logger;
    private final RuleDependencyRepository ruleDependencyRepository;
    private final RuleRepository ruleRepository;
    private final Map<RuledEntityType, CHSRepository> ruledEntityRepositories;
    private final RestClient restClient;
    private final IndividualConstructionService individualConstructionService;
    private final RuleValidationService ruleValidationService;
    private final ProgramEncounterConstructionService programEncounterConstructionService;
    private final ProgramEnrolmentConstructionService programEnrolmentConstructionService;
    private final FormRepository formRepository;
    private final RuleFailureLogRepository ruleFailureLogRepository;
    private final ObservationService observationService;
    private final EntityApprovalStatusService entityApprovalStatusService;
    private final ContractBuilderServices contractBuilderServices;
    private IndividualService individualService;

    @Autowired
    public RuleService(RuleDependencyRepository ruleDependencyRepository,
                       RuleRepository ruleRepository,
                       FormRepository formRepository,
                       ProgramRepository programRepository,
                       EncounterTypeRepository encounterTypeRepository,
                       RestClient restClient,
                       IndividualConstructionService individualConstructionService,
                       RuleValidationService ruleValidationService,
                       ProgramEncounterConstructionService programEncounterConstructionService,
                       ProgramEnrolmentConstructionService programEnrolmentConstructionService,
                       RuleFailureLogRepository ruleFailureLogRepository,
                       ObservationService observationService,
                       EntityApprovalStatusService entityApprovalStatusService, ContractBuilderServices contractBuilderServices, IndividualService individualService) {
        this.ruleFailureLogRepository = ruleFailureLogRepository;
        this.observationService = observationService;
        this.entityApprovalStatusService = entityApprovalStatusService;
        this.contractBuilderServices = contractBuilderServices;
        this.individualService = individualService;
        logger = LoggerFactory.getLogger(this.getClass());
        this.ruleDependencyRepository = ruleDependencyRepository;
        this.ruleRepository = ruleRepository;
        this.ruledEntityRepositories = new HashMap<RuledEntityType, CHSRepository>() {{
            put(RuledEntityType.Form, formRepository);
            put(RuledEntityType.Program, programRepository);
            put(RuledEntityType.EncounterType, encounterTypeRepository);
        }};
        this.restClient = restClient;
        this.individualConstructionService = individualConstructionService;
        this.ruleValidationService = ruleValidationService;
        this.programEncounterConstructionService = programEncounterConstructionService;
        this.programEnrolmentConstructionService = programEnrolmentConstructionService;
        this.formRepository = formRepository;
    }

    public RuleService(RestClient restClient, IndividualConstructionService individualConstructionService) {
        this.restClient = restClient;
        logger = null;
        ruleDependencyRepository = null;
        ruleRepository = null;
        ruledEntityRepositories = null;
        this.individualConstructionService = individualConstructionService;
        ruleValidationService = null;
        programEncounterConstructionService = null;
        programEnrolmentConstructionService = null;
        formRepository = null;
        ruleFailureLogRepository = null;
        observationService = null;
        entityApprovalStatusService = null;
        contractBuilderServices = null;
        individualService = null;
    }

    @Transactional
    public RuleDependency createDependency(String ruleCode, String ruleHash) {
        RuleDependency ruleDependency = ruleDependencyRepository
                .findByOrganisationId(UserContextHolder.getUserContext().getOrganisation().getId());
        if (ruleDependency == null) ruleDependency = new RuleDependency();
        if (ruleHash.equals(ruleDependency.getChecksum())) return ruleDependency;
        ruleDependency.setCode(ruleCode);
        ruleDependency.setChecksum(ruleHash);
        ruleDependency.assignUUIDIfRequired();
        logger.info(String.format("Rule dependency with UUID: %s", ruleDependency.getUuid()));
        return ruleDependencyRepository.save(ruleDependency);
    }

    private Rule _setCommonAttributes(Rule rule, RuleRequest ruleRequest) {
        rule.setUuid(ruleRequest.getUuid());
        rule.setData(new RuleData(ruleRequest.getData()));
        rule.setName(ruleRequest.getName());
        rule.setFnName(ruleRequest.getFnName());
        rule.setType(RuleType.valueOf(StringUtils.capitalize(ruleRequest.getType())));
        rule.setExecutionOrder(ruleRequest.getExecutionOrder());
        rule.setVoided(false);
        return rule;
    }

    @Transactional
    public Rule createOrUpdate(RuleRequest ruleRequest) {
        String ruleDependencyUUID = ruleRequest.getRuleDependencyUUID();
        RuleDependency ruleDependency = ruleDependencyRepository.findByUuid(ruleDependencyUUID);
        String ruleUUID = ruleRequest.getUuid();
        Rule rule = ruleRepository.findByUuid(ruleUUID);
        if (rule == null) rule = new Rule();
        rule.setRuleDependency(ruleDependency);
        rule = this._setCommonAttributes(rule, ruleRequest);
        String entityUUID = ruleRequest.getEntityUUID();

        checkEntityExists(ruleRequest);

        rule.setEntity(ruleRequest.getEntity());

        logger.info(String.format("Creating Rule with UUID '%s', Name '%s', Type '%s', Entity '%s'",
                rule.getUuid(), rule.getName(), rule.getType(), ruleRequest.getEntityType()));

        return ruleRepository.save(rule);
    }

    private void checkEntityExists(RuleRequest ruleRequest) {
        String entityUUID = ruleRequest.getEntityUUID();
        if (!RuledEntityType.isNone(ruleRequest.getEntityType())) {
            CHSRepository chsRepository = ruledEntityRepositories.get(ruleRequest.getEntityType());
            if (chsRepository.findByUuid(entityUUID) == null) {
                throw new ValidationException(String.format("%s with uuid: %s not found for rule with uuid: %s",
                        ruleRequest.getEntityType(), entityUUID, ruleRequest.getUuid()));
            }
        }
    }

    @Transactional
    public void createOrUpdate(List<RuleRequest> rules) {
        List<Rule> rulesFromDB = ruleRepository.findByOrganisationId(UserContextHolder.getUserContext().getOrganisation().getId());
        List<String> newRuleUUIDs = rules.stream()
                .map(this::createOrUpdate)
                .map(Rule::getUuid)
                .collect(Collectors.toList());

        Stream<Rule> deletedRules = rulesFromDB.stream().filter(r -> !newRuleUUIDs.contains(r.getUuid()));

        deletedRules.peek(vr -> vr.setVoided(true)).forEach(ruleRepository::save);
    }

    public RuleResponseEntity executeProgramSummaryRule(ProgramEnrolment programEnrolment) {
        RuleResponseEntity ruleResponseEntity = new RuleResponseEntity();
        if (programEnrolment == null) {
            ruleResponseEntity.setStatus(HttpStatus.NOT_FOUND.toString());
            return ruleResponseEntity;
        }
        RuleRequestEntity rule = new RuleRequestEntity();
        Program program = programEnrolment.getProgram();
        rule.setProgramSummaryCode(program.getEnrolmentSummaryRule());
        String workFlowType = WorkFlowTypeEnum.PROGRAM_SUMMARY.getWorkFlowTypeName();
        rule.setWorkFlowType(workFlowType);
        rule.setFormUuid(program.getUuid());
        rule.setRuleType("Program Summary");
        ProgramEnrolmentContract programEnrolmentContract = ProgramEnrolmentContract.fromEnrolment(programEnrolment, observationService, entityApprovalStatusService);
        programEnrolmentContract.setRule(rule);
        Set<ProgramEncounterContract> programEncountersContracts = programEnrolment.getProgramEncounters().stream().map(programEncounterConstructionService::constructProgramEncounterContractWrapper).collect(Collectors.toSet());
        programEnrolmentContract.setProgramEncounters(programEncountersContracts);
        programEnrolmentContract.setSubject(individualConstructionService.getSubjectInfo(programEnrolment.getIndividual()));
        RuleFailureLog ruleFailureLog = ruleValidationService.generateRuleFailureLog(rule, "Web", "Rules : " + workFlowType, programEnrolment.getUuid());
        ruleResponseEntity = createHttpHeaderAndSendRequest("/api/summaryRule", programEnrolmentContract, ruleFailureLog, RuleResponseEntity.class, ruleResponseEntity);
        setObservationsOnResponse(workFlowType, ruleResponseEntity);
        return ruleResponseEntity;
    }

    public RuleResponseEntity executeSubjectSummaryRule(Individual individual) {
        RuleResponseEntity ruleResponseEntity = new RuleResponseEntity();
        if (individual == null) {
            ruleResponseEntity.setStatus(HttpStatus.NOT_FOUND.toString());
            return ruleResponseEntity;
        }
        RuleRequestEntity rule = new RuleRequestEntity();
        SubjectType subjectType = individual.getSubjectType();
        rule.setSubjectSummaryCode(subjectType.getSubjectSummaryRule());
        String workFlowType = WorkFlowTypeEnum.SUBJECT_SUMMARY.getWorkFlowTypeName();
        rule.setWorkFlowType(workFlowType);
        rule.setFormUuid(subjectType.getUuid());
        rule.setRuleType("Subject Summary");
        IndividualContract individualContract = individualConstructionService.getSubjectInfo(individual);
        individualContract.setRule(rule);
        RuleFailureLog ruleFailureLog = ruleValidationService.generateRuleFailureLog(rule, "Web", "Rules : " + workFlowType, individual.getUuid());
        ruleResponseEntity = createHttpHeaderAndSendRequest("/api/summaryRule", individualContract, ruleFailureLog, RuleResponseEntity.class, ruleResponseEntity);
        setObservationsOnResponse(workFlowType, ruleResponseEntity);
        return ruleResponseEntity;
    }

    public EligibilityRuleResponseEntity executeEligibilityRule(Individual individual, List<EncounterType> encounterTypes) {
        EligibilityRuleResponseEntity ruleResponseEntity = new EligibilityRuleResponseEntity();
        IndividualContract individualContract = individualConstructionService.getSubjectInfo(individual);
        List<EncounterTypeContract> encounterTypeContracts = encounterTypes.stream().map(EncounterTypeContract::fromEncounterType).collect(Collectors.toList());
        EncounterEligibilityRuleRequestEntity ruleRequest = new EncounterEligibilityRuleRequestEntity(individualContract, encounterTypeContracts);
        ruleResponseEntity = createHttpHeaderAndSendRequest("/api/encounterEligibility", ruleRequest, null, EligibilityRuleResponseEntity.class, ruleResponseEntity);
        return ruleResponseEntity;
    }

    public DateTime executeScheduleRule(Individual individual, String scheduleRule) {
        ScheduleRuleResponseEntity scheduleRuleResponseEntity = new ScheduleRuleResponseEntity();
        RuleContract individualContract = individualConstructionService.getSubjectInfo(individual);
        ScheduleRuleRequestEntity ruleRequest = new ScheduleRuleRequestEntity(individualContract, scheduleRule);
        scheduleRuleResponseEntity = createHttpHeaderAndSendRequest("api/scheduleRule", ruleRequest, null, ScheduleRuleResponseEntity.class, scheduleRuleResponseEntity);
        return scheduleRuleResponseEntity.getScheduledDateTime();
    }

    public RuleResponseEntity executeServerSideRules(RequestEntityWrapper requestEntityWrapper) throws IOException, JSONException {
        RuleRequestEntity rule = requestEntityWrapper.getRule();
        Form form = formRepository.findByUuid(rule.getFormUuid());
        rule.setDecisionCode(form.getDecisionRule());
        rule.setVisitScheduleCode(form.getVisitScheduleRule());
        rule.setChecklistCode(form.getChecklistsRule());

        Object entity = null;
        String entityUuid = null;
        String workFlowType = requestEntityWrapper.getRule().getWorkFlowType();
        switch (WorkFlowTypeEnum.findByValue(workFlowType.toLowerCase())) {
            case PROGRAM_ENROLMENT:
                ProgramEnrolmentRequestEntity programEnrolmentRequestEntity = requestEntityWrapper.getProgramEnrolmentRequestEntity();
                entityUuid = programEnrolmentRequestEntity.getUuid();
                ProgramEnrolmentContract programEnrolmentContract = programEnrolmentConstructionService.constructProgramEnrolmentContract(programEnrolmentRequestEntity);
                programEnrolmentContract.setRule(rule);
                programEnrolmentContract.setVisitSchedules(new ArrayList<>());
                programEnrolmentContract.setChecklistDetails(programEnrolmentConstructionService.constructChecklistDetailRequest());
                entity = programEnrolmentContract;
                break;
            case PROGRAM_ENCOUNTER:
                ProgramEncounterRequestEntity programEncounterRequestEntity = requestEntityWrapper.getProgramEncounterRequestEntity();
                entityUuid = programEncounterRequestEntity.getUuid();
                ProgramEncounterContract programEncounterContract = programEncounterConstructionService.constructProgramEncounterContract(programEncounterRequestEntity);
                programEncounterContract.setRule(rule);
                programEncounterContract.setVisitSchedules(programEncounterConstructionService.constructProgramEnrolmentVisitScheduleContract(programEncounterRequestEntity));
                entity = programEncounterContract;
                break;
            case ENCOUNTER:
                EncounterRequestEntity encounterRequestEntity = requestEntityWrapper.getEncounterRequestEntity();
                entityUuid = encounterRequestEntity.getUuid();
                EncounterContract encounterContract = programEncounterConstructionService.constructEncounterContract(encounterRequestEntity);
                encounterContract.setRule(rule);
                encounterContract.setVisitSchedules(programEncounterConstructionService.constructIndividualVisitScheduleContract(encounterRequestEntity));
                entity = encounterContract;
                break;
            case INDIVIDUAL:
                IndividualRequestEntity individualRequestEntity = requestEntityWrapper.getIndividualRequestEntity();
                entityUuid = individualRequestEntity.getUuid();
                IndividualContract individualContract = new IndividualContractBuilder(contractBuilderServices, individualService).build(individualRequestEntity);
                individualContract.setRule(rule);
                individualContract.setVisitSchedules(new ArrayList<>());
                entity = individualContract;
                break;
        }

        RuleFailureLog ruleFailureLog = ruleValidationService.generateRuleFailureLog(rule, "Web", "Rules : " + workFlowType, entityUuid);
        RuleResponseEntity ruleResponseEntity = createHttpHeaderAndSendRequest("/api/rules", entity, ruleFailureLog, RuleResponseEntity.class, new RuleResponseEntity());
        setObservationsOnResponse(workFlowType, ruleResponseEntity);
        return ruleResponseEntity;
    }

    private void setObservationsOnResponse(String workFlowType, RuleResponseEntity ruleResponseEntity) {
        DecisionResponseEntity decisions = ruleResponseEntity.getDecisions();
        List<KeyValueResponse> summaries = ruleResponseEntity.getSummaries();
        WorkFlowTypeEnum workFlowTypeEnum = WorkFlowTypeEnum.findByValue(workFlowType.toLowerCase());

        switch (workFlowTypeEnum) {
            case PROGRAM_ENROLMENT:
                decisions.setEnrolmentObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getEnrolmentDecisions(), workFlowTypeEnum));
                decisions.setRegistrationObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getRegistrationDecisions(), workFlowTypeEnum));
                break;
            case PROGRAM_ENCOUNTER:
                decisions.setEncounterObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getEncounterDecisions(), workFlowTypeEnum));
                decisions.setEnrolmentObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getEnrolmentDecisions(), workFlowTypeEnum));
                decisions.setRegistrationObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getRegistrationDecisions(), workFlowTypeEnum));
                break;
            case ENCOUNTER:
                decisions.setEncounterObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getEncounterDecisions(), workFlowTypeEnum));
                decisions.setRegistrationObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getRegistrationDecisions(), workFlowTypeEnum));
                break;
            case INDIVIDUAL:
                decisions.setRegistrationObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getRegistrationDecisions(), workFlowTypeEnum));
                break;
            case PROGRAM_SUMMARY:
            case SUBJECT_SUMMARY:
                ruleResponseEntity.setSummaryObservations(observationService.createObservationContractsFromKeyValueResponse(summaries, workFlowTypeEnum));
                break;
        }
    }

    private RuleResponseEntity emptySuccessEntity() {
        RuleResponseEntity entity = new RuleResponseEntity();
        entity.setStatus("success");
        return entity;
    }

    private <R extends BaseRuleResponseEntity> R createHttpHeaderAndSendRequest(String url, Object contractObject, RuleFailureLog ruleFailureLog, Class<R> responseType, R newInstance) {
        try {
            ObjectMapper mapper = ObjectMapperSingleton.getObjectMapper();
            mapper.registerModule(new JodaModule());
            String ruleResponse = restClient.post(url, contractObject);
            R ruleResponseEntity = mapper.readValue(ruleResponse, responseType);
            if (ruleResponseEntity.getStatus().equals("failure")) {
                RuleError ruleError = ruleResponseEntity.getError();
                saveRuleError(ruleFailureLog, ruleError.getMessage(), ruleError.getStack());
            }
            return ruleResponseEntity;
        } catch (Exception e) {
            saveRuleError(ruleFailureLog, e.getMessage(), getStackTrace(e));
            return getFailureRuleResponseEntity(e, newInstance);
        }
    }



    private <R extends BaseRuleResponseEntity> R getFailureRuleResponseEntity(Exception e, R ruleResponseEntity) {
        RuleError ruleError = new RuleError();
        ruleError.setMessage(e.getMessage());
        ruleError.setStack(getStackTrace(e));
        ruleResponseEntity.setStatus("failure");
        ruleResponseEntity.setError(ruleError);
        return ruleResponseEntity;
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    private void saveRuleError(RuleFailureLog ruleFailureLog, String message, String stack) {
        if(ruleFailureLog != null) {
            ruleFailureLog.setErrorMessage(message);
            ruleFailureLog.setStacktrace(stack);
            ruleFailureLogRepository.save(ruleFailureLog);
        }
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return ruleRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

}