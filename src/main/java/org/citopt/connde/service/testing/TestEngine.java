package org.citopt.connde.service.testing;


import org.apache.commons.lang3.SystemUtils;
import org.aspectj.lang.annotation.Before;
import org.citopt.connde.domain.adapter.parameters.ParameterInstance;
import org.citopt.connde.domain.component.Actuator;
import org.citopt.connde.domain.component.Sensor;
import org.citopt.connde.domain.rules.Rule;
import org.citopt.connde.domain.rules.RuleTrigger;
import org.citopt.connde.domain.testing.TestDetails;
import org.citopt.connde.domain.testing.Testing;
import org.citopt.connde.domain.valueLog.ValueLog;
import org.citopt.connde.repository.*;
import org.citopt.connde.service.receiver.ValueLogReceiver;
import org.citopt.connde.service.receiver.ValueLogReceiverObserver;
import org.citopt.connde.web.rest.RestDeploymentController;
import org.citopt.connde.web.rest.RestRuleController;
import org.citopt.connde.web.rest.response.ActionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;


import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

@Component
public class TestEngine implements ValueLogReceiverObserver {
    @Autowired
    private TestDetailsRepository testDetailsRepository;

    @Autowired
    private TestEngine testEngine;

    @Autowired
    private RuleTriggerRepository ruleTriggerRepository;

    @Autowired
    private ActuatorRepository actuatorRepository;

    @Autowired
    private ValueLogReceiver valueLogReceiver;

    @Autowired
    private TestRepository testRepo;

    @Autowired
    private RestDeploymentController restDeploymentController;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private RestRuleController restRuleController;


    // List of all active Tests/testValues
    Map<String, TestDetails> activeTests = new HashMap<>();
    Map<String, List<Double>> testValues = new HashMap<>();


    /**
     * Returns a list of all active tests.
     *
     * @return activeTests
     */
    public Map<String, TestDetails> getActiveTests() {
        return activeTests;
    }

    /**
     * Sets a list of all active tests
     *
     * @param activeTests active/running tests
     */
    public void setActiveTests(Map<String, TestDetails> activeTests) {
        this.activeTests = activeTests;
    }

    /**
     * Returns a list of all incomming values of the sensors of the activeted tests.
     *
     * @return list of test values
     */
    public Map<String, List<Double>> getTestValues() {
        return testValues;
    }

    /**
     * Sets a list of all incomming values of the sensors of the activeted tests.
     *
     * @param testValues list of test values
     */
    public void setTestValues(Map<String, List<Double>> testValues) {
        this.testValues = testValues;
    }

    /**
     * Registers the TestEngine as an Observer to the ValueLogReceiver which then will be notified about incoming value logs.
     *
     * @param valueLogReceiver The value log receiver instance to use
     */
    @Autowired
    private TestEngine(ValueLogReceiver valueLogReceiver) {
        valueLogReceiver.registerObserver(this);
    }

    /**
     * Stores all Values from the active Tests
     *
     * @param valueLog The corresponding value log that arrived
     */
    @Override
    public void onValueReceived(ValueLog valueLog) {
        if (!activeTests.containsKey(valueLog.getIdref())) {
            return;
        }
        if (!testValues.containsKey(valueLog.getIdref())) {
            List<Double> newList = new ArrayList<>();
            newList.add(valueLog.getValue());
            testValues.put(valueLog.getIdref(), newList);
        } else {
            List<Double> oldList = testValues.get(valueLog.getIdref());
            oldList.add(valueLog.getValue());
        }
    }

    /**
     * Checks if the sensors of the specific test are running
     *
     * @param testDetails specific test with all details
     * @return boolean, if test is still running
     */
    public boolean testRunning(TestDetails testDetails) {
        List<Sensor> testingSensors = testDetails.getSensor();
        boolean response = false;
        for (Sensor sensor : testingSensors) {
            ResponseEntity<Boolean> sensorRunning = restDeploymentController.isRunningSensor(sensor.getId());
            response = sensorRunning.getBody();
        }

        return response;
    }

    /**
     * Sets the End time of the test, if every Sensor of a test is finished.
     *
     * @param testId Id of the the running test
     * @return value-list of the simulated Sensor
     */
    public Map<String, List<Double>> isFinished(String testId) {
        boolean response = true;
        TestDetails testDetails = testDetailsRepository.findById(testId);
        while (response) {
            response = testEngine.testRunning(testDetails);
        }
        testDetails.setEndTestTimeNow();
        testDetailsRepository.save(testDetails);

        return testEngine.getTestValues();
    }


    /**
     * Checks if the test was successful or not.
     *
     * @param triggerValuesMap map of all trigger values of a specific test
     * @param ruleNames        Names of all rules regarding to the test
     * @return information about the success
     */
    public String checkSuccess(TestDetails test, Map<String, List<Double>> triggerValuesMap, List<String> ruleNames) {
        String success = "Not Successful";
        boolean triggerRules = test.isTriggerRules();

        if (triggerRules) {
            if (triggerValuesMap.size() == ruleNames.size()) {
                for (String ruleName : ruleNames) {
                    if (triggerValuesMap.containsKey(ruleName)) {
                        success = "Successful";
                    } else {
                        success = "Not Successful";
                        break;
                    }
                }

            }
        } else {
            if (triggerValuesMap.size() == 0) {
                success = "Successful";
            }
        }

        return success;
    }

    /**
     * Enable selected Rules, Deploy and Start the Actuator and Sensors of the test
     *
     * @param testDetails specific test to be executed
     */
    public ResponseEntity<String> startTest(TestDetails testDetails) {
        Actuator testingActuator = actuatorRepository.findByName("TestingActuator");

        List<Sensor> testingSensor = testDetails.getSensor();
        List<Rule> rules = testDetails.getRules();
        List<ParameterInstance> config = testDetails.getConfig();

        //activate the selected rules for the test
        for (Rule rule : rules) {
            // check if selected rules are active --> if not active Rules
            restRuleController.enableRule(rule.getId());
        }

        //check if test exists
        if (!testDetailsRepository.exists(testDetails.getId())) {

            return new ResponseEntity<>("Test does not exists.", HttpStatus.NOT_FOUND);
        }

        //check if actuator is deployed
        ResponseEntity<Boolean> actuatorDeployed = restDeploymentController.isRunningActuator(testingActuator.getId());
        if (!actuatorDeployed.getBody()) {
            //if false deploy actuator
            restDeploymentController.deployActuator(testingActuator.getId());
        }
        // start the Actuator
        restDeploymentController.startActuator(testingActuator.getId(), new ArrayList<>());


        // check if the sensor/s are currently running
        for (Sensor sensor : testingSensor) {
            ResponseEntity<Boolean> sensorDeployed = restDeploymentController.isRunningSensor(sensor.getId());
            // check if sensor is deployed
            if (!sensorDeployed.getBody()) {
                //if not deploy Sensor
                restDeploymentController.deploySensor((sensor.getId()));
            }

            restDeploymentController.stopSensor(sensor.getId());
            restDeploymentController.startSensor(sensor.getId(), config);
        }


        return new ResponseEntity<>("Test successfully started.", HttpStatus.OK);
    }


    /**
     * Saved informations about the success, executed Rules and the trigger-values
     *
     * @param testId ID of the executed test
     */
    public void testSuccess(String testId) {
        TestDetails testDetails = testDetailsRepository.findById(testId);
        List<String> ruleNames = new ArrayList<>();

        List<Rule> ruleList = testDetails.getRules();

        // add all  names and triggerIDs of the rules of the application  tested
        for (Rule rule : ruleList) {
            ruleNames.add(rule.getName());
        }

        Map<String, List<Double>> triggerValues = getTriggerValues(testId);
        String sucessResponse = testEngine.checkSuccess(testDetails, triggerValues, ruleNames);
        List<String> rulesExecuted = testEngine.getRulesExecuted(triggerValues);

        // save informations about the success and rules of the executed test
        testDetails.setTriggerValues(triggerValues);
        testDetails.setSuccessful(sucessResponse);
        testDetails.setRulesExecuted(rulesExecuted);
        testDetailsRepository.save(testDetails);
    }

    /**
     * Returns a list of all values that triggered the selected rules in the test, between start and end time.
     *
     * @param testId ID of the executed test
     * @return List of trigger-values
     */
    public Map<String, List<Double>> getTriggerValues(String testId) {
        Map<String, List<Double>> testValues = new HashMap<>();


        TestDetails testDetails = testDetailsRepository.findById(testId);
        List<String> ruleNames = new ArrayList<>();
        List<String> triggerID = new ArrayList<>();

        Integer startTime = testDetails.getStartTimeUnix();
        long endTime = testDetails.getEndTimeUnix();


        for (int i = 0; i < testDetails.getSensor().size(); i++) {
            for (RuleTrigger trigger : ruleTriggerRepository.findAll()) {
                Sensor sensor = testDetails.getSensor().get(i);
                String s = sensor.getId();
                if (trigger.getQuery().contains(s)) {
                    triggerID.add(trigger.getId());
                    for (Rule nextRule : ruleRepository.findAll()) {
                        if (nextRule.getTrigger().getId().equals(trigger.getId())) {
                            ruleNames.add(nextRule.getName());
                        }
                    }
                }
            }
        }

        for (int i = 0; i < ruleNames.size(); i++) {
            List<Double> values = new ArrayList<>();
            String rulename = ruleNames.get(i);
            List<Testing> test = testRepo.findAllByTriggerId(triggerID.get(i));
            for (Testing testing : test) {
                if (testing.getRule().contains(rulename)) {
                    LinkedHashMap<String, Double> timeTiggerValue = (LinkedHashMap<String, Double>) testing.getOutput().getOutputMap().get("event_0");
                    LinkedHashMap<String, Long> timeTiggerValMp = (LinkedHashMap<String, Long>) testing.getOutput().getOutputMap().get("event_0");
                    long timeTiggerVal = timeTiggerValMp.get("time");
                    if (timeTiggerVal >= startTime && timeTiggerVal <= endTime) {
                        values.add(timeTiggerValue.get("value"));
                    }


                }
            }
            if (values.size() > 0) {
                testValues.put(rulename, values);
            }
        }
        return testValues;
    }


    /**
     * Returns all informations about the rules of the tested application before the execution
     *
     * @param test to be executed test
     * @return list of informations about the rules of the tested application before execution
     */
    public List<Rule> getStatRulesBefore(TestDetails test) {
        // Get the rules selected by the user with their informations about the last execution,.. before the sensor is started
        List<Rule> rulesbefore = new ArrayList<>();
        rulesbefore.addAll(test.getRules());

        List<RuleTrigger> allRules = ruleTriggerRepository.findAll();
        // Get Informations for all rules of the IoT-Applikation
        for (int i = 0; i < test.getSensor().size(); i++) {
            for (RuleTrigger trigger : allRules) {
                Sensor sensor = test.getSensor().get(i);
                String s = sensor.getId();
                if (trigger.getQuery().contains(s)) {
                    for (Rule nextRule : ruleRepository.findAll()) {
                        if (nextRule.getTrigger().getId().equals(trigger.getId())) {
                            if (!rulesbefore.contains(nextRule)) {
                                rulesbefore.add(nextRule);
                            }
                        }
                    }
                }
            }
        }

        return rulesbefore;
    }

    /**
     * Starts the test and saves all values form the sensor.
     *
     * @param test test to be executed
     */
    public Map<String, List<Double>> executeTest(TestDetails test) {

        Map<String, TestDetails> activeTests = testEngine.getActiveTests();
        Map<String, List<Double>> list = testEngine.getTestValues();
        for (Sensor sensor : test.getSensor()) {
            activeTests.put(sensor.getId(), test);
            list.remove(sensor.getId());
        }
        testEngine.setActiveTests(activeTests);
        testEngine.setTestValues(list);
        testEngine.startTest(testDetailsRepository.findById(test.getId()));

        Map<String, List<Double>> valueList;
        Map<String, List<Double>> valueListTest = new HashMap<>();

        // Get List of all simulated Values
        valueList = testEngine.isFinished(test.getId());
        TestDetails testDetails2 = testDetailsRepository.findOne(test.getId());
        for (Sensor sensor : test.getSensor()) {
            List<Double> temp = valueList.get(sensor.getId());
            valueList.put(sensor.getName(), temp);
            valueListTest.put(sensor.getName(), temp);
            list.remove(sensor.getId());
        }

        // save list of sensor values to database
        testDetails2.setSimulationList(valueListTest);
        testDetailsRepository.save(testDetails2);

        return valueListTest;
    }


    /**
     * Gets all Rules executed by the test
     *
     * @param triggerValues map of all trigger values of a specific test
     * @return a list of all executed rules
     */
    public List<String> getRulesExecuted(Map<String, List<Double>> triggerValues) {

        List<String> executedRules = new ArrayList<>();
        triggerValues.forEach((k, v) -> executedRules.add(k));

        return executedRules;
    }


    /**
     * Download/Open Testreport.
     *
     * @param testId ID of the specific test
     */
    public ResponseEntity<String> downloadPDF(String testId) throws IOException {
        TestDetails test = testDetailsRepository.findById(testId);
        File result = new File(test.getPathPDF());

        ResponseEntity respEntity;

        if (result.exists()) {
            InputStream inputStream = new FileInputStream(test.getPathPDF());

            byte[] out = org.apache.commons.io.IOUtils.toByteArray(inputStream);

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.add("content-disposition", "attachment; filename=" + testId + ".pdf");

            respEntity = new ResponseEntity(out, responseHeaders, HttpStatus.OK);

        } else {
            respEntity = new ResponseEntity("File Not Found", HttpStatus.OK);
        }


        return respEntity;
    }


}

    
