package org.citopt.connde.service.receiver;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.citopt.connde.domain.valueLog.ValueLog;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * then added to the value log repository.
 * Provides methods for handling incoming Mqtt events and parsing incoming value messages to value logs
 * which are then passed to the observers of the ValueLogReceiver.
 */
class ValueLogReceiverArrivalHandler implements MqttCallback {

    private static final Logger logger = Logger.getLogger(ValueLogReceiverArrivalHandler.class.getName());

    //Format in which dates are stores
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    //JSON key names
    private static final String JSON_KEY_COMPONENT_TYPE = "component";
    private static final String JSON_COMPONENT_ID = "id";
    private static final String JSON_KEY_VALUE = "value";
    private static final String JSON_NOISY_DATA = "noisyData";

    //Set of observers
    private Set<ValueLogReceiverObserver> observerSet;

    @Autowired
    private NoiseComponent noiseComponent;

    /**
     * Creates a new value logger event handler.
     *
     * @param observerSet The set of observers to notify about incoming value logs.
     */
    ValueLogReceiverArrivalHandler(Set<ValueLogReceiverObserver> observerSet) {
        this.observerSet = observerSet;
    }

    /**
     * Handles the case that the mqtt client lost connection to the broker.
     *
     * @param throwable Throwable that indicates the issue
     */
    @Override
    public void connectionLost(Throwable throwable) {
        System.err.println("Mqtt client lost connection.");
    }

    /**
     * Handles incoming mqtt messages, i.e. parses the incoming value message to a value log which is then
     * passed to the observers of the ValueLogReceiver.
     *
     * @param topic       The topic under which the message was sent
     * @param mqttMessage The received value log message
     * @throws JSONException In case the message could not be parsed
     */
    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws JSONException {

       //Record current time
        Instant time = ZonedDateTime.now().toInstant();

        //Extract QoS
        int qos = mqttMessage.getQos();

        //Extract message string from the message object
        String message = new String(mqttMessage.getPayload());

        //Create a json object from the message
        JSONObject json = new JSONObject(message);

        //Extract all required data from the message and add it to a new value log object
        ValueLog valueLog = new ValueLog();

        String componentType = json.getString(JSON_KEY_COMPONENT_TYPE);
        String componentID = json.getString(JSON_COMPONENT_ID);
        double value = json.getDouble(JSON_KEY_VALUE);

        //Set value log fields
        valueLog.setTopic(topic);
        valueLog.setMessage(message);
        valueLog.setQos(qos);
        valueLog.setTime(time);
        valueLog.setIdref(componentID);
        valueLog.setComponent(componentType);
        if (json.getBoolean(JSON_NOISY_DATA)) {
            logger.log(Level.INFO, "##################" + json.toString());
            valueLog.setOriginalData(json.getDouble(JSON_KEY_VALUE));
            double anonymisedValue = noiseComponent.anonymiseDistanceValue(json.getDouble(JSON_KEY_VALUE));
            logger.log(Level.INFO, "############# " + anonymisedValue);
            valueLog.setValue(anonymisedValue);
        } else {
            valueLog.setValue(value);
        }

        //Notify all observers
        notifyObservers(valueLog);
    }

    /**
     * Handle events that are triggered when the delivery of a message was completed.
     *
     * @param iMqttDeliveryToken Delivery token of the message
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
    }

    /**
     * Notifies all observers of the ValueLogReceiver about the received value log.
     *
     * @param valueLog The received value log
     */
    private void notifyObservers(ValueLog valueLog) {
        //Sanity check
        if (valueLog == null) {
            throw new IllegalArgumentException("Value log must not be null.");
        }

        //Iterate over all observers and notify them
        for (ValueLogReceiverObserver observer : observerSet) {
            observer.onValueReceived(valueLog);
        }
    }
}