// tag::copyright[]
/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - Initial implementation
 *******************************************************************************/
// end::copyright[]
package it.io.openliberty.guides.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.microshed.testing.SharedContainerConfig;
import org.microshed.testing.jaxrs.RESTClient;
import org.microshed.testing.jupiter.MicroShedTest;
import org.microshed.testing.kafka.KafkaConsumerConfig;
import org.microshed.testing.kafka.KafkaProducerConfig;

import io.openliberty.guides.inventory.InventoryResource;
import io.openliberty.guides.models.SystemLoad;
import io.openliberty.guides.models.SystemLoad.SystemLoadSerializer;

@MicroShedTest
@SharedContainerConfig(AppContainerConfig.class)
@TestMethodOrder(OrderAnnotation.class)
public class InventoryServiceIT {

    @RESTClient
    public static InventoryResource inventoryResource;

    @KafkaProducerConfig(valueSerializer = SystemLoadSerializer.class)
    public static KafkaProducer<String, SystemLoad> producer;

    @KafkaConsumerConfig(valueDeserializer = StringDeserializer.class, 
            groupId = "property-name", topics = "propertyNameTopic", 
            properties = ConsumerConfig.AUTO_OFFSET_RESET_CONFIG + "=earliest")
    public static KafkaConsumer<String, String> propertyConsumer;

    @AfterAll
    public static void cleanup() {
        inventoryResource.resetSystems();
    }

    @Test
    public void testCpuUsage() throws InterruptedException {
        SystemLoad sl = new SystemLoad("localhost", 1.1);
        producer.send(new ProducerRecord<String, SystemLoad>("systemLoadTopic", sl));
        Thread.sleep(5000);
        Response response = inventoryResource.getSystems();
        List<Properties> systems =
                response.readEntity(new GenericType<List<Properties>>() {});
        Assertions.assertEquals(200, response.getStatus(),
                "Response should be 200");
        Assertions.assertEquals(systems.size(), 1);
        for (Properties system : systems) {
            Assertions.assertEquals(sl.hostname, system.get("hostname"),
                    "Hostname doesn't match!");
            BigDecimal systemLoad = (BigDecimal) system.get("systemLoad");
            Assertions.assertEquals(sl.loadAverage, systemLoad.doubleValue(),
                    "CPU load doesn't match!");
        }
    }

    @Test
    public void testGetProperty() {
        Response response = inventoryResource.getSystemProperty("os.name");
        Assertions.assertEquals(200, response.getStatus(),
                "Response should be 200");
        int recordsProcessed = 0;
        ConsumerRecords<String, String> records = propertyConsumer.poll(Duration.ofMillis(3000));
        System.out.println("Polled " + records.count() + " records from Kafka:");
        for (ConsumerRecord<String, String> record : records) {
            String p = record.value();
            System.out.println(p);
            assertEquals("os.name", p);
            recordsProcessed++;
        }
        propertyConsumer.commitAsync();
        assertTrue(recordsProcessed > 0, "No records processed");
    }
}
