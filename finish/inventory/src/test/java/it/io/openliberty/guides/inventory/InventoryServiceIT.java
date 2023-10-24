// tag::copyright[]
/*******************************************************************************
 * Copyright (c) 2020, 2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
// end::copyright[]
package it.io.openliberty.guides.inventory;

import java.util.Collections;
import java.util.List;
import java.time.Duration;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.Properties;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.client.ClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import io.openliberty.guides.models.SystemLoad;
import io.openliberty.guides.models.SystemLoad.SystemLoadSerializer;

@Testcontainers
public class InventoryServiceIT {

    private static Logger logger = LoggerFactory.getLogger(InventoryServiceIT.class);

    public static InventoryResourceCleint client;

    private static Network network = Network.newNetwork();

    public static KafkaProducer<String, SystemLoad> producer;

    public static KafkaConsumer<String, String> propertyConsumer;

    private static ImageFromDockerfile inventoryImage
        = new ImageFromDockerfile("inventory:1.0-SNAPSHOT")
            .withDockerfile(Paths.get("./Dockerfile"));

    private static KafkaContainer kafkaContainer = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:latest"))
            .withListener(() -> "kafka:19092")
            .withNetwork(network);

    private static GenericContainer<?> inventoryContainer =
        new GenericContainer(inventoryImage)
            .withNetwork(network)
            .withExposedPorts(9085)
            .waitingFor(Wait.forHttp("/health/ready").forPort(9085))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .dependsOn(kafkaContainer);

    private static InventoryResourceCleint createRestClient(String urlPath) {
        ClientBuilder builder = ResteasyClientBuilder.newBuilder();
        ResteasyClient client = (ResteasyClient) builder.build();
        ResteasyWebTarget target = client.target(UriBuilder.fromPath(urlPath));
        return target.proxy(InventoryResourceCleint.class);
    }

    @BeforeAll
    public static void startContainers() {
        kafkaContainer.start();
        inventoryContainer.withEnv(
            "mp.messaging.connector.liberty-kafka.bootstrap.servers", "kafka:19092");
        inventoryContainer.start();
        client = createRestClient("http://"
            + inventoryContainer.getHost()
            + ":" + inventoryContainer.getFirstMappedPort());
    }

    @BeforeEach
    public void setUp() {
        Properties producerProps = new Properties();
        producerProps.put(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        producerProps.put(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        producerProps.put(
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                SystemLoadSerializer.class.getName());

        producer = new KafkaProducer<String, SystemLoad>(producerProps);

        Properties consumerProps = new Properties();
        consumerProps.put(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        consumerProps.put(
            ConsumerConfig.GROUP_ID_CONFIG,
                "property-name");
        consumerProps.put(
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        consumerProps.put(
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        consumerProps.put(
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");

        propertyConsumer = new KafkaConsumer<String, String>(consumerProps);
        propertyConsumer.subscribe(
                Collections.singletonList("request.system.property"));
    }

    @AfterAll
    public static void stopContainers() {
        inventoryContainer.stop();
        kafkaContainer.stop();
        network.close();
    }

    @AfterEach
    public void tearDown() {
        producer.close();
        propertyConsumer.close();
    }

    @Test
    public void testCpuUsage() throws InterruptedException {
        SystemLoad sl = new SystemLoad("localhost", 1.1);
        producer.send(new ProducerRecord<String, SystemLoad>("system.load", sl));
        Thread.sleep(5000);
        Response response = client.getSystems();
        List<Properties> systems =
                response.readEntity(new GenericType<List<Properties>>() { });
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
        Response response = client.updateSystemProperty("os.name");
        Assertions.assertEquals(200, response.getStatus(),
                "Response should be 200");
        ConsumerRecords<String, String> records =
                propertyConsumer.poll(Duration.ofMillis(3000));
        System.out.println("Polled " + records.count() + " records from Kafka:");
        assertTrue(records.count() > 0, "No records polled");

        for (ConsumerRecord<String, String> record : records) {
            String p = record.value();
            System.out.println(p);
            assertEquals("os.name", p);
        }
        propertyConsumer.commitAsync();
    }
}
